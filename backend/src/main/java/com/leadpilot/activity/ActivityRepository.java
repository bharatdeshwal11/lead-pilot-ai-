package com.leadpilot.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByLeadIdOrderByCreatedAtDescIdDesc(long leadId);
}
