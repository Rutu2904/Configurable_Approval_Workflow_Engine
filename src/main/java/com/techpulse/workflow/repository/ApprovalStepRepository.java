package com.techpulse.workflow.repository;

import com.techpulse.workflow.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByRequestTypeOrderByStepOrderAsc(String requestType);

    Optional<ApprovalStep> findByRequestTypeAndStepOrder(String requestType, Integer stepOrder);

    // Find the next step after the given step order
    Optional<ApprovalStep> findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc(
            String requestType, Integer stepOrder);

    boolean existsByRequestType(String requestType);
}

