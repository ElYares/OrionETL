package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.orchestrator.ETLOrchestrator;
import com.elyares.etl.application.usecase.execution.PipelineExecutionRunner;
import com.elyares.etl.application.usecase.execution.RetryExecutionUseCase;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.RetryExhaustedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PipelineExecutionRunnerTest {

    @Test
    void shouldRetryOnceAndReturnSuccessfulRetryExecution() {
        var pipeline = SampleDataFactory.aPipeline();
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ETLOrchestrator etlOrchestrator = mock(ETLOrchestrator.class);
        RetryExecutionUseCase retryExecutionUseCase = mock(RetryExecutionUseCase.class);

        PipelineExecution firstPending = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        PipelineExecution firstRunning = new PipelineExecution(
            firstPending.getId(),
            pipeline.getId(),
            firstPending.getExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        firstRunning.start();
        PipelineExecution firstFailed = new PipelineExecution(
            firstRunning.getId(),
            pipeline.getId(),
            firstRunning.getExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        firstFailed.fail("first failure");

        PipelineExecution retryPending = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.RETRY,
            "tester"
        );
        retryPending.incrementRetryCount();
        PipelineExecution retryRunning = new PipelineExecution(
            retryPending.getId(),
            pipeline.getId(),
            retryPending.getExecutionId(),
            TriggerType.RETRY,
            "tester"
        );
        retryRunning.incrementRetryCount();
        retryRunning.start();
        PipelineExecution retrySucceeded = new PipelineExecution(
            retryRunning.getId(),
            pipeline.getId(),
            retryRunning.getExecutionId(),
            TriggerType.RETRY,
            "tester"
        );
        retrySucceeded.incrementRetryCount();
        retrySucceeded.complete(
            com.elyares.etl.domain.valueobject.RecordCount.of(10),
            com.elyares.etl.domain.valueobject.RecordCount.of(10),
            com.elyares.etl.domain.valueobject.RecordCount.zero(),
            com.elyares.etl.domain.valueobject.RecordCount.of(10)
        );

        when(lifecycleService.createExecution(pipeline.getId(), TriggerType.MANUAL, "tester")).thenReturn(firstPending);
        when(lifecycleService.markRunning(firstPending.getExecutionId())).thenReturn(firstRunning);
        when(etlOrchestrator.orchestrate(pipeline, firstRunning)).thenReturn(firstFailed);
        when(retryExecutionUseCase.createRetryExecution(firstFailed.getExecutionId().toString(), "tester"))
            .thenReturn(retryPending);
        when(lifecycleService.markRunning(retryPending.getExecutionId())).thenReturn(retryRunning);
        when(etlOrchestrator.orchestrate(pipeline, retryRunning)).thenReturn(retrySucceeded);

        PipelineExecutionRunner runner = new PipelineExecutionRunner(lifecycleService, etlOrchestrator, retryExecutionUseCase);

        PipelineExecution result = runner.run(pipeline, TriggerType.MANUAL, "tester");

        assertThat(result.getExecutionId()).isEqualTo(retrySucceeded.getExecutionId());
        verify(retryExecutionUseCase).createRetryExecution(firstFailed.getExecutionId().toString(), "tester");
    }

    @Test
    void shouldStopWhenRetryIsExhausted() {
        var pipeline = SampleDataFactory.aPipeline();
        ExecutionLifecycleService lifecycleService = mock(ExecutionLifecycleService.class);
        ETLOrchestrator etlOrchestrator = mock(ETLOrchestrator.class);
        RetryExecutionUseCase retryExecutionUseCase = mock(RetryExecutionUseCase.class);

        PipelineExecution firstPending = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        PipelineExecution firstRunning = new PipelineExecution(
            firstPending.getId(),
            pipeline.getId(),
            firstPending.getExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        firstRunning.start();
        PipelineExecution firstFailed = new PipelineExecution(
            firstRunning.getId(),
            pipeline.getId(),
            firstRunning.getExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        firstFailed.fail("final failure");

        when(lifecycleService.createExecution(pipeline.getId(), TriggerType.MANUAL, "tester")).thenReturn(firstPending);
        when(lifecycleService.markRunning(firstPending.getExecutionId())).thenReturn(firstRunning);
        when(etlOrchestrator.orchestrate(pipeline, firstRunning)).thenReturn(firstFailed);
        when(retryExecutionUseCase.createRetryExecution(anyString(), anyString()))
            .thenThrow(new RetryExhaustedException("pipeline", 1));

        PipelineExecutionRunner runner = new PipelineExecutionRunner(lifecycleService, etlOrchestrator, retryExecutionUseCase);

        PipelineExecution result = runner.run(pipeline, TriggerType.MANUAL, "tester");

        assertThat(result.getExecutionId()).isEqualTo(firstFailed.getExecutionId());
    }
}
