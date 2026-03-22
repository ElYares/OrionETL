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
@Table(name = "etl_pipeline_executions")
public class EtlPipelineExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "execution_ref", nullable = false, unique = true)
    private UUID executionRef;

    @Column(nullable = false)
    private String status;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_read", nullable = false)
    private long totalRead;

    @Column(name = "total_transformed", nullable = false)
    private long totalTransformed;

    @Column(name = "total_rejected", nullable = false)
    private long totalRejected;

    @Column(name = "total_loaded", nullable = false)
    private long totalLoaded;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "parent_execution_id")
    private UUID parentExecutionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
