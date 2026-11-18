package com.leadpilot.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class FollowUpDtos {

    private FollowUpDtos() {
    }

    public enum View {
        ALL, UPCOMING, OVERDUE, COMPLETED
    }

    public enum Status {
        UPCOMING, OVERDUE, COMPLETED
    }

    public record CreateRequest(
            @NotNull @Positive Long leadId,
            @Positive Long assigneeId,
            @NotBlank @Size(max = 500) String note,
            @NotNull Instant dueAt
    ) {
    }

    public record UpdateRequest(
            @Positive Long assigneeId,
            @Size(min = 1, max = 500) String note,
            Instant dueAt,
            Boolean completed
    ) {
    }

    public record Response(
            long id,
            long leadId,
            String leadName,
            boolean canOpenLead,
            long assigneeId,
            String assigneeName,
            String note,
            Instant dueAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            Status status
    ) {
    }

    public record PageResponse(List<Response> items, long totalElements, int totalPages, int page, int size) {
    }
}
