package com.techpulse.workflow.repository;

import com.techpulse.workflow.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    List<ApprovalStep> findByRequestTypeOrderByStepOrderAsc(String requestType);

    Optional<ApprovalStep> findByRequestTypeAndStepOrder(String requestType, int stepOrder);

    Optional<ApprovalStep> findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc(
            String requestType, int stepOrder);

    boolean existsByRequestType(String requestType);
}
