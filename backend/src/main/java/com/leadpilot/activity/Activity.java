package com.leadpilot.activity;

import com.leadpilot.lead.Lead;
import com.leadpilot.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "activities")
class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PostgreSQL clears this foreign key when the audited lead is deleted.
    @Column(name = "lead_id")
    private Long leadId;

    @Column(nullable = false)
    private long subjectLeadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccount actor;

    @Column(nullable = false, length = 120)
    private String actorName;

    @Column(nullable = false, length = 48)
    private String type;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected Activity() {
    }

    Activity(Lead lead, UserAccount actor, String type, String description, Instant createdAt) {
        this.leadId = lead.getId();
        this.subjectLeadId = lead.getId();
        this.actor = actor;
        this.actorName = actor.getName();
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
    }

    ActivityService.Entry toEntry() {
        return new ActivityService.Entry(id, type, description, actorName, createdAt);
    }
}
