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
@Table(name = "etl_audit_records")
public class EtlAuditRecordEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Column(nullable = false)
    private String action;

    @Column(name = "actor_type")
    private String actorType;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
