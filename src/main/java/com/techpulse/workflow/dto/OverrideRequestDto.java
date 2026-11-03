package com.techpulse.workflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverrideRequestDto(
        @NotNull Boolean approve,
        @Size(max = 500) String comments
) {}
