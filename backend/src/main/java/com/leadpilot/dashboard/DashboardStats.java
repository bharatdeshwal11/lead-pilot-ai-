package com.leadpilot.dashboard;

import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.LeadStatus;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardStats(
        long totalLeads,
        long newLeads,
        long qualifiedLeads,
        long wonLeads,
        long lostLeads,
        long highPriorityLeads,
        long overdueFollowUps,
        BigDecimal conversionRate,
        Map<LeadSource, Long> leadsBySource,
        Map<LeadStatus, Long> leadsByStatus
) {
}
