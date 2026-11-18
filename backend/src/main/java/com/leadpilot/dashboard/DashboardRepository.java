package com.leadpilot.dashboard;

import com.leadpilot.lead.Lead;
import com.leadpilot.lead.LeadSource;
import com.leadpilot.lead.LeadStatus;
import com.leadpilot.lead.Priority;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface DashboardRepository extends Repository<Lead, Long> {

    @Query("""
            SELECT lead.status AS status, count(lead) AS total
            FROM Lead lead LEFT JOIN lead.assignedUser assignee
            WHERE (:admin = true OR assignee.id = :userId)
            GROUP BY lead.status
            """)
    List<StatusCount> countByStatus(@Param("admin") boolean admin, @Param("userId") long userId);

    @Query("""
            SELECT lead.source AS source, count(lead) AS total
            FROM Lead lead LEFT JOIN lead.assignedUser assignee
            WHERE (:admin = true OR assignee.id = :userId)
            GROUP BY lead.source
            """)
    List<SourceCount> countBySource(@Param("admin") boolean admin, @Param("userId") long userId);

    @Query("""
            SELECT count(lead)
            FROM Lead lead LEFT JOIN lead.assignedUser assignee
            WHERE (:admin = true OR assignee.id = :userId) AND lead.priority = :priority
            """)
    long countByPriority(@Param("admin") boolean admin, @Param("userId") long userId,
                         @Param("priority") Priority priority);

    interface StatusCount {
        LeadStatus getStatus();
        long getTotal();
    }

    interface SourceCount {
        LeadSource getSource();
        long getTotal();
    }
}
