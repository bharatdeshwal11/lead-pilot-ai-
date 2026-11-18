package com.leadpilot.followup;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.followup.FollowUpDtos.CreateRequest;
import com.leadpilot.followup.FollowUpDtos.PageResponse;
import com.leadpilot.followup.FollowUpDtos.Response;
import com.leadpilot.followup.FollowUpDtos.Status;
import com.leadpilot.followup.FollowUpDtos.UpdateRequest;
import com.leadpilot.followup.FollowUpDtos.View;
import com.leadpilot.lead.Lead;
import com.leadpilot.lead.LeadService;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserService;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class FollowUpService {

    private final FollowUpRepository followUps;
    private final LeadService leads;
    private final UserService users;
    private final ActivityService activities;
    private final Clock clock;

    FollowUpService(FollowUpRepository followUps, LeadService leads, UserService users,
                    ActivityService activities, Clock clock) {
        this.followUps = followUps;
        this.leads = leads;
        this.users = users;
        this.activities = activities;
        this.clock = clock;
    }

    @Transactional
    public Response create(CreateRequest request, UserAccount actor) {
        Lead lead = leads.requireAccessible(request.leadId(), actor);
        UserAccount assignee = chooseInitialAssignee(request.assigneeId(), lead, actor);
        Instant now = clock.instant();
        FollowUp followUp = followUps.save(new FollowUp(lead, assignee, normalizeNote(request.note()), request.dueAt(), now));
        activities.record(lead, actor, "FOLLOW_UP_CREATED", "Scheduled a follow-up");
        return toResponse(followUp, actor, now);
    }

    @Transactional(readOnly = true)
    public PageResponse list(Long leadId, View view, int page, int size, UserAccount actor) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw ApiException.badRequest("Use a nonnegative page and page size between 1 and 100.");
        }
        Instant now = clock.instant();
        Specification<FollowUp> filters = visibleTo(actor).and(inView(view, now));
        if (leadId != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("lead").get("id"), leadId));
        }
        var results = followUps.findAll(filters, PageRequest.of(page, size, Sort.by("dueAt", "id")));
        var items = results.stream().map(followUp -> toResponse(followUp, actor, now)).toList();
        return new PageResponse(items, results.getTotalElements(), results.getTotalPages(), page, size);
    }

    @Transactional(readOnly = true)
    public long countOverdue(UserAccount actor, Instant now) {
        return followUps.count(visibleTo(actor).and(inView(View.OVERDUE, now)));
    }

    @Transactional
    public Response update(long id, UpdateRequest request, UserAccount actor) {
        FollowUp followUp = followUps.findById(id)
                .orElseThrow(() -> ApiException.notFound("Follow-up not found."));
        Lead lead = followUp.getLead();
        if (!canOpenLead(lead, actor) && !Objects.equals(followUp.getAssignee().getId(), actor.getId())) {
            throw ApiException.notFound("Follow-up not found.");
        }

        boolean editsFields = request.assigneeId() != null || request.note() != null || request.dueAt() != null;
        boolean complete = Boolean.TRUE.equals(request.completed());
        if (Boolean.FALSE.equals(request.completed())) {
            throw ApiException.badRequest("Use completed=true to mark a follow-up complete.");
        }
        if (!editsFields && !complete) {
            throw ApiException.badRequest("Provide at least one follow-up field to update.");
        }
        Instant now = clock.instant();
        if (followUp.getCompletedAt() != null) {
            if (complete && !editsFields) {
                return toResponse(followUp, actor, now);
            }
            throw ApiException.conflict("A completed follow-up cannot be edited.");
        }

        UserAccount assignee = followUp.getAssignee();
        if (request.assigneeId() != null && !Objects.equals(request.assigneeId(), assignee.getId())) {
            if (actor.getRole() != Role.ADMIN) {
                throw ApiException.forbidden("Only an administrator can reassign a follow-up.");
            }
            assignee = users.requireSalesAgent(request.assigneeId());
        }
        String note = request.note() == null ? followUp.getNote() : normalizeNote(request.note());
        Instant dueAt = request.dueAt() == null ? followUp.getDueAt() : request.dueAt();
        followUp.update(assignee, note, dueAt, complete, now);
        if (complete) {
            activities.record(lead, actor, "FOLLOW_UP_COMPLETED", "Completed a follow-up");
        } else {
            activities.record(lead, actor, "FOLLOW_UP_UPDATED", "Updated a follow-up");
        }
        return toResponse(followUp, actor, now);
    }

    private UserAccount chooseInitialAssignee(Long requestedId, Lead lead, UserAccount actor) {
        if (actor.getRole() != Role.ADMIN) {
            if (requestedId != null && !Objects.equals(requestedId, actor.getId())) {
                throw ApiException.forbidden("You can only assign a new follow-up to yourself.");
            }
            return actor;
        }
        if (requestedId != null) {
            return users.requireSalesAgent(requestedId);
        }
        if (lead.getAssignedUser() == null) {
            throw ApiException.badRequest("Select a salesperson for the follow-up.");
        }
        return users.requireSalesAgent(lead.getAssignedUser().getId());
    }

    private static String normalizeNote(String note) {
        String normalized = note.strip();
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("Follow-up note must not be blank.");
        }
        return normalized;
    }

    private static boolean canOpenLead(Lead lead, UserAccount actor) {
        return actor.getRole() == Role.ADMIN
                || lead.getAssignedUser() != null && Objects.equals(lead.getAssignedUser().getId(), actor.getId());
    }

    private static Specification<FollowUp> visibleTo(UserAccount actor) {
        if (actor.getRole() == Role.ADMIN) {
            return (root, query, builder) -> builder.conjunction();
        }
        return (root, query, builder) -> {
            var leadAssignee = root.join("lead").join("assignedUser", JoinType.LEFT);
            return builder.or(
                    builder.equal(leadAssignee.get("id"), actor.getId()),
                    builder.equal(root.get("assignee").get("id"), actor.getId())
            );
        };
    }

    private static Specification<FollowUp> inView(View view, Instant now) {
        return (root, query, builder) -> switch (view) {
            case ALL -> builder.conjunction();
            case UPCOMING -> builder.and(builder.isNull(root.get("completedAt")),
                    builder.greaterThanOrEqualTo(root.get("dueAt"), now));
            case OVERDUE -> builder.and(builder.isNull(root.get("completedAt")),
                    builder.lessThan(root.get("dueAt"), now));
            case COMPLETED -> builder.isNotNull(root.get("completedAt"));
        };
    }

    private static Response toResponse(FollowUp followUp, UserAccount actor, Instant now) {
        boolean canOpenLead = canOpenLead(followUp.getLead(), actor);
        Status status = Status.UPCOMING;
        if (followUp.getCompletedAt() != null) {
            status = Status.COMPLETED;
        } else if (followUp.getDueAt().isBefore(now)) {
            status = Status.OVERDUE;
        }
        return new Response(followUp.getId(), followUp.getLead().getId(),
                canOpenLead ? followUp.getLead().getCustomerName() : null, canOpenLead,
                followUp.getAssignee().getId(), followUp.getAssignee().getName(), followUp.getNote(),
                followUp.getDueAt(), followUp.getCompletedAt(), followUp.getCreatedAt(), followUp.getUpdatedAt(), status);
    }
}
