package com.techpulse.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "approval_steps",
       uniqueConstraints = @UniqueConstraint(columnNames = {"request_type", "step_order"}))
@Getter @Setter @NoArgsConstructor
@Builder
@AllArgsConstructor
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false, length = 30)
    private String role;
}
