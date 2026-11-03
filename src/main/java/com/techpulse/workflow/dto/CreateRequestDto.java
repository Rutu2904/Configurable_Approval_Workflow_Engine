package com.techpulse.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequestDto(
        @NotBlank String type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description
) {}
