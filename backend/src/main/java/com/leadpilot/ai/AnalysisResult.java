package com.leadpilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnalysisResult(
        @NotNull Intent intent,
        @Size(max = 240) String requirement,
        @Size(max = 180) String budget,
        @Size(max = 180) String timeline,
        @NotBlank @Size(max = 600) String summary,
        @NotBlank @Size(max = 400) String nextAction,
        @NotBlank @Size(max = 400) String uncertaintyNotes
) {}
