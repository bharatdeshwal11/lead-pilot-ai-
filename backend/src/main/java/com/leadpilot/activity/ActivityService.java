package com.leadpilot.activity;

import com.leadpilot.lead.Lead;
import com.leadpilot.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ActivityService {
    private final ActivityRepository activities;
    private final Clock clock;

    public ActivityService(ActivityRepository activities, Clock clock) {
        this.activities = activities;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Lead lead, UserAccount actor, String type, String description) {
        activities.save(new Activity(lead, actor, type, description, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<Entry> listForLead(long leadId) {
        return activities.findByLeadIdOrderByCreatedAtDescIdDesc(leadId).stream().map(Activity::toEntry).toList();
    }

    public record Entry(Long id, String type, String description, String actorName, Instant createdAt) {
    }
}
