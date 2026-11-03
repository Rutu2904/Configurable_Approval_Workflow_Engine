package com.techpulse.workflow.engine;

import com.techpulse.workflow.entity.*;
import com.techpulse.workflow.exception.InvalidStateTransitionException;
import com.techpulse.workflow.exception.InvalidWorkflowException;
import com.techpulse.workflow.exception.ResourceNotFoundException;
import com.techpulse.workflow.repository.*;
import com.techpulse.workflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final RequestRepository requestRepository;
    private final ApprovalStepRepository stepRepo;
    private final ApprovalHistoryRepository historyRepo;
    private final UserRepository userRepository;

    @Transactional
    public Request createRequest(String type, String title, String description, UserPrincipal user) {
        if (!stepRepo.existsByRequestType(type)) {
            throw new InvalidWorkflowException("Unknown request type: " + type);
        }
        ApprovalStep firstStep = stepRepo.findByRequestTypeOrderByStepOrderAsc(type).get(0);

        Request request = Request.builder()
                .type(type)
                .title(title)
                .description(description)
                .status(RequestStatus.PENDING)
                .currentStepOrder(firstStep.getStepOrder())
                .createdBy(userRepository.getReferenceById(user.getId()))
                .createdAt(LocalDateTime.now())
                .build();
        request = requestRepository.save(request);

        saveHistory(request, HistoryAction.CREATED, firstStep.getStepOrder(), user, null);
        return request;
    }

    @Transactional
    public Request approve(Long id, String comments, UserPrincipal user) {
        Request request = loadPending(id);
        assertNotCreator(request, user);

        ApprovalStep step = currentStep(request);
        assertHasRole(user, step.getRole());

        int actedOrder = step.getStepOrder();
        Optional<ApprovalStep> next = stepRepo
                .findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc(
                        request.getType(), step.getStepOrder());

        if (next.isPresent()) {
            request.setCurrentStepOrder(next.get().getStepOrder());
        } else {
            request.setStatus(RequestStatus.APPROVED);
            request.setCurrentStepOrder(null);
        }
        request = requestRepository.save(request);
        saveHistory(request, HistoryAction.APPROVED, actedOrder, user, comments);
        return request;
    }

    @Transactional
    public Request reject(Long id, String comments, UserPrincipal user) {
        Request request = loadPending(id);
        assertNotCreator(request, user);

        ApprovalStep step = currentStep(request);
        assertHasRole(user, step.getRole());

        int actedOrder = step.getStepOrder();
        request.setStatus(RequestStatus.REJECTED);
        request.setCurrentStepOrder(null);
        request = requestRepository.save(request);
        saveHistory(request, HistoryAction.REJECTED, actedOrder, user, comments);
        return request;
    }

    @Transactional
    public Request adminOverride(Long id, boolean approve, String comments, UserPrincipal admin) {
        Request request = loadPending(id);

        Map<Boolean, HistoryAction> actionMap = Map.of(
                true,  HistoryAction.ADMIN_APPROVED,
                false, HistoryAction.ADMIN_REJECTED
        );
        RequestStatus newStatus = approve ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        HistoryAction action = actionMap.get(approve);

        Integer actedOrder = request.getCurrentStepOrder();
        request.setStatus(newStatus);
        request.setCurrentStepOrder(null);
        request = requestRepository.save(request);
        saveHistory(request, action, actedOrder, admin, comments);
        return request;
    }

    @Transactional(readOnly = true)
    public Request getRequest(Long id, UserPrincipal user) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));

        boolean isApproverOrAdmin = user.getRoles().contains("APPROVER") || user.getRoles().contains("ADMIN");
        if (!isApproverOrAdmin && !request.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> getHistory(Long id, UserPrincipal user) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));

        boolean isAdmin = user.getRoles().contains("ADMIN");
        boolean isCreator = request.getCreatedBy().getId().equals(user.getId());
        if (!isAdmin && !isCreator) {
            throw new AccessDeniedException("Access denied");
        }
        return historyRepo.findByRequestIdOrderByActionAtAsc(id);
    }

    public Optional<ApprovalStep> getCurrentStep(Request r) {
        if (r.getCurrentStepOrder() == null) return Optional.empty();
        return stepRepo.findByRequestTypeAndStepOrder(r.getType(), r.getCurrentStepOrder());
    }

    // --- helpers ---

    private Request loadPending(Long id) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + id));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Request " + id + " is not PENDING (current: " + request.getStatus() + ")");
        }
        return request;
    }

    private void assertNotCreator(Request request, UserPrincipal user) {
        if (request.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Creator cannot act on their own request");
        }
    }

    private ApprovalStep currentStep(Request request) {
        return stepRepo.findByRequestTypeAndStepOrder(request.getType(), request.getCurrentStepOrder())
                .orElseThrow(() -> new InvalidStateTransitionException("Current step not found"));
    }

    private void assertHasRole(UserPrincipal user, String requiredRole) {
        if (!user.getRoles().contains(requiredRole)) {
            throw new AccessDeniedException("User does not have required role: " + requiredRole);
        }
    }

    private void saveHistory(Request request, HistoryAction action, Integer stepOrder,
                             UserPrincipal user, String comments) {
        historyRepo.save(ApprovalHistory.builder()
                .request(request)
                .stepOrder(stepOrder)
                .action(action)
                .actionBy(userRepository.getReferenceById(user.getId()))
                .actionAt(LocalDateTime.now())
                .comments(comments)
                .build());
    }
}

