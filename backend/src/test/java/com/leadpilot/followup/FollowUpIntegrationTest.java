package com.leadpilot.followup;

import com.leadpilot.PostgresIntegrationTest;
import com.leadpilot.activity.ActivityService;
import com.leadpilot.common.ApiException;
import com.leadpilot.followup.FollowUpDtos.CreateRequest;
import com.leadpilot.followup.FollowUpDtos.Response;
import com.leadpilot.followup.FollowUpDtos.Status;
import com.leadpilot.followup.FollowUpDtos.UpdateRequest;
import com.leadpilot.followup.FollowUpDtos.View;
import com.leadpilot.lead.LeadDtos;
import com.leadpilot.lead.LeadService;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class FollowUpIntegrationTest extends PostgresIntegrationTest {

    @Autowired private FollowUpService followUps;
    @Autowired private ActivityService activities;
    @Autowired private LeadService leads;
    @Autowired private UserRepository users;

    private UserAccount admin;
    private UserAccount owner;
    private UserAccount otherAgent;
    private long leadId;
    private Instant now;

    @BeforeEach
    void prepare() {
        admin = newUser("Administrator", Role.ADMIN);
        owner = newUser("Lead owner", Role.SALES_AGENT);
        otherAgent = newUser("Task assignee", Role.SALES_AGENT);
        leadId = leads.create(new LeadDtos.Create("Fictional customer", UUID.randomUUID() + "@example.test", null,
                "I would like a property tour.", LeadSource.WEBSITE, null, null, owner.getId()), admin).id();
        now = Instant.now();
    }

    @Test
    void filtersAndPaginatesOpenAndCompletedTasksAndPreservesCompletionTime() {
        var past = followUps.create(new CreateRequest(leadId, null, "  Confirm tour  ", now.minusSeconds(600)), owner);
        var soon = followUps.create(new CreateRequest(leadId, null, "Send checklist", now.plusSeconds(600)), owner);
        var later = followUps.create(new CreateRequest(leadId, null, "Confirm handoff", now.plusSeconds(1200)), owner);

        assertThat(past.note()).isEqualTo("Confirm tour");
        assertThat(past.status()).isEqualTo(Status.OVERDUE);
        assertThat(followUps.list(leadId, View.OVERDUE, 0, 25, owner).items())
                .extracting(Response::id).containsExactly(past.id());
        var upcoming = followUps.list(leadId, View.UPCOMING, 0, 1, owner);
        assertThat(upcoming.totalPages()).isEqualTo(2);
        assertThat(upcoming.totalElements()).isEqualTo(2);
        assertThat(upcoming.items()).extracting(Response::id).containsExactly(soon.id());
        assertThat(followUps.list(leadId, View.UPCOMING, 1, 1, owner).items())
                .extracting(Response::id).containsExactly(later.id());

        var completed = followUps.update(past.id(), new UpdateRequest(null, null, null, true), owner);
        var retry = followUps.update(past.id(), new UpdateRequest(null, null, null, true), owner);
        assertThat(completed.completedAt()).isNotNull().isEqualTo(retry.completedAt());
        assertThat(followUps.list(leadId, View.OVERDUE, 0, 25, owner).items()).isEmpty();
        assertThat(followUps.list(leadId, View.COMPLETED, 0, 25, owner).items())
                .extracting(Response::id).containsExactly(past.id());
        assertThat(activities.listForLead(leadId)).extracting(ActivityService.Entry::type)
                .filteredOn("FOLLOW_UP_COMPLETED"::equals).containsExactly("FOLLOW_UP_COMPLETED");
    }

    @Test
    void assignedTasksDoNotRevealCustomerNameToOtherAgents() {
        var task = followUps.create(new CreateRequest(leadId, otherAgent.getId(), "Prepare generic materials",
                now.plusSeconds(600)), admin);
        assertThat(followUps.list(leadId, View.ALL, 0, 25, newUser("Unrelated agent", Role.SALES_AGENT)).items())
                .isEmpty();
        var assigned = followUps.list(leadId, View.ALL, 0, 25, otherAgent).items().getFirst();
        assertThat(assigned.id()).isEqualTo(task.id());
        assertThat(assigned.canOpenLead()).isFalse();
        assertThat(assigned.leadName()).isNull();
        assertThat(followUps.update(task.id(), new UpdateRequest(null, null, null, true), otherAgent).status())
                .isEqualTo(Status.COMPLETED);
        assertThat(followUps.list(leadId, View.COMPLETED, 0, 25, owner).items().getFirst().leadName())
                .isEqualTo("Fictional customer");
    }

    @Test
    void rejectsAssigningNewTasksToAnotherAgentAsSalesAgent() {
        assertThatThrownBy(() -> followUps.create(new CreateRequest(leadId, otherAgent.getId(), "Call",
                now.plusSeconds(600)), owner)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void rejectsUnauthorizedUpdatesWithoutDisclosingTaskExistence() {
        var task = followUps.create(new CreateRequest(leadId, null, "Call", now.plusSeconds(600)), owner);
        assertThatThrownBy(() -> followUps.update(task.id(), new UpdateRequest(null, null, null, true), otherAgent))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsEditsToCompletedTasks() {
        var task = followUps.create(new CreateRequest(leadId, null, "Call", now.plusSeconds(600)), owner);
        followUps.update(task.id(), new UpdateRequest(null, null, null, true), owner);
        assertThatThrownBy(() -> followUps.update(task.id(), new UpdateRequest(null, "New note", null, null), owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    private UserAccount newUser(String name, Role role) {
        return users.save(new UserAccount(name, UUID.randomUUID() + "@example.test", "unused-test-hash", role));
    }
}
