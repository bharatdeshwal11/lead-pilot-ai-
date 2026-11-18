package com.leadpilot.ai;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.lead.Lead;
import com.leadpilot.lead.LeadService;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AiService {
    private static final int MAX_ENQUIRY_LENGTH = 4000;

    private final LeadService leads;
    private final LeadAnalysisRepository analyses;
    private final ReplyDraftRepository drafts;
    private final ActivityService activities;
    private final LeadAiProvider provider;
    private final Validator validator;
    private final Clock clock;
    private final TransactionTemplate writeTransaction;
    private final Set<Generation> inProgress = ConcurrentHashMap.newKeySet();

    AiService(
            LeadService leads, LeadAnalysisRepository analyses, ReplyDraftRepository drafts,
            ActivityService activities, LeadAiProvider provider, Validator validator,
            Clock clock, PlatformTransactionManager transactionManager
    ) {
        this.leads = leads;
        this.analyses = analyses;
        this.drafts = drafts;
        this.activities = activities;
        this.provider = provider;
        this.validator = validator;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    AiDtos.AnalysisView analyze(long leadId, UserAccount actor) {
        String enquiry = enquiry(leadId, actor);
        return generate(leadId, GenerationType.ANALYSIS, () -> {
            AnalysisResult result = provider.analyze(enquiry);
            if (result == null || !validator.validate(result).isEmpty()) {
                throw new AiProviderException(false);
            }
            return writeTransaction.execute(status -> {
                Lead lead = requireSameEnquiry(leadId, actor, enquiry);
                LeadAnalysis analysis = analyses.save(new LeadAnalysis(lead, actor, result, provider.name(), clock.instant()));
                activities.record(lead, actor, "AI_ANALYZED", "Saved an AI analysis.");
                return analysis.toView();
            });
        });
    }

    @Transactional(readOnly = true)
    List<AiDtos.AnalysisView> listAnalyses(long leadId, UserAccount actor) {
        leads.requireAccessible(leadId, actor);
        return analyses.findByLeadIdOrderByCreatedAtDescIdDesc(leadId).stream().map(LeadAnalysis::toView).toList();
    }

    AiDtos.DraftView createDraft(long leadId, UserAccount actor) {
        String enquiry = enquiry(leadId, actor);
        return generate(leadId, GenerationType.REPLY, () -> {
            ReplyTemplate template = provider.suggestReply(enquiry);
            if (template == null) {
                throw new AiProviderException(false);
            }
            return writeTransaction.execute(status -> {
                Lead lead = requireSameEnquiry(leadId, actor, enquiry);
                ReplyDraft draft = drafts.save(new ReplyDraft(lead, actor, template, provider.name(), clock.instant()));
                activities.record(lead, actor, "DRAFT_CREATED", "Generated a reply draft.");
                return draft.toView();
            });
        });
    }

    @Transactional(readOnly = true)
    List<AiDtos.DraftView> listDrafts(long leadId, UserAccount actor) {
        leads.requireAccessible(leadId, actor);
        return drafts.findByLeadIdOrderByCreatedAtDescIdDesc(leadId).stream().map(ReplyDraft::toView).toList();
    }

    @Transactional
    AiDtos.DraftView editDraft(long draftId, UserAccount actor, AiDtos.EditDraft update) {
        ReplyDraft draft = requireDraft(draftId, actor);
        if (draft.status() == ReplyStatus.APPROVED) {
            throw ApiException.conflict("Approved drafts are read-only. Generate a new draft to make changes.");
        }

        String body = update.body();
        ReplyStatus status = update.status();
        if (body == null && status == null) {
            throw ApiException.badRequest("Supply a draft body or status.");
        }
        if (body != null && body.isBlank()) {
            throw ApiException.badRequest("The draft body cannot be blank.");
        }
        if (status != null && status != ReplyStatus.DRAFT && status != ReplyStatus.PENDING_REVIEW) {
            throw ApiException.badRequest("Use the review action to approve or reject a draft.");
        }
        if (draft.status() == ReplyStatus.REJECTED && status != ReplyStatus.DRAFT) {
            throw ApiException.conflict("Reopen the rejected draft before editing or submitting it.");
        }

        draft.edit(body == null ? null : body.strip(), status, clock.instant());
        activities.record(draft.lead(), actor, "DRAFT_UPDATED", "Updated a reply draft.");
        return draft.toView();
    }

    @Transactional
    AiDtos.DraftView reviewDraft(long draftId, UserAccount actor, boolean approve) {
        if (actor.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only administrators can review reply drafts.");
        }
        ReplyDraft draft = requireDraft(draftId, actor);
        if (draft.status() != ReplyStatus.PENDING_REVIEW) {
            throw ApiException.conflict("Only a draft pending review can be approved or rejected.");
        }

        draft.review(approve ? ReplyStatus.APPROVED : ReplyStatus.REJECTED, actor, clock.instant());
        if (approve) {
            activities.record(draft.lead(), actor, "DRAFT_APPROVED", "Approved a reply draft.");
        } else {
            activities.record(draft.lead(), actor, "DRAFT_REJECTED", "Rejected a reply draft.");
        }
        return draft.toView();
    }

    private ReplyDraft requireDraft(long draftId, UserAccount actor) {
        ReplyDraft draft = drafts.findById(draftId).orElseThrow(() -> ApiException.notFound("Reply draft not found."));
        leads.requireAccessible(draft.lead().getId(), actor);
        return draft;
    }

    private String enquiry(long leadId, UserAccount actor) {
        String enquiry = leads.requireAccessible(leadId, actor).getEnquiryMessage();
        if (enquiry.isBlank() || enquiry.length() > MAX_ENQUIRY_LENGTH) {
            throw ApiException.badRequest("AI requires an enquiry between 1 and 4,000 characters.");
        }
        return enquiry;
    }

    private Lead requireSameEnquiry(long leadId, UserAccount actor, String initialEnquiry) {
        Lead lead = leads.requireAccessible(leadId, actor);
        if (!initialEnquiry.equals(lead.getEnquiryMessage())) {
            throw ApiException.conflict("The enquiry changed during generation. Generate again to use the latest text.");
        }
        return lead;
    }

    private <T> T generate(long leadId, GenerationType type, Supplier<T> operation) {
        Generation generation = new Generation(leadId, type);
        if (!inProgress.add(generation)) {
            throw ApiException.conflict("This AI request is already running for the lead.");
        }
        try {
            return operation.get();
        } catch (AiProviderException e) {
            HttpStatus status = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
            throw new ApiException(status, "The AI provider could not complete the request. The lead is unchanged. Try again shortly.");
        } finally {
            inProgress.remove(generation);
        }
    }

    private enum GenerationType { ANALYSIS, REPLY }

    private record Generation(long leadId, GenerationType type) {}
}
