package com.leadpilot.ai;

import com.leadpilot.lead.Priority;

public enum Intent {
    HIGH(Priority.HIGH),
    MEDIUM(Priority.MEDIUM),
    LOW(Priority.LOW);

    private final Priority priority;

    Intent(Priority priority) {
        this.priority = priority;
    }

    public Priority recommendedPriority() {
        return priority;
    }
}
