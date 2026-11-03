package com.techpulse.workflow.repository;

import com.techpulse.workflow.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByRequestIdOrderByActionAtAsc(Long requestId);
}
