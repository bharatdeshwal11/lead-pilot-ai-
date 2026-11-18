package com.leadpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadpilot.PostgresIntegrationTest;
import com.leadpilot.common.ApiException;
import com.leadpilot.lead.LeadDtos;
import com.leadpilot.lead.LeadService;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.Priority;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

class AiWorkflowIntegrationTest extends PostgresIntegrationTest {
    @Autowired private LeadService leads;
    @Autowired private UserRepository users;
    @Autowired private AiService ai;

    @Test
    @Transactional
    void persistsAnalysesAndReviewedDraftsForAssignedLeadOnly() {
        UserAccount agent = account("draft-owner@example.test", Role.SALES_AGENT);
        UserAccount otherAgent = account("other-draft-agent@example.test", Role.SALES_AGENT);
        UserAccount admin = account("draft-reviewer@example.test", Role.ADMIN);
        long leadId = leads.create(new LeadDtos.Create(
                "Casey Example", "draft-customer@example.test", null,
                "Can we arrange a site visit for a 2 BHK in Sector 137 within two weeks?", LeadSource.MANUAL,
                null, null, null), agent).id();

        AiDtos.AnalysisView analysis = ai.analyze(leadId, agent);
        assertThat(analysis.intent()).isEqualTo(Intent.HIGH);
        assertThat(analysis.recommendedPriority()).isEqualTo(Priority.HIGH);
        assertThat(ai.listAnalyses(leadId, agent)).extracting(AiDtos.AnalysisView::id).containsExactly(analysis.id());
        assertThat(leads.get(leadId, admin).priority()).isEqualTo(Priority.MEDIUM);
        assertThatThrownBy(() -> ai.listAnalyses(leadId, otherAgent))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));

        AiDtos.DraftView draft = ai.createDraft(leadId, agent);
        assertThat(draft.status()).isEqualTo(ReplyStatus.DRAFT);
        assertThatThrownBy(() -> ai.editDraft(draft.id(), otherAgent, new AiDtos.EditDraft("Wrong owner", null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));

        ai.editDraft(draft.id(), agent, new AiDtos.EditDraft("Please share your visit preferences.", ReplyStatus.PENDING_REVIEW));
        assertThatThrownBy(() -> ai.reviewDraft(draft.id(), agent, true))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(ai.reviewDraft(draft.id(), admin, false).status()).isEqualTo(ReplyStatus.REJECTED);
        ai.editDraft(draft.id(), agent, new AiDtos.EditDraft("Could you share two dates that suit you?", ReplyStatus.DRAFT));
        ai.editDraft(draft.id(), agent, new AiDtos.EditDraft(null, ReplyStatus.PENDING_REVIEW));
        AiDtos.DraftView approved = ai.reviewDraft(draft.id(), admin, true);

        assertThat(approved.status()).isEqualTo(ReplyStatus.APPROVED);
        assertThat(approved.reviewedAt()).isNotNull();
        assertThat(ai.listDrafts(leadId, agent)).extracting(AiDtos.DraftView::body)
                .containsExactly("Could you share two dates that suit you?");
        assertThatThrownBy(() -> ai.editDraft(draft.id(), agent, new AiDtos.EditDraft("Changed", null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    private UserAccount account(String email, Role role) {
        return users.save(new UserAccount("Test Account", email, "test-hash", role));
    }
}
