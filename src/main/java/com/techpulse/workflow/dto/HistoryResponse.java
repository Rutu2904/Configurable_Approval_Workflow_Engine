package com.techpulse.workflow.dto;

import com.techpulse.workflow.enums.HistoryAction;
import java.time.LocalDateTime;

public record HistoryResponse(
        Long id,
        Long requestId,
        Integer stepOrder,
        HistoryAction action,
        Long actionById,
        String actionByUsername,
        LocalDateTime actionAt,
        String comments
) {}

