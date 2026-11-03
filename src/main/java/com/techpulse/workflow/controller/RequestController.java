package com.techpulse.workflow.controller;

import com.techpulse.workflow.dto.*;
import com.techpulse.workflow.engine.WorkflowEngine;
import com.techpulse.workflow.entity.ApprovalHistory;
import com.techpulse.workflow.entity.Request;
import com.techpulse.workflow.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private final WorkflowEngine engine;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('REQUESTER')")
    public RequestResponse createRequest(@Valid @RequestBody CreateRequestDto dto) {
        Request request = engine.createRequest(dto.type(), dto.title(), dto.description(), CurrentUser.get());
        return toResponse(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER','APPROVER','ADMIN')")
    public RequestResponse getRequest(@PathVariable Long id) {
        Request request = engine.getRequest(id, CurrentUser.get());
        return toResponse(request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public RequestResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ActionRequestDto dto) {
        String comments = dto != null ? dto.comments() : null;
        Request request = engine.approve(id, comments, CurrentUser.get());
        return toResponse(request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public RequestResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ActionRequestDto dto) {
        String comments = dto != null ? dto.comments() : null;
        Request request = engine.reject(id, comments, CurrentUser.get());
        return toResponse(request);
    }

    @PostMapping("/{id}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public RequestResponse override(
            @PathVariable Long id,
            @Valid @RequestBody OverrideRequestDto dto) {
        Request request = engine.adminOverride(id, dto.approve(), dto.comments(), CurrentUser.get());
        return toResponse(request);
    }

    @GetMapping("/history/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER','ADMIN')")
    public List<HistoryResponse> getHistory(@PathVariable Long id) {
        return engine.getHistory(id, CurrentUser.get())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private RequestResponse toResponse(Request r) {
        StepInfo stepInfo = engine.getCurrentStep(r)
                .map(s -> new StepInfo(s.getStepOrder(), s.getRole()))
                .orElse(null);
        return new RequestResponse(
                r.getId(),
                r.getType(),
                r.getTitle(),
                r.getDescription(),
                r.getStatus(),
                r.getCreatedBy() != null ? r.getCreatedBy().getId() : null,
                r.getCreatedAt(),
                stepInfo
        );
    }

    private HistoryResponse toHistoryResponse(ApprovalHistory h) {
        return new HistoryResponse(
                h.getId(),
                h.getRequest() != null ? h.getRequest().getId() : null,
                h.getStepOrder(),
                h.getAction(),
                h.getActionBy() != null ? h.getActionBy().getId() : null,
                h.getActionAt(),
                h.getComments()
        );
    }
}
