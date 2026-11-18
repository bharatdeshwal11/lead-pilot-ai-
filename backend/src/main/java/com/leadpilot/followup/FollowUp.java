package com.leadpilot.followup;

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
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "follow_ups")
public class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false, updatable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private UserAccount assignee;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(nullable = false)
    private Instant dueAt;

    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected FollowUp() {
    }

    FollowUp(Lead lead, UserAccount assignee, String note, Instant dueAt, Instant now) {
        this.lead = lead;
        this.assignee = assignee;
        this.note = note;
        this.dueAt = dueAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(UserAccount assignee, String note, Instant dueAt, boolean complete, Instant now) {
        this.assignee = assignee;
        this.note = note;
        this.dueAt = dueAt;
        if (complete) {
            this.completedAt = now;
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    Lead getLead() {
        return lead;
    }

    UserAccount getAssignee() {
        return assignee;
    }

    String getNote() {
        return note;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
