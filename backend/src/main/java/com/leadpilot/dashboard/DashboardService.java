package com.leadpilot.dashboard;

import com.leadpilot.followup.FollowUpService;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.LeadStatus;
import com.leadpilot.lead.Priority;
import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;

@Service
public class DashboardService {

    private final DashboardRepository dashboard;
    private final FollowUpService followUps;
    private final Clock clock;

    DashboardService(DashboardRepository dashboard, FollowUpService followUps, Clock clock) {
        this.dashboard = dashboard;
        this.followUps = followUps;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardStats stats(UserAccount actor) {
        Instant now = clock.instant();
        boolean admin = actor.getRole() == Role.ADMIN;
        long userId = actor.getId();

        var byStatus = new EnumMap<LeadStatus, Long>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, 0L);
        }
        dashboard.countByStatus(admin, userId).forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

        var bySource = new EnumMap<LeadSource, Long>(LeadSource.class);
        for (LeadSource source : LeadSource.values()) {
            bySource.put(source, 0L);
        }
        dashboard.countBySource(admin, userId).forEach(row -> bySource.put(row.getSource(), row.getTotal()));

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long won = byStatus.get(LeadStatus.WON);
        BigDecimal conversion = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(won)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new DashboardStats(total, byStatus.get(LeadStatus.NEW), byStatus.get(LeadStatus.QUALIFIED), won,
                byStatus.get(LeadStatus.LOST), dashboard.countByPriority(admin, userId, Priority.HIGH),
                followUps.countOverdue(actor, now), conversion, Collections.unmodifiableMap(bySource),
                Collections.unmodifiableMap(byStatus));
    }
}
