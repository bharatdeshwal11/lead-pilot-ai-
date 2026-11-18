package com.leadpilot.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
    boolean existsByFingerprint(String fingerprint);
    boolean existsByFingerprintAndIdNot(String fingerprint, long id);

    @Override
    @EntityGraph(attributePaths = "assignedUser")
    Page<Lead> findAll(Specification<Lead> specification, Pageable pageable);
}
