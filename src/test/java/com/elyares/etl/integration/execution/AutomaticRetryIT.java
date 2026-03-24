package com.elyares.etl.integration.execution;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AutomaticRetryIT extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Test
    void shouldCreateRetryExecutionAfterFailure() {
        Pipeline pipeline = pipelineRepository.save(failingRetryPipeline());

        PipelineExecutionDto result = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "retry-it")
        );

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);

        Integer executionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM etl_pipeline_executions WHERE pipeline_id = ?::uuid",
            Integer.class,
            pipeline.getId().toString()
        );
        assertThat(executionCount).isEqualTo(2);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT execution_ref::text AS execution_ref,
                   trigger_type,
                   retry_count,
                   parent_execution_id::text AS parent_execution_id,
                   status
            FROM etl_pipeline_executions
            WHERE pipeline_id = ?::uuid
            ORDER BY created_at ASC
            """,
            pipeline.getId().toString()
        );

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("trigger_type")).isEqualTo("MANUAL");
        assertThat(rows.get(0).get("retry_count")).isEqualTo(0);
        assertThat(rows.get(0).get("status")).isEqualTo("FAILED");

        assertThat(rows.get(1).get("trigger_type")).isEqualTo("RETRY");
        assertThat(rows.get(1).get("retry_count")).isEqualTo(1);
        assertThat(rows.get(1).get("status")).isEqualTo("FAILED");
        assertThat(rows.get(1).get("parent_execution_id")).isNotNull();
        assertThat(result.executionId()).isEqualTo(rows.get(1).get("execution_ref"));
    }

    private Pipeline failingRetryPipeline() {
        return new Pipeline(
            PipelineId.of("11111111-2222-3333-4444-555555555555"),
            "retry-failing-csv",
            "1.0.0",
            "Pipeline de prueba para validar retry automatico",
            PipelineStatus.ACTIVE,
            new SourceConfig(
                SourceType.CSV,
                "/tmp/orionetl-does-not-exist.csv",
                "UTF-8",
                ',',
                true,
                Map.of()
            ),
            new TargetConfig(
                TargetType.DATABASE,
                "public",
                "sales_transactions_staging",
                "sales_transactions",
                LoadStrategy.UPSERT,
                List.of("transaction_id"),
                100
            ),
            TransformationConfig.defaultConfig(),
            new ValidationConfig(
                List.of("transaction_id"),
                Map.of(),
                List.of("transaction_id"),
                Map.of(),
                null,
                true,
                List.of(),
                false,
                Map.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                false,
                ErrorThreshold.of(5.0),
                true
            ),
            ScheduleConfig.disabled(),
            new RetryPolicy(1, 0L, List.of(ErrorType.TECHNICAL)),
            Instant.parse("2026-03-23T00:00:00Z"),
            Instant.parse("2026-03-23T00:00:00Z")
        );
    }
}
