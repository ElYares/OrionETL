package com.elyares.etl.integration.persistence;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.domain.valueobject.RecordCount;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionRepositoryAdapterIT extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Test
    void shouldPersistExecutionAndLoadStepsErrorsAndActiveState() {
        Pipeline pipeline = pipelineRepository.save(buildPipeline("exec-active-it"));
        ExecutionId executionId = ExecutionId.generate();

        PipelineExecution execution = new PipelineExecution(
            null,
            pipeline.getId(),
            executionId,
            TriggerType.MANUAL,
            "it-user"
        );
        execution.start();

        PipelineExecutionStep extractStep = new PipelineExecutionStep(null, executionId, "EXTRACT", 1);
        extractStep.markSuccess(120);
        execution.addStep(extractStep);

        ExecutionError error = new ExecutionError(
            null,
            executionId,
            "TRANSFORM",
            ErrorType.DATA_QUALITY,
            null,
            "DQ-001",
            "Invalid amount format",
            null,
            "row=8"
        );
        execution.addError(error);

        executionRepository.save(execution);

        Optional<PipelineExecution> found = executionRepository.findByExecutionId(executionId);
        Optional<PipelineExecution> active = executionRepository.findActiveByPipelineId(pipeline.getId());

        assertThat(found).isPresent();
        PipelineExecution loaded = found.orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(loaded.getSteps()).hasSize(1);
        assertThat(loaded.getSteps().getFirst().getStepName()).isEqualTo("EXTRACT");
        assertThat(loaded.getSteps().getFirst().getRecordsProcessed()).isEqualTo(120);
        assertThat(loaded.getErrors()).hasSize(1);
        assertThat(loaded.getErrors().getFirst().getErrorCode()).isEqualTo("DQ-001");

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().getExecutionId()).isEqualTo(executionId);

        execution.complete(
            RecordCount.of(120),
            RecordCount.of(118),
            RecordCount.of(2),
            RecordCount.of(118)
        );
        executionRepository.save(execution);

        Optional<PipelineExecution> noActive = executionRepository.findActiveByPipelineId(pipeline.getId());
        List<PipelineExecution> history = executionRepository.findByPipelineId(pipeline.getId(), 5);

        assertThat(noActive).isEmpty();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(history.getFirst().getTotalLoaded().value()).isEqualTo(118);
    }

    @Test
    void shouldReturnRetryingExecutionAsActive() {
        Pipeline pipeline = pipelineRepository.save(buildPipeline("exec-retrying-it"));

        PipelineExecution retrying = new PipelineExecution(
            null,
            pipeline.getId(),
            ExecutionId.generate(),
            TriggerType.SCHEDULED,
            "scheduler"
        );
        retrying.setStatus(ExecutionStatus.RETRYING);
        retrying.incrementRetryCount();

        executionRepository.save(retrying);

        Optional<PipelineExecution> active = executionRepository.findActiveByPipelineId(pipeline.getId());

        assertThat(active).isPresent();
        assertThat(active.orElseThrow().getStatus()).isEqualTo(ExecutionStatus.RETRYING);
        assertThat(active.orElseThrow().getRetryCount()).isEqualTo(1);
    }

    private Pipeline buildPipeline(String name) {
        PipelineId id = PipelineId.generate();
        Instant now = Instant.now();

        return new Pipeline(
            id,
            name,
            "1.0.0",
            "Pipeline para test de execution adapter",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.CSV, "/tmp/source.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "staging_exec", "final_exec", LoadStrategy.UPSERT, List.of("id"), 100),
            new ValidationConfig(List.of("id"), Map.of("id", "STRING"), List.of("id"), ErrorThreshold.of(5.0), true),
            ScheduleConfig.disabled(),
            RetryPolicy.of(3, 1000L),
            now,
            now
        );
    }
}
