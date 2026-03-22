package com.elyares.etl.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "etl_execution_errors")
public class EtlExecutionErrorEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "error_type", nullable = false)
    private String errorType;

    @Column(name = "error_code")
    private String errorCode;

    @Column(nullable = false)
    private String message;

    @Column(name = "stack_trace")
    private String stackTrace;

    @Column(name = "record_reference")
    private String recordReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
