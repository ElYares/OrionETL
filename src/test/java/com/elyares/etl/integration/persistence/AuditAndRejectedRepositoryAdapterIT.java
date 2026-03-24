package com.elyares.etl.integration.persistence;

import com.elyares.etl.domain.contract.AuditRepository;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAndRejectedRepositoryAdapterIT extends PostgresIntegrationTestBase {

    @Autowired
    private PipelineRepository pipelineRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private RejectedRecordRepository rejectedRecordRepository;

    @Test
    void shouldPersistAuditRecordsWithJsonbDetailsAndReturnByExecution() {
        Pipeline pipeline = pipelineRepository.save(buildPipeline("audit-it"));
        ExecutionId executionId = createExecution(pipeline.getId());

        AuditRecord start = new AuditRecord(
            null,
            executionId,
            pipeline.getId(),
            "PIPELINE_STARTED",
            "SYSTEM",
            Map.of("hostname", "etl-node-1", "attempt", 1)
        );
        AuditRecord finish = new AuditRecord(
            null,
            executionId,
            pipeline.getId(),
            "PIPELINE_FINISHED",
            "SYSTEM",
            Map.of("status", "SUCCESS", "loaded", 42)
        );

        auditRepository.save(start);
        auditRepository.save(finish);

        List<AuditRecord> records = auditRepository.findByExecutionId(executionId);

        assertThat(records).hasSize(2);
        assertThat(records).extracting(AuditRecord::getAction)
            .containsExactly("PIPELINE_STARTED", "PIPELINE_FINISHED");
        assertThat(records.getLast().getDetails()).containsEntry("status", "SUCCESS");

        String detailsType = jdbcTemplate.queryForObject(
            """
            select pg_typeof(ar.details)::text
            from etl_audit_records ar
            join etl_pipeline_executions pe on pe.id = ar.execution_id
            where pe.execution_ref = ?
            limit 1
            """,
            String.class,
            UUID.fromString(executionId.toString())
        );

        assertThat(detailsType).isEqualTo("jsonb");
    }

    @Test
    void shouldPersistRejectedRecordsAndRecoverValidationErrors() {
        Pipeline pipeline = pipelineRepository.save(buildPipeline("rejected-it"));
        ExecutionId executionId = createExecution(pipeline.getId());

        RawRecord rawRecord = new RawRecord(
            15L,
            Map.of("transaction_id", "TX-15", "amount", "NaN"),
            "sales.csv",
            Instant.now()
        );
        ValidationError error = new ValidationError(
            "amount",
            "NaN",
            "DECIMAL_FORMAT",
            "Amount must be decimal",
            ErrorSeverity.ERROR
        );
        RejectedRecord rejected = new RejectedRecord(
            rawRecord,
            "VALIDATE_BUSINESS",
            "Validation failed",
            List.of(error)
        );

        rejectedRecordRepository.saveAll(List.of(rejected), executionId);

        long count = rejectedRecordRepository.countByExecutionId(executionId);
        List<RejectedRecord> loaded = rejectedRecordRepository.findByExecutionId(executionId);

        assertThat(count).isEqualTo(1);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().getStepName()).isEqualTo("VALIDATE_BUSINESS");
        assertThat(loaded.getFirst().getRejectionReason()).isEqualTo("Validation failed");
        assertThat(loaded.getFirst().getValidationErrors()).hasSize(1);
        assertThat(loaded.getFirst().getValidationErrors().getFirst().rule()).isEqualTo("DECIMAL_FORMAT");
        assertThat(loaded.getFirst().getOriginalRecord().getData()).containsEntry("transaction_id", "TX-15");

        String rawType = jdbcTemplate.queryForObject(
            """
            select pg_typeof(rr.raw_data)::text
            from etl_rejected_records rr
            join etl_pipeline_executions pe on pe.id = rr.execution_id
            where pe.execution_ref = ?
            limit 1
            """,
            String.class,
            UUID.fromString(executionId.toString())
        );
        String validationErrorsType = jdbcTemplate.queryForObject(
            """
            select pg_typeof(rr.validation_errors)::text
            from etl_rejected_records rr
            join etl_pipeline_executions pe on pe.id = rr.execution_id
            where pe.execution_ref = ?
            limit 1
            """,
            String.class,
            UUID.fromString(executionId.toString())
        );

        assertThat(rawType).isEqualTo("jsonb");
        assertThat(validationErrorsType).isEqualTo("jsonb");
    }

    private ExecutionId createExecution(PipelineId pipelineId) {
        ExecutionId executionId = ExecutionId.generate();
        PipelineExecution execution = new PipelineExecution(
            null,
            pipelineId,
            executionId,
            TriggerType.MANUAL,
            "integration-test"
        );
        executionRepository.save(execution);
        return executionId;
    }

    private Pipeline buildPipeline(String name) {
        PipelineId id = PipelineId.generate();
        Instant now = Instant.now();

        return new Pipeline(
            id,
            name,
            "1.0.0",
            "Pipeline para test de audit/rejected adapters",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.CSV, "/tmp/source.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "staging_audit", "final_audit", LoadStrategy.UPSERT, List.of("id"), 100),
            TransformationConfig.defaultConfig(),
            new ValidationConfig(List.of("id"), Map.of("id", "STRING"), List.of("id"), ErrorThreshold.of(5.0), true),
            ScheduleConfig.disabled(),
            RetryPolicy.of(1, 500L),
            now,
            now
        );
    }
}
