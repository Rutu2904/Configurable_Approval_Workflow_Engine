package com.techpulse.workflow.dto;

import com.techpulse.workflow.entity.HistoryAction;

import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        Long requestId,
        Integer stepOrder,
        HistoryAction action,
        Long actionById,
        LocalDateTime actionAt,
        String comments
) {}
