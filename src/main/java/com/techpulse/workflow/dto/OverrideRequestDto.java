package com.techpulse.workflow.dto;

import jakarta.validation.constraints.NotNull;

public record OverrideRequestDto(
        @NotNull(message = "Approve flag is required") Boolean approve,
        String comments
) {}

