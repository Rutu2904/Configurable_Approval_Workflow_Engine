package com.techpulse.workflow.service;

import com.techpulse.workflow.dto.StepInfo;
import com.techpulse.workflow.entity.*;
import com.techpulse.workflow.enums.HistoryAction;
import com.techpulse.workflow.enums.RequestStatus;
import com.techpulse.workflow.exception.*;
import com.techpulse.workflow.repository.*;
import com.techpulse.workflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private RequestRepository requestRepository;
    @Mock private ApprovalStepRepository stepRepository;
    @Mock private ApprovalHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private WorkflowService workflowService;

    private UserPrincipal requesterPrincipal;
    private UserPrincipal approverPrincipal;
    private UserPrincipal adminPrincipal;
    private User requesterUser;
    private User approverUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        requesterUser = new User();
        requesterUser.setId(1L);
        requesterUser.setUsername("alice");
        requesterUser.setRoles(Set.of("REQUESTER"));
        requesterPrincipal = new UserPrincipal(requesterUser);

        approverUser = new User();
        approverUser.setId(2L);
        approverUser.setUsername("bob");
        approverUser.setRoles(Set.of("APPROVER", "MANAGER"));
        approverPrincipal = new UserPrincipal(approverUser);

        adminUser = new User();
        adminUser.setId(5L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of("ADMIN"));
        adminPrincipal = new UserPrincipal(adminUser);
    }

    @Test
    void createRequest_success() {
        ApprovalStep step = new ApprovalStep();
        step.setStepOrder(1);
        step.setRole("MANAGER");
        step.setRequestType("LEAVE");

        when(stepRepository.existsByRequestType("LEAVE")).thenReturn(true);
        when(stepRepository.findByRequestTypeOrderByStepOrderAsc("LEAVE")).thenReturn(List.of(step));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requesterUser));
        when(requestRepository.save(any(Request.class))).thenAnswer(i -> {
            Request r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        Request result = workflowService.createRequest("LEAVE", "Vacation", "Going on trip", requesterPrincipal);

        assertEquals(RequestStatus.PENDING, result.getStatus());
        assertEquals(1, result.getCurrentStepOrder());
        verify(historyRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void createRequest_unknownType() {
        when(stepRepository.existsByRequestType("FOO")).thenReturn(false);
        assertThrows(InvalidWorkflowException.class,
                () -> workflowService.createRequest("FOO", "Test", "desc", requesterPrincipal));
    }

    @Test
    void approve_toNextStep() {
        Request request = buildPendingRequest(1);
        ApprovalStep currentStep = buildStep("LEAVE", 1, "MANAGER");
        ApprovalStep nextStep = buildStep("LEAVE", 2, "HR");

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(stepRepository.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(currentStep));
        when(stepRepository.findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc("LEAVE", 1))
                .thenReturn(Optional.of(nextStep));
        when(userRepository.findById(2L)).thenReturn(Optional.of(approverUser));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Request result = workflowService.approve(1L, "ok", approverPrincipal);

        assertEquals(RequestStatus.PENDING, result.getStatus());
        assertEquals(2, result.getCurrentStepOrder());
    }

    @Test
    void approve_finalStep() {
        Request request = buildPendingRequest(1);
        ApprovalStep currentStep = buildStep("LEAVE", 1, "MANAGER");

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(stepRepository.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(currentStep));
        when(stepRepository.findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc("LEAVE", 1))
                .thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(approverUser));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Request result = workflowService.approve(1L, "approved", approverPrincipal);

        assertEquals(RequestStatus.APPROVED, result.getStatus());
        assertNull(result.getCurrentStepOrder());
    }

    @Test
    void approve_notFound() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> workflowService.approve(99L, "ok", approverPrincipal));
    }

    @Test
    void approve_notPending() {
        Request request = buildPendingRequest(1);
        request.setStatus(RequestStatus.APPROVED);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        assertThrows(InvalidStateTransitionException.class,
                () -> workflowService.approve(1L, "ok", approverPrincipal));
    }

    @Test
    void approve_ownRequest() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        assertThrows(AccessDeniedException.class,
                () -> workflowService.approve(1L, "ok", requesterPrincipal));
    }

    @Test
    void approve_wrongStepRole() {
        Request request = buildPendingRequest(1);
        ApprovalStep currentStep = buildStep("LEAVE", 1, "HR");

        // User has MANAGER role, step needs HR
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(stepRepository.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(currentStep));
        assertThrows(AccessDeniedException.class,
                () -> workflowService.approve(1L, "ok", approverPrincipal));
    }

    @Test
    void reject_success() {
        Request request = buildPendingRequest(1);
        ApprovalStep currentStep = buildStep("LEAVE", 1, "MANAGER");

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(stepRepository.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(currentStep));
        when(userRepository.findById(2L)).thenReturn(Optional.of(approverUser));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Request result = workflowService.reject(1L, "no", approverPrincipal);

        assertEquals(RequestStatus.REJECTED, result.getStatus());
        assertNull(result.getCurrentStepOrder());
    }

    @Test
    void adminOverride_approve() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminUser));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Request result = workflowService.adminOverride(1L, true, "override", adminPrincipal);

        assertEquals(RequestStatus.APPROVED, result.getStatus());
    }

    @Test
    void adminOverride_reject() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminUser));
        when(requestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Request result = workflowService.adminOverride(1L, false, "nope", adminPrincipal);

        assertEquals(RequestStatus.REJECTED, result.getStatus());
    }

    @Test
    void getRequest_requesterCanSeeOwn() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        Request result = workflowService.getRequest(1L, requesterPrincipal);
        assertEquals(1L, result.getId());
    }

    @Test
    void getRequest_requesterCannotSeeOthers() {
        Request request = buildPendingRequest(1);
        request.getCreatedBy().setId(99L); // different user
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(AccessDeniedException.class,
                () -> workflowService.getRequest(1L, requesterPrincipal));
    }

    @Test
    void getHistory_adminAllowed() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(historyRepository.findByRequestIdOrderByActionAtAsc(1L)).thenReturn(List.of());

        List<ApprovalHistory> result = workflowService.getHistory(1L, adminPrincipal);
        assertNotNull(result);
    }

    @Test
    void getHistory_creatorAllowed() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(historyRepository.findByRequestIdOrderByActionAtAsc(1L)).thenReturn(List.of());

        List<ApprovalHistory> result = workflowService.getHistory(1L, requesterPrincipal);
        assertNotNull(result);
    }

    @Test
    void getHistory_otherUserDenied() {
        Request request = buildPendingRequest(1);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(AccessDeniedException.class,
                () -> workflowService.getHistory(1L, approverPrincipal));
    }

    @Test
    void getCurrentStep_exists() {
        Request request = buildPendingRequest(1);
        ApprovalStep step = buildStep("LEAVE", 1, "MANAGER");
        when(stepRepository.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step));

        Optional<StepInfo> result = workflowService.getCurrentStep(request);
        assertTrue(result.isPresent());
        assertEquals("MANAGER", result.get().role());
    }

    @Test
    void getCurrentStep_finished() {
        Request request = buildPendingRequest(1);
        request.setCurrentStepOrder(null);

        Optional<StepInfo> result = workflowService.getCurrentStep(request);
        assertTrue(result.isEmpty());
    }

    // --- Helpers ---

    private Request buildPendingRequest(int stepOrder) {
        Request request = new Request();
        request.setId(1L);
        request.setType("LEAVE");
        request.setTitle("Vacation");
        request.setStatus(RequestStatus.PENDING);
        request.setCurrentStepOrder(stepOrder);
        request.setCreatedBy(requesterUser);
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }

    private ApprovalStep buildStep(String type, int order, String role) {
        ApprovalStep step = new ApprovalStep();
        step.setId((long) order);
        step.setRequestType(type);
        step.setStepOrder(order);
        step.setRole(role);
        return step;
    }
}

