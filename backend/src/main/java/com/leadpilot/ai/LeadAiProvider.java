package com.leadpilot.ai;

public interface LeadAiProvider {
    String name();

    AnalysisResult analyze(String enquiry);

    ReplyTemplate suggestReply(String enquiry);
}
