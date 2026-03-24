package com.elyares.etl.unit.facade;

import com.elyares.etl.application.dto.ExecutionAcceptedDto;
import com.elyares.etl.application.facade.PipelineExecutionFacade;
import com.elyares.etl.application.usecase.execution.PipelineExecutionRunner;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineExecutionFacadeTest {

    @Test
    void triggerShouldStartExecutionAsyncAndReturnAcceptedDto() {
        GetPipelineUseCase getPipelineUseCase = mock(GetPipelineUseCase.class);
        PipelineOrchestrationService pipelineOrchestrationService = mock(PipelineOrchestrationService.class);
        ExecutionLifecycleService executionLifecycleService = mock(ExecutionLifecycleService.class);
        PipelineExecutionRunner pipelineExecutionRunner = mock(PipelineExecutionRunner.class);

        Pipeline pipeline = samplePipeline();
        PipelineExecution pending = new PipelineExecution(
            UUID.randomUUID(),
            pipeline.getId(),
            ExecutionId.of("01234567-89ab-cdef-0123-456789abcdef"),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "api:anonymous"
        );
        PipelineExecution running = new PipelineExecution(
            pending.getId(),
            pipeline.getId(),
            pending.getExecutionId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "api:anonymous"
        );
        running.start();

        when(getPipelineUseCase.getDomainByReference("sales-daily")).thenReturn(pipeline);
        doNothing().when(pipelineOrchestrationService).validatePreconditions(any(), any());
        when(executionLifecycleService.createExecution(any(), any(), any())).thenReturn(pending);
        when(executionLifecycleService.markRunning(pending.getExecutionId())).thenReturn(running);

        PipelineExecutionFacade facade = new PipelineExecutionFacade(
            getPipelineUseCase,
            pipelineOrchestrationService,
            executionLifecycleService,
            pipelineExecutionRunner,
            Runnable::run
        );

        ExecutionAcceptedDto result = facade.trigger("sales-daily", null, Map.of("batch_date", "2026-03-23"));

        assertThat(result.executionId()).isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
        assertThat(result.pipelineName()).isEqualTo("sales-daily");
        assertThat(result.status()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(result.triggeredBy()).isEqualTo("api:anonymous");
        verify(pipelineExecutionRunner).continueWithRetries(pipeline, running, "api:anonymous");
    }

    private Pipeline samplePipeline() {
        return new Pipeline(
            PipelineId.of("9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001"),
            "sales-daily",
            "1.0.0",
            "Sales pipeline",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.CSV, "/tmp/sales.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "sales_transactions_staging", "sales_transactions",
                com.elyares.etl.domain.enums.LoadStrategy.UPSERT, List.of("transaction_id"), 1000, true,
                com.elyares.etl.domain.enums.RollbackStrategy.DELETE_BY_EXECUTION, true, "status", "CLOSED"),
            new TransformationConfig(List.of(), null, "UTC", "currency", "USD", "USD", Map.of(), List.of(), List.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), 2, java.math.RoundingMode.HALF_UP, List.of()),
            new ValidationConfig(List.of(), Map.of(), List.of(), Map.of(), null, true, List.of(), false, Map.of(),
                List.of(), Map.of(), Map.of(), Map.of(), false, ErrorThreshold.of(5.0), true),
            new ScheduleConfig(null, "UTC", true, List.of()),
            new RetryPolicy(0, 0L, List.of()),
            Instant.parse("2026-03-23T00:00:00Z"),
            Instant.parse("2026-03-23T00:00:00Z")
        );
    }
}
