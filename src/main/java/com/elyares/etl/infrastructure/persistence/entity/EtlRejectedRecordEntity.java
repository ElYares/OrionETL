package com.elyares.etl.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
@Table(name = "etl_rejected_records")
public class EtlRejectedRecordEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawData;

    @Column(name = "rejection_reason", nullable = false)
    private String rejectionReason;

    @Column(name = "validation_errors", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String validationErrors;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;
}
