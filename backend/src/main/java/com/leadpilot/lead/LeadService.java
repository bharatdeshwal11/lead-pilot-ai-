package com.leadpilot.lead;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class LeadService {
    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "customerName", "email", "source", "status", "priority");

    private final LeadRepository leads;
    private final UserService users;
    private final ActivityService activities;
    private final Clock clock;

    public LeadService(LeadRepository leads, UserService users, ActivityService activities, Clock clock) {
        this.leads = leads;
        this.users = users;
        this.activities = activities;
        this.clock = clock;
    }

    @Transactional
    public LeadDtos.Summary create(LeadDtos.Create input, UserAccount actor) {
        UserAccount assignee = initialAssignee(input.assignedUserId(), actor);
        String email = normalizeEmail(input.email());
        String enquiry = input.enquiryMessage().strip();
        String fingerprint = fingerprint(email, enquiry);
        if (leads.existsByFingerprint(fingerprint)) {
            throw duplicate();
        }

        Lead lead = new Lead(input.customerName().strip(), email, cleanPhone(input.phone()), enquiry, input.source(),
                Objects.requireNonNullElse(input.status(), LeadStatus.NEW),
                Objects.requireNonNullElse(input.priority(), Priority.MEDIUM), assignee, fingerprint, clock.instant());
        leads.saveAndFlush(lead);
        activities.record(lead, actor, "LEAD_CREATED", "Lead created.");
        if (assignee != null) {
            activities.record(lead, actor, "LEAD_ASSIGNED", "Lead assigned to " + assignee.getName() + ".");
        }
        return LeadDtos.Summary.from(lead);
    }

    public LeadDtos.Summary get(long id, UserAccount actor) {
        return LeadDtos.Summary.from(requireAccessible(id, actor));
    }

    public LeadDtos.Page list(LeadDtos.Filter filter, UserAccount actor) {
        if (filter.page() < 0 || filter.size() < 1 || filter.size() > 100
                || (long) filter.page() * filter.size() > Integer.MAX_VALUE) {
            throw ApiException.badRequest("Use a nonnegative page and page size between 1 and 100.");
        }
        if (filter.q() != null && filter.q().length() > 200) {
            throw ApiException.badRequest("Search text must be 200 characters or fewer.");
        }

        String[] sort = filter.sort().split(",", -1);
        if (sort.length > 2 || !SORT_FIELDS.contains(sort[0])) {
            throw ApiException.badRequest("Sort by createdAt, updatedAt, customerName, email, source, status, or priority.");
        }
        Sort.Direction direction = sort.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromOptionalString(sort[1])
                .orElseThrow(() -> ApiException.badRequest("Sort direction must be asc or desc."));
        Sort pageSort = sort[0].equals("priority") ? Sort.unsorted() : Sort.by(direction, sort[0], "id");

        Page<LeadDtos.Summary> page = leads.findAll(specification(filter, actor, sort[0], direction),
                PageRequest.of(filter.page(), filter.size(), pageSort)).map(LeadDtos.Summary::from);
        return new LeadDtos.Page(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }

    @Transactional
    public LeadDtos.Summary update(long id, LeadDtos.Update input, UserAccount actor) {
        Lead lead = requireAccessible(id, actor);
        UserAccount assignedUser = lead.getAssignedUser();
        if (input.assignmentProvided) {
            users.requireAdmin(actor);
            assignedUser = input.assignedUserId == null ? null : users.requireSalesAgent(input.assignedUserId);
        }

        String customerName = input.customerName == null ? lead.getCustomerName() : input.customerName.strip();
        String email = input.email == null ? lead.getEmail() : normalizeEmail(input.email);
        String phone = input.phoneProvided ? cleanPhone(input.phone) : lead.getPhone();
        String enquiry = input.enquiryMessage == null ? lead.getEnquiryMessage() : input.enquiryMessage.strip();
        LeadSource source = Objects.requireNonNullElse(input.source, lead.getSource());
        LeadStatus status = Objects.requireNonNullElse(input.status, lead.getStatus());
        Priority priority = Objects.requireNonNullElse(input.priority, lead.getPriority());

        String fingerprint = fingerprint(email, enquiry);
        if ((!email.equals(lead.getEmail()) || !enquiry.equals(lead.getEnquiryMessage()))
                && leads.existsByFingerprintAndIdNot(fingerprint, id)) {
            throw duplicate();
        }

        boolean contactChanged = lead.updateContact(customerName, email, phone, enquiry, source, fingerprint);
        boolean assignmentChanged = !Objects.equals(userId(assignedUser), userId(lead.getAssignedUser()));
        boolean statusChanged = status != lead.getStatus();
        boolean priorityChanged = priority != lead.getPriority();
        if (contactChanged) {
            activities.record(lead, actor, "LEAD_UPDATED", "Lead details updated.");
        }
        if (assignmentChanged) {
            activities.record(lead, actor, "LEAD_ASSIGNED",
                    assignedUser == null ? "Lead assignment cleared." : "Lead assigned to " + assignedUser.getName() + ".");
        }
        if (statusChanged) {
            activities.record(lead, actor, "STATUS_CHANGED", "Status changed from " + lead.getStatus() + " to " + status + ".");
        }
        if (priorityChanged) {
            activities.record(lead, actor, "PRIORITY_CHANGED",
                    "Priority changed from " + lead.getPriority() + " to " + priority + ".");
        }
        if (contactChanged || assignmentChanged || statusChanged || priorityChanged) {
            lead.updateWorkflow(status, priority, assignedUser, clock.instant());
        }
        return LeadDtos.Summary.from(lead);
    }

    @Transactional
    public void delete(long id, UserAccount actor) {
        users.requireAdmin(actor);
        Lead lead = requireAccessible(id, actor);
        activities.record(lead, actor, "LEAD_DELETED", "Lead deleted.");
        leads.delete(lead);
    }

    public List<ActivityService.Entry> history(long id, UserAccount actor) {
        requireAccessible(id, actor);
        return activities.listForLead(id);
    }

    public Lead requireAccessible(long id, UserAccount actor) {
        return leads.findById(id)
                .filter(lead -> actor.getRole() == Role.ADMIN
                        || Objects.equals(actor.getId(), userId(lead.getAssignedUser())))
                .orElseThrow(() -> ApiException.notFound("Lead not found."));
    }

    private UserAccount initialAssignee(Long id, UserAccount actor) {
        if (actor.getRole() == Role.SALES_AGENT) {
            if (id != null && !id.equals(actor.getId())) {
                throw ApiException.forbidden("Sales agents can create leads assigned to themselves.");
            }
            return actor;
        }
        return id == null ? null : users.requireSalesAgent(id);
    }

    private static Long userId(UserAccount user) {
        return user == null ? null : user.getId();
    }

    private static String cleanPhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.strip();
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    static String fingerprint(String email, String enquiry) {
        try {
            byte[] source = (email + "\n" + enquiry).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static ApiException duplicate() {
        return ApiException.conflict("A lead already uses this email address and enquiry.");
    }

    private Specification<Lead> specification(LeadDtos.Filter filter, UserAccount actor, String sortField,
                                              Sort.Direction direction) {
        return (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (actor.getRole() == Role.SALES_AGENT) {
                conditions.add(builder.equal(root.get("assignedUser").get("id"), actor.getId()));
            }
            if (filter.assignedUserId() != null) {
                conditions.add(builder.equal(root.get("assignedUser").get("id"), filter.assignedUserId()));
            }
            if (filter.source() != null) {
                conditions.add(builder.equal(root.get("source"), filter.source()));
            }
            if (filter.priority() != null) {
                conditions.add(builder.equal(root.get("priority"), filter.priority()));
            }
            if (filter.status() != null) {
                conditions.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String pattern = "%" + filter.q().strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                conditions.add(builder.or(
                        builder.like(builder.lower(root.get("customerName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("email")), pattern, '\\'),
                        builder.like(builder.lower(root.get("enquiryMessage")), pattern, '\\'),
                        builder.like(root.get("phone"), pattern, '\\')));
            }
            if (sortField.equals("priority") && query != null && query.getResultType() == Lead.class) {
                var rank = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("priority"), Priority.HIGH), 3)
                        .when(builder.equal(root.get("priority"), Priority.MEDIUM), 2)
                        .otherwise(1);
                if (direction.isAscending()) {
                    query.orderBy(builder.asc(rank), builder.asc(root.get("id")));
                } else {
                    query.orderBy(builder.desc(rank), builder.desc(root.get("id")));
                }
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }
}
