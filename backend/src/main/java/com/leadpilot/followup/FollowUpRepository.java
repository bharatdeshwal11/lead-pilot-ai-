package com.leadpilot.followup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface FollowUpRepository extends JpaRepository<FollowUp, Long>, JpaSpecificationExecutor<FollowUp> {

    @Override
    @EntityGraph(attributePaths = {"lead", "lead.assignedUser", "assignee"})
    Page<FollowUp> findAll(Specification<FollowUp> specification, Pageable pageable);
}
