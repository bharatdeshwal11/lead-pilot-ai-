package com.leadpilot.user;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.lead.LeadDtos;
import com.leadpilot.lead.LeadService;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.LeadStatus;
import com.leadpilot.lead.Priority;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "leadpilot.demo.enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {
    private final UserRepository users;
    private final UserService userService;
    private final LeadService leads;
    private final ActivityService activities;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Validator validator;
    private final UserDtos.Create admin;
    private final UserDtos.Create agent;

    public DemoData(UserRepository users, UserService userService, LeadService leads, ActivityService activities,
                    JdbcTemplate jdbc, Clock clock, Validator validator,
                    @Value("${leadpilot.demo.admin-email:}") String adminEmail,
                    @Value("${leadpilot.demo.admin-password:}") String adminPassword,
                    @Value("${leadpilot.demo.agent-email:}") String agentEmail,
                    @Value("${leadpilot.demo.agent-password:}") String agentPassword) {
        this.users = users;
        this.userService = userService;
        this.leads = leads;
        this.activities = activities;
        this.jdbc = jdbc;
        this.clock = clock;
        this.validator = validator;
        this.admin = new UserDtos.Create("Aarav Deshmukh", normalize(adminEmail), Role.ADMIN, adminPassword);
        this.agent = new UserDtos.Create("Simran Kapoor", normalize(agentEmail), Role.SALES_AGENT, agentPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        jdbc.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
        if (users.count() != 0) {
            return;
        }
        if (!validator.validate(admin).isEmpty() || !validator.validate(agent).isEmpty()
                || admin.email().equals(agent.email())) {
            throw new IllegalStateException("Demo setup requires two distinct email addresses and valid passwords.");
        }

        UserAccount adminAccount = create(admin);
        UserAccount agentAccount = create(agent);
        List<String> fixtures = new ClassPathResource("demo/leads.txt")
                .getContentAsString(StandardCharsets.UTF_8).lines().toList();
        for (int index = 0; index < fixtures.size(); index++) {
            String[] row = fixtures.get(index).split("\\|", 5);
            Long assigneeId = index % 4 == 3 ? null : agentAccount.getId();
            LeadDtos.Summary summary = leads.create(new LeadDtos.Create(row[0],
                    "enquiry-" + (index + 1) + "@example.test", null, row[4], LeadSource.valueOf(row[1]),
                    LeadStatus.valueOf(row[2]), Priority.valueOf(row[3]), assigneeId), adminAccount);
            OffsetDateTime receivedAt = clock.instant().atOffset(ZoneOffset.UTC).minusDays(6L + index);
            jdbc.update("UPDATE leads SET created_at = ?, updated_at = ? WHERE id = ?",
                    receivedAt, receivedAt, summary.id());
            jdbc.update("UPDATE activities SET created_at = ? WHERE subject_lead_id = ?", receivedAt, summary.id());
            if (index < 3) {
                seedFollowUp(summary.id(), agentAccount, adminAccount, index);
            }
        }
    }

    private UserAccount create(UserDtos.Create user) {
        return users.saveAndFlush(new UserAccount(user.name(), user.email(),
                userService.hashPassword(user.password()), user.role()));
    }

    private void seedFollowUp(long leadId, UserAccount agentAccount, UserAccount adminAccount, int index) {
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        OffsetDateTime due = index == 1 ? now.plusDays(2) : now.minusDays(index + 1);
        OffsetDateTime completed = index == 2 ? now.minusDays(1) : null;
        String note = List.of("Confirm the appointment window for the site visit.",
                "Share the floor plans and ask about preferred locations.",
                "Send the regional property guide and record any questions.").get(index);
        jdbc.update("""
                INSERT INTO follow_ups (lead_id, assigned_user_id, note, due_at, completed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, leadId, agentAccount.getId(), note, due, completed, now.minusDays(4),
                completed == null ? now.minusDays(4) : completed);
        var lead = leads.requireAccessible(leadId, adminAccount);
        activities.record(lead, adminAccount, "FOLLOW_UP_CREATED", "Follow-up scheduled.");
        if (completed != null) {
            activities.record(lead, agentAccount, "FOLLOW_UP_COMPLETED", "Follow-up completed.");
        }
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
