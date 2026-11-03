package com.techpulse.workflow.service;

import com.techpulse.workflow.dto.StepInfo;
import com.techpulse.workflow.entity.*;
import com.techpulse.workflow.enums.HistoryAction;
import com.techpulse.workflow.enums.RequestStatus;
import com.techpulse.workflow.exception.*;
import com.techpulse.workflow.repository.*;
import com.techpulse.workflow.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WorkflowService {

    private final RequestRepository requestRepository;
    private final ApprovalStepRepository stepRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public WorkflowService(RequestRepository requestRepository,
                           ApprovalStepRepository stepRepository,
                           ApprovalHistoryRepository historyRepository,
                           UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.stepRepository = stepRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    // Create a new approval request
    @Transactional
    public Request createRequest(String type, String title, String description, UserPrincipal user) {
        // Check if workflow type exists in DB
        if (!stepRepository.existsByRequestType(type)) {
            throw new InvalidWorkflowException("Unknown workflow type: " + type);
        }

        // Get the first step from DB
        List<ApprovalStep> steps = stepRepository.findByRequestTypeOrderByStepOrderAsc(type);
        ApprovalStep firstStep = steps.get(0);

        User creator = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Save the request
        Request request = new Request();
        request.setType(type);
        request.setTitle(title);
        request.setDescription(description);
        request.setStatus(RequestStatus.PENDING);
        request.setCurrentStepOrder(firstStep.getStepOrder());
        request.setCreatedBy(creator);
        request.setCreatedAt(LocalDateTime.now());
        request = requestRepository.save(request);

        // Record history
        saveHistory(request, firstStep.getStepOrder(), HistoryAction.CREATED, creator, null);

        return request;
    }

    // Approve the current step
    @Transactional
    public Request approve(Long id, String comments, UserPrincipal user) {
        Request request = findRequestOrThrow(id);
        checkPending(request);
        checkNotCreator(request, user);
        ApprovalStep currentStep = checkUserHasStepRole(request, user);

        User actor = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if there is a next step
        Optional<ApprovalStep> nextStep = stepRepository
                .findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc(
                        request.getType(), currentStep.getStepOrder());

        if (nextStep.isPresent()) {
            // Move to next step
            request.setCurrentStepOrder(nextStep.get().getStepOrder());
        } else {
            // Final approval
            request.setStatus(RequestStatus.APPROVED);
            request.setCurrentStepOrder(null);
        }

        saveHistory(request, currentStep.getStepOrder(), HistoryAction.APPROVED, actor, comments);
        return requestRepository.save(request);
    }

    // Reject the request
    @Transactional
    public Request reject(Long id, String comments, UserPrincipal user) {
        Request request = findRequestOrThrow(id);
        checkPending(request);
        checkNotCreator(request, user);
        ApprovalStep currentStep = checkUserHasStepRole(request, user);

        User actor = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        request.setStatus(RequestStatus.REJECTED);
        request.setCurrentStepOrder(null);

        saveHistory(request, currentStep.getStepOrder(), HistoryAction.REJECTED, actor, comments);
        return requestRepository.save(request);
    }

    // Admin can override any pending request
    @Transactional
    public Request adminOverride(Long id, boolean approve, String comments, UserPrincipal admin) {
        Request request = findRequestOrThrow(id);
        checkPending(request);

        User actor = userRepository.findById(admin.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        request.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        HistoryAction action = approve ? HistoryAction.ADMIN_APPROVED : HistoryAction.ADMIN_REJECTED;

        saveHistory(request, request.getCurrentStepOrder(), action, actor, comments);
        request.setCurrentStepOrder(null);
        return requestRepository.save(request);
    }

    // Get a request - REQUESTER-only users can see only their own
    public Request getRequest(Long id, UserPrincipal user) {
        Request request = findRequestOrThrow(id);

        // If user only has REQUESTER role, they can only see their own requests
        boolean onlyRequester = user.getRoles().size() == 1 && user.getRoles().contains("REQUESTER");
        if (onlyRequester && !request.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only view your own requests");
        }

        return request;
    }

    // Get history - only ADMIN or the creator
    public List<ApprovalHistory> getHistory(Long requestId, UserPrincipal user) {
        Request request = findRequestOrThrow(requestId);

        boolean isAdmin = user.getRoles().contains("ADMIN");
        boolean isCreator = request.getCreatedBy().getId().equals(user.getId());

        if (!isAdmin && !isCreator) {
            throw new AccessDeniedException("Only admin or the request creator can view history");
        }

        return historyRepository.findByRequestIdOrderByActionAtAsc(requestId);
    }

    // Get the current step info for a request
    public Optional<StepInfo> getCurrentStep(Request request) {
        if (request.getCurrentStepOrder() == null) {
            return Optional.empty();
        }
        return stepRepository.findByRequestTypeAndStepOrder(request.getType(), request.getCurrentStepOrder())
                .map(step -> new StepInfo(step.getStepOrder(), step.getRole()));
    }

    // --- Private helpers ---

    private Request findRequestOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + id));
    }

    private void checkPending(Request request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Request is not pending, current status: " + request.getStatus());
        }
    }

    private void checkNotCreator(Request request, UserPrincipal user) {
        if (request.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You cannot approve/reject your own request");
        }
    }

    // Check that the user has the role required by the current step
    private ApprovalStep checkUserHasStepRole(Request request, UserPrincipal user) {
        ApprovalStep currentStep = stepRepository
                .findByRequestTypeAndStepOrder(request.getType(), request.getCurrentStepOrder())
                .orElseThrow(() -> new InvalidWorkflowException("Current step not found in workflow"));

        if (!user.getRoles().contains(currentStep.getRole())) {
            throw new AccessDeniedException(
                    "You don't have the required role for this approval step");
        }
        return currentStep;
    }

    private void saveHistory(Request request, Integer stepOrder, HistoryAction action,
                             User actor, String comments) {
        ApprovalHistory history = new ApprovalHistory();
        history.setRequest(request);
        history.setStepOrder(stepOrder);
        history.setAction(action);
        history.setActionBy(actor);
        history.setActionAt(LocalDateTime.now());
        history.setComments(comments);
        historyRepository.save(history);
    }
}

