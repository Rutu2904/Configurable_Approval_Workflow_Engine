package com.techpulse.workflow.dto;

import jakarta.validation.constraints.Size;

public record ActionRequestDto(
        @Size(max = 500) String comments
) {}
