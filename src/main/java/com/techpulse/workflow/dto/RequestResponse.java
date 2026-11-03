package com.techpulse.workflow.dto;

import com.techpulse.workflow.enums.RequestStatus;
import java.time.LocalDateTime;

public record RequestResponse(
        Long id,
        String type,
        String title,
        String description,
        RequestStatus status,
        Long createdById,
        LocalDateTime createdAt,
        StepInfo currentStep
) {}

