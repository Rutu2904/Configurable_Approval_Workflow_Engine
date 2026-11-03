package com.techpulse.workflow.repository;

import com.techpulse.workflow.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {
}
