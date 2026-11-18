package com.leadpilot.ai;

import com.leadpilot.lead.Lead;
import com.leadpilot.lead.Priority;
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
import java.time.Instant;

@Entity
@Table(name = "lead_analyses")
class LeadAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Intent intent;

    @Column(length = 240)
    private String requirement;

    @Column(length = 180)
    private String budget;

    @Column(length = 180)
    private String timeline;

    @Column(nullable = false, length = 600)
    private String summary;

    @Column(nullable = false, length = 400)
    private String nextAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority recommendedPriority;

    @Column(nullable = false, length = 400)
    private String uncertaintyNotes;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false)
    private Instant createdAt;

    protected LeadAnalysis() {}

    LeadAnalysis(Lead lead, UserAccount author, AnalysisResult result, String provider, Instant createdAt) {
        this.lead = lead;
        this.author = author;
        this.intent = result.intent();
        this.requirement = result.requirement();
        this.budget = result.budget();
        this.timeline = result.timeline();
        this.summary = result.summary();
        this.nextAction = result.nextAction();
        this.recommendedPriority = result.intent().recommendedPriority();
        this.uncertaintyNotes = result.uncertaintyNotes();
        this.provider = provider;
        this.createdAt = createdAt;
    }

    AiDtos.AnalysisView toView() {
        return new AiDtos.AnalysisView(id, lead.getId(), intent, requirement, budget, timeline, summary,
                nextAction, recommendedPriority, uncertaintyNotes, createdAt, provider);
    }
}
