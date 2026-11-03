package com.techpulse.workflow.controller;

import com.techpulse.workflow.dto.*;
import com.techpulse.workflow.entity.ApprovalHistory;
import com.techpulse.workflow.entity.Request;
import com.techpulse.workflow.security.CurrentUser;
import com.techpulse.workflow.security.UserPrincipal;
import com.techpulse.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/requests")
public class RequestController {

    private final WorkflowService workflowService;

    public RequestController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // Create a new request
    @PostMapping
    @PreAuthorize("hasRole('REQUESTER')")
    public ResponseEntity<RequestResponse> createRequest(@Valid @RequestBody CreateRequestDto dto) {
        UserPrincipal user = CurrentUser.get();
        Request request = workflowService.createRequest(dto.type(), dto.title(), dto.description(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(request));
    }

    // Get a request by id
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER', 'APPROVER', 'ADMIN')")
    public ResponseEntity<RequestResponse> getRequest(@PathVariable Long id) {
        UserPrincipal user = CurrentUser.get();
        Request request = workflowService.getRequest(id, user);
        return ResponseEntity.ok(toResponse(request));
    }

    // Approve current step
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<RequestResponse> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) ActionRequestDto dto) {
        UserPrincipal user = CurrentUser.get();
        String comments = dto != null ? dto.comments() : null;
        Request request = workflowService.approve(id, comments, user);
        return ResponseEntity.ok(toResponse(request));
    }

    // Reject the request
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<RequestResponse> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) ActionRequestDto dto) {
        UserPrincipal user = CurrentUser.get();
        String comments = dto != null ? dto.comments() : null;
        Request request = workflowService.reject(id, comments, user);
        return ResponseEntity.ok(toResponse(request));
    }

    // Admin override
    @PostMapping("/{id}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RequestResponse> override(@PathVariable Long id,
                                                     @Valid @RequestBody OverrideRequestDto dto) {
        UserPrincipal user = CurrentUser.get();
        Request request = workflowService.adminOverride(id, dto.approve(), dto.comments(), user);
        return ResponseEntity.ok(toResponse(request));
    }

    // Get approval history
    @GetMapping("/history/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER', 'ADMIN')")
    public ResponseEntity<List<HistoryResponse>> getHistory(@PathVariable Long id) {
        UserPrincipal user = CurrentUser.get();
        List<ApprovalHistory> history = workflowService.getHistory(id, user);
        List<HistoryResponse> response = history.stream()
                .map(this::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    // Convert entity to response DTO
    private RequestResponse toResponse(Request request) {
        Optional<StepInfo> step = workflowService.getCurrentStep(request);
        return new RequestResponse(
                request.getId(),
                request.getType(),
                request.getTitle(),
                request.getDescription(),
                request.getStatus(),
                request.getCreatedBy().getId(),
                request.getCreatedAt(),
                step.orElse(null)
        );
    }

    private HistoryResponse toHistoryResponse(ApprovalHistory h) {
        return new HistoryResponse(
                h.getId(),
                h.getRequest().getId(),
                h.getStepOrder(),
                h.getAction(),
                h.getActionBy().getId(),
                h.getActionBy().getUsername(),
                h.getActionAt(),
                h.getComments()
        );
    }
}

