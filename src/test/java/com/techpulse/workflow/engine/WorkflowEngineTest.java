package com.techpulse.workflow.engine;

import com.techpulse.workflow.entity.*;
import com.techpulse.workflow.exception.InvalidStateTransitionException;
import com.techpulse.workflow.exception.InvalidWorkflowException;
import com.techpulse.workflow.exception.ResourceNotFoundException;
import com.techpulse.workflow.repository.ApprovalHistoryRepository;
import com.techpulse.workflow.repository.ApprovalStepRepository;
import com.techpulse.workflow.repository.RequestRepository;
import com.techpulse.workflow.repository.UserRepository;
import com.techpulse.workflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private RequestRepository requestRepository;
    @Mock
    private ApprovalStepRepository stepRepo;
    @Mock
    private ApprovalHistoryRepository historyRepo;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkflowEngine workflowEngine;

    private User creatorUser;
    private UserPrincipal creatorPrincipal;
    private UserPrincipal approverPrincipal;
    private UserPrincipal hrApproverPrincipal;
    private UserPrincipal adminPrincipal;
    private UserPrincipal otherRequesterPrincipal;

    @BeforeEach
    void setUp() {
        creatorUser = User.builder().id(1L).username("alice").password("pass").roles(Set.of("REQUESTER")).build();
        creatorPrincipal = new UserPrincipal(1L, "alice", "pass", Set.of("REQUESTER"));
        approverPrincipal = new UserPrincipal(2L, "bob", "pass", Set.of("APPROVER", "MANAGER"));
        hrApproverPrincipal = new UserPrincipal(3L, "carol", "pass", Set.of("APPROVER", "HR"));
        adminPrincipal = new UserPrincipal(5L, "admin", "pass", Set.of("ADMIN"));
        otherRequesterPrincipal = new UserPrincipal(6L, "eve", "pass", Set.of("REQUESTER"));
    }

    @Test
    void createRequest_success() {
        when(stepRepo.existsByRequestType("LEAVE")).thenReturn(true);
        ApprovalStep firstStep = ApprovalStep.builder().id(10L).requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        when(stepRepo.findByRequestTypeOrderByStepOrderAsc("LEAVE")).thenReturn(List.of(firstStep));
        when(userRepository.getReferenceById(1L)).thenReturn(creatorUser);
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request req = workflowEngine.createRequest("LEAVE", "Trip", "Going to Hawaii", creatorPrincipal);

        assertThat(req.getType()).isEqualTo("LEAVE");
        assertThat(req.getTitle()).isEqualTo("Trip");
        assertThat(req.getDescription()).isEqualTo("Going to Hawaii");
        assertThat(req.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(req.getCurrentStepOrder()).isEqualTo(1);
        verify(historyRepo).save(any(ApprovalHistory.class));
    }

    @Test
    void createRequest_unknownType_throwsInvalidWorkflowException() {
        when(stepRepo.existsByRequestType("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> workflowEngine.createRequest("UNKNOWN", "Test", "Desc", creatorPrincipal))
                .isInstanceOf(InvalidWorkflowException.class)
                .hasMessageContaining("Unknown request type: UNKNOWN");
    }

    @Test
    void approve_movesToNextStep_andSavesHistory() {
        Request req = Request.builder()
                .id(100L).type("LEAVE").status(RequestStatus.PENDING).currentStepOrder(1)
                .createdBy(creatorUser).createdAt(LocalDateTime.now()).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        ApprovalStep step1 = ApprovalStep.builder().requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        ApprovalStep step2 = ApprovalStep.builder().requestType("LEAVE").stepOrder(2).role("HR").build();

        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step1));
        when(stepRepo.findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc("LEAVE", 1))
                .thenReturn(Optional.of(step2));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request updated = workflowEngine.approve(100L, "Looks good", approverPrincipal);

        assertThat(updated.getCurrentStepOrder()).isEqualTo(2);
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.PENDING);
        verify(historyRepo).save(argThat(h ->
                h.getAction() == HistoryAction.APPROVED &&
                h.getStepOrder() == 1 &&
                "Looks good".equals(h.getComments())));
    }

    @Test
    void approve_lastStep_setsApprovedAndCurrentStepNull() {
        Request req = Request.builder()
                .id(100L).type("LEAVE").status(RequestStatus.PENDING).currentStepOrder(2)
                .createdBy(creatorUser).createdAt(LocalDateTime.now()).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        ApprovalStep step2 = ApprovalStep.builder().requestType("LEAVE").stepOrder(2).role("HR").build();
        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 2)).thenReturn(Optional.of(step2));
        when(stepRepo.findFirstByRequestTypeAndStepOrderGreaterThanOrderByStepOrderAsc("LEAVE", 2))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request updated = workflowEngine.approve(100L, "Final approval", hrApproverPrincipal);

        assertThat(updated.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(updated.getCurrentStepOrder()).isNull();
    }

    @Test
    void approve_requestNotFound_throwsResourceNotFoundException() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workflowEngine.approve(999L, "Notes", approverPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_statusNotPending_throwsInvalidStateTransitionException() {
        Request req = Request.builder().id(100L).status(RequestStatus.APPROVED).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> workflowEngine.approve(100L, "Notes", approverPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void approve_creatorApprovingOwn_throwsAccessDeniedException() {
        Request req = Request.builder()
                .id(100L).status(RequestStatus.PENDING).createdBy(creatorUser).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> workflowEngine.approve(100L, "Notes", creatorPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void approve_userLacksStepRole_throwsAccessDeniedException() {
        Request req = Request.builder()
                .id(100L).type("LEAVE").status(RequestStatus.PENDING).currentStepOrder(1)
                .createdBy(creatorUser).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));
        ApprovalStep step1 = ApprovalStep.builder().requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step1));

        assertThatThrownBy(() -> workflowEngine.approve(100L, "Notes", hrApproverPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void reject_success() {
        Request req = Request.builder()
                .id(100L).type("LEAVE").status(RequestStatus.PENDING).currentStepOrder(1)
                .createdBy(creatorUser).createdAt(LocalDateTime.now()).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));
        ApprovalStep step1 = ApprovalStep.builder().requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step1));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request rejected = workflowEngine.reject(100L, "Denied", approverPrincipal);

        assertThat(rejected.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected.getCurrentStepOrder()).isNull();
        verify(historyRepo).save(argThat(h ->
                h.getAction() == HistoryAction.REJECTED &&
                h.getStepOrder() == 1 &&
                "Denied".equals(h.getComments())));
    }

    @Test
    void reject_failures() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> workflowEngine.reject(999L, "Notes", approverPrincipal))
                .isInstanceOf(ResourceNotFoundException.class);

        Request notPending = Request.builder().id(100L).status(RequestStatus.REJECTED).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(notPending));
        assertThatThrownBy(() -> workflowEngine.reject(100L, "Notes", approverPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        Request ownReq = Request.builder().id(101L).status(RequestStatus.PENDING).createdBy(creatorUser).build();
        when(requestRepository.findById(101L)).thenReturn(Optional.of(ownReq));
        assertThatThrownBy(() -> workflowEngine.reject(101L, "Notes", creatorPrincipal))
                .isInstanceOf(AccessDeniedException.class);

        Request wrongRoleReq = Request.builder().id(102L).type("LEAVE").status(RequestStatus.PENDING).currentStepOrder(1).createdBy(creatorUser).build();
        when(requestRepository.findById(102L)).thenReturn(Optional.of(wrongRoleReq));
        ApprovalStep step1 = ApprovalStep.builder().requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step1));
        assertThatThrownBy(() -> workflowEngine.reject(102L, "Notes", hrApproverPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminOverride_approve_success() {
        Request req = Request.builder()
                .id(100L).status(RequestStatus.PENDING).currentStepOrder(1).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request result = workflowEngine.adminOverride(100L, true, "Admin approved", adminPrincipal);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(result.getCurrentStepOrder()).isNull();
        verify(historyRepo).save(argThat(h -> h.getAction() == HistoryAction.ADMIN_APPROVED));
    }

    @Test
    void adminOverride_reject_success() {
        Request req = Request.builder()
                .id(100L).status(RequestStatus.PENDING).currentStepOrder(1).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));

        Request result = workflowEngine.adminOverride(100L, false, "Admin rejected", adminPrincipal);

        assertThat(result.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.getCurrentStepOrder()).isNull();
        verify(historyRepo).save(argThat(h -> h.getAction() == HistoryAction.ADMIN_REJECTED));
    }

    @Test
    void adminOverride_failsWhenNotPending() {
        Request req = Request.builder().id(100L).status(RequestStatus.APPROVED).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> workflowEngine.adminOverride(100L, true, "Notes", adminPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void getRequest_ownerAllowed_approverAdminAllowed_otherDenied() {
        Request req = Request.builder().id(100L).createdBy(creatorUser).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        assertThat(workflowEngine.getRequest(100L, creatorPrincipal)).isNotNull();
        assertThat(workflowEngine.getRequest(100L, approverPrincipal)).isNotNull();
        assertThat(workflowEngine.getRequest(100L, adminPrincipal)).isNotNull();

        assertThatThrownBy(() -> workflowEngine.getRequest(100L, otherRequesterPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getHistory_adminAllowed_creatorAllowed_otherDenied() {
        Request req = Request.builder().id(100L).createdBy(creatorUser).build();
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));
        when(historyRepo.findByRequestIdOrderByActionAtAsc(100L)).thenReturn(List.of());

        assertThat(workflowEngine.getHistory(100L, creatorPrincipal)).isNotNull();
        assertThat(workflowEngine.getHistory(100L, adminPrincipal)).isNotNull();

        assertThatThrownBy(() -> workflowEngine.getHistory(100L, approverPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getCurrentStep_presentAndEmpty() {
        Request withOrder = Request.builder().type("LEAVE").currentStepOrder(1).build();
        ApprovalStep step = ApprovalStep.builder().requestType("LEAVE").stepOrder(1).role("MANAGER").build();
        when(stepRepo.findByRequestTypeAndStepOrder("LEAVE", 1)).thenReturn(Optional.of(step));

        assertThat(workflowEngine.getCurrentStep(withOrder)).contains(step);

        Request noOrder = Request.builder().type("LEAVE").currentStepOrder(null).build();
        assertThat(workflowEngine.getCurrentStep(noOrder)).isEmpty();
    }
}

