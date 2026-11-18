package com.leadpilot.ai;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LeadAnalysisRepository extends JpaRepository<LeadAnalysis, Long> {
    List<LeadAnalysis> findByLeadIdOrderByCreatedAtDescIdDesc(long leadId);
}
