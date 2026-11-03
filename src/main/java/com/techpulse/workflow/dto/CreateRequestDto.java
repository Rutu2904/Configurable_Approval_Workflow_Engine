package com.techpulse.workflow.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRequestDto(
        @NotBlank(message = "Type is required") String type,
        @NotBlank(message = "Title is required") String title,
        String description
) {}

