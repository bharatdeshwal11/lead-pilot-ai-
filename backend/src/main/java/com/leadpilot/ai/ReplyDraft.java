package com.leadpilot.ai;

import com.leadpilot.lead.Lead;
import com.leadpilot.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "reply_drafts")
class ReplyDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private UserAccount reviewer;

    @Column(nullable = false, length = 2500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReplyStatus status;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant reviewedAt;

    @Version
    private long version;

    protected ReplyDraft() {}

    ReplyDraft(Lead lead, UserAccount author, ReplyTemplate template, String provider, Instant now) {
        this.lead = lead;
        this.author = author;
        this.body = template.body();
        this.status = ReplyStatus.DRAFT;
        this.provider = provider;
        this.createdAt = now;
        this.updatedAt = now;
    }

    Lead lead() {
        return lead;
    }

    ReplyStatus status() {
        return status;
    }

    void edit(String body, ReplyStatus status, Instant now) {
        if (body != null) {
            this.body = body;
        }
        if (status != null) {
            this.status = status;
        }
        this.reviewer = null;
        this.reviewedAt = null;
        this.updatedAt = now;
    }

    void review(ReplyStatus status, UserAccount reviewer, Instant now) {
        this.status = status;
        this.reviewer = reviewer;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    AiDtos.DraftView toView() {
        return new AiDtos.DraftView(id, lead.getId(), body, status, createdAt, updatedAt, provider, reviewedAt);
    }
}
