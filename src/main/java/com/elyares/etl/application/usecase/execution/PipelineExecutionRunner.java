package com.elyares.etl.application.usecase.execution;

import com.elyares.etl.application.orchestrator.ETLOrchestrator;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.shared.exception.RetryExhaustedException;

/**
 * Ejecuta un pipeline y, si falla con una condición reintentable, encadena nuevos intentos.
 */
public class PipelineExecutionRunner {

    private final ExecutionLifecycleService executionLifecycleService;
    private final ETLOrchestrator etlOrchestrator;
    private final RetryExecutionUseCase retryExecutionUseCase;

    public PipelineExecutionRunner(ExecutionLifecycleService executionLifecycleService,
                                   ETLOrchestrator etlOrchestrator,
                                   RetryExecutionUseCase retryExecutionUseCase) {
        this.executionLifecycleService = executionLifecycleService;
        this.etlOrchestrator = etlOrchestrator;
        this.retryExecutionUseCase = retryExecutionUseCase;
    }

    public PipelineExecution run(Pipeline pipeline, TriggerType triggerType, String triggeredBy) {
        PipelineExecution pending = executionLifecycleService.createExecution(
            pipeline.getId(),
            triggerType,
            triggeredBy
        );
        PipelineExecution running = executionLifecycleService.markRunning(pending.getExecutionId());
        return continueWithRetries(pipeline, running, triggeredBy);
    }

    public PipelineExecution continueWithRetries(Pipeline pipeline,
                                                 PipelineExecution runningExecution,
                                                 String retryTriggeredBy) {
        PipelineExecution current = etlOrchestrator.orchestrate(pipeline, runningExecution);

        while (current.getStatus() == ExecutionStatus.FAILED) {
            PipelineExecution retryPending;
            try {
                retryPending = retryExecutionUseCase.createRetryExecution(
                    current.getExecutionId().toString(),
                    retryTriggeredBy
                );
            } catch (RetryExhaustedException | IllegalStateException ex) {
                return current;
            }

            PipelineExecution retryRunning = executionLifecycleService.markRunning(retryPending.getExecutionId());
            current = etlOrchestrator.orchestrate(pipeline, retryRunning);
        }

        return current;
    }
}
