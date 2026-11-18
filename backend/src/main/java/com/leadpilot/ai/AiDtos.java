package com.leadpilot.ai;

import com.leadpilot.lead.Priority;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AiDtos {
    private AiDtos() {}

    public record AnalysisView(
            long id, long leadId, Intent intent, String requirement, String budget, String timeline,
            String summary, String nextAction, Priority recommendedPriority, String uncertaintyNotes,
            Instant createdAt, String provider
    ) {}

    public record DraftView(
            long id, long leadId, String body, ReplyStatus status, Instant createdAt, Instant updatedAt,
            String provider, Instant reviewedAt
    ) {}

    public record EditDraft(
            @Size(max = 2500) String body,
            ReplyStatus status
    ) {}
}
