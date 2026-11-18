package com.leadpilot.dashboard;

import com.leadpilot.PostgresIntegrationTest;
import com.leadpilot.followup.FollowUpDtos;
import com.leadpilot.followup.FollowUpService;
import com.leadpilot.lead.LeadDtos;
import com.leadpilot.lead.LeadService;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.LeadStatus;
import com.leadpilot.lead.Priority;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DashboardIntegrationTest extends PostgresIntegrationTest {

    @Autowired private DashboardService dashboard;
    @Autowired private FollowUpService followUps;
    @Autowired private LeadService leads;
    @Autowired private UserRepository users;

    @Test
    void aggregatesStoredLeadsAndOpenOverdueTasksWithinEachUserScope() {
        UserAccount admin = newUser(Role.ADMIN);
        UserAccount owner = newUser(Role.SALES_AGENT);
        UserAccount otherAgent = newUser(Role.SALES_AGENT);
        var initial = dashboard.stats(admin);

        long owned = createLead(admin, owner.getId(), LeadSource.WEBSITE, LeadStatus.NEW, Priority.HIGH);
        createLead(admin, owner.getId(), LeadSource.WEBSITE, LeadStatus.QUALIFIED, Priority.HIGH);
        createLead(admin, owner.getId(), LeadSource.EMAIL, LeadStatus.WON, Priority.LOW);
        long other = createLead(admin, otherAgent.getId(), LeadSource.MANUAL, LeadStatus.LOST, Priority.HIGH);
        createLead(admin, null, LeadSource.OTHER, LeadStatus.CONTACTED, Priority.MEDIUM);

        Instant past = Instant.now().minusSeconds(3600);
        followUps.create(new FollowUpDtos.CreateRequest(owned, null, "Call back", past), owner);
        var completed = followUps.create(new FollowUpDtos.CreateRequest(owned, null, "Send tour notes", past), owner);
        followUps.update(completed.id(), new FollowUpDtos.UpdateRequest(null, null, null, true), owner);
        followUps.create(new FollowUpDtos.CreateRequest(other, owner.getId(), "Prepare summary", past), admin);
        followUps.create(new FollowUpDtos.CreateRequest(other, null, "Check status", past), otherAgent);

        var scoped = dashboard.stats(owner);
        assertThat(scoped.totalLeads()).isEqualTo(3);
        assertThat(scoped.newLeads()).isEqualTo(1);
        assertThat(scoped.qualifiedLeads()).isEqualTo(1);
        assertThat(scoped.wonLeads()).isEqualTo(1);
        assertThat(scoped.lostLeads()).isZero();
        assertThat(scoped.highPriorityLeads()).isEqualTo(2);
        assertThat(scoped.overdueFollowUps()).isEqualTo(2);
        assertThat(scoped.conversionRate()).isEqualByComparingTo("33.33");
        assertThat(scoped.leadsBySource()).containsEntry(LeadSource.WEBSITE, 2L).containsEntry(LeadSource.EMAIL, 1L)
                .containsEntry(LeadSource.MANUAL, 0L).containsEntry(LeadSource.OTHER, 0L);
        assertThat(scoped.leadsByStatus()).containsEntry(LeadStatus.CONTACTED, 0L)
                .containsEntry(LeadStatus.QUALIFIED, 1L).hasSize(LeadStatus.values().length);

        var global = dashboard.stats(admin);
        assertThat(global.totalLeads()).isEqualTo(initial.totalLeads() + 5);
        assertThat(global.highPriorityLeads()).isEqualTo(initial.highPriorityLeads() + 3);
        assertThat(global.overdueFollowUps()).isEqualTo(initial.overdueFollowUps() + 3);
        assertThat(global.leadsBySource().get(LeadSource.OTHER)).isEqualTo(initial.leadsBySource().get(LeadSource.OTHER) + 1);
    }

    @Test
    void returnsZeroConversionAndAllChartBucketsForAnAgentWithNoLeads() {
        var stats = dashboard.stats(newUser(Role.SALES_AGENT));
        assertThat(stats.totalLeads()).isZero();
        assertThat(stats.conversionRate()).isZero();
        assertThat(stats.leadsByStatus()).hasSize(LeadStatus.values().length).allSatisfy((key, value) ->
                assertThat(value).isZero());
        assertThat(stats.leadsBySource()).hasSize(LeadSource.values().length).allSatisfy((key, value) ->
                assertThat(value).isZero());
    }

    private long createLead(UserAccount admin, Long assigneeId, LeadSource source, LeadStatus status, Priority priority) {
        return leads.create(new LeadDtos.Create("Fictional customer", UUID.randomUUID() + "@example.test", null,
                "I am interested in viewing a new property.", source, status, priority, assigneeId), admin).id();
    }

    private UserAccount newUser(Role role) {
        return users.save(new UserAccount("Fictional staff", UUID.randomUUID() + "@example.test", "unused-test-hash", role));
    }
}
