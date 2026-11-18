package com.leadpilot.ai;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReplyDraftRepository extends JpaRepository<ReplyDraft, Long> {
    List<ReplyDraft> findByLeadIdOrderByCreatedAtDescIdDesc(long leadId);
}
