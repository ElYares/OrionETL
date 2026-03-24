package com.elyares.etl.application.usecase.execution;

import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.shared.exception.EtlException;

/**
 * Caso de uso para iniciar una ejecución de reintento.
 */
public class RetryExecutionUseCase {

    private final ExecutionRepository executionRepository;
    private final PipelineRepository pipelineRepository;
    private final RetryEligibilityRule retryEligibilityRule;
    private final ExecutionLifecycleService executionLifecycleService;
    private final ExecutionMapper executionMapper;

    public RetryExecutionUseCase(ExecutionRepository executionRepository,
                                 PipelineRepository pipelineRepository,
                                 RetryEligibilityRule retryEligibilityRule,
                                 ExecutionLifecycleService executionLifecycleService,
                                 ExecutionMapper executionMapper) {
        this.executionRepository = executionRepository;
        this.pipelineRepository = pipelineRepository;
        this.retryEligibilityRule = retryEligibilityRule;
        this.executionLifecycleService = executionLifecycleService;
        this.executionMapper = executionMapper;
    }

    public PipelineExecutionDto execute(String failedExecutionId, String triggeredBy) {
        PipelineExecution retryExecution = createRetryExecution(failedExecutionId, triggeredBy);
        Pipeline pipeline = pipelineRepository.findById(retryExecution.getPipelineId())
            .orElseThrow(() -> new EtlException("ETL_PIPELINE_NOT_FOUND", "Pipeline not found for retry execution"));
        return executionMapper.toDto(retryExecution, pipeline.getName());
    }

    public PipelineExecution createRetryExecution(String failedExecutionId, String triggeredBy) {
        PipelineExecution failedExecution = executionRepository.findByExecutionId(ExecutionId.of(failedExecutionId))
            .orElseThrow(() -> new EtlException("ETL_EXEC_NOT_FOUND", "Execution not found: " + failedExecutionId));

        Pipeline pipeline = pipelineRepository.findById(failedExecution.getPipelineId())
            .orElseThrow(() -> new EtlException("ETL_PIPELINE_NOT_FOUND", "Pipeline not found for execution: " + failedExecutionId));

        retryEligibilityRule.evaluate(
            failedExecution.getRetryCount(),
            resolveLastErrorType(failedExecution),
            pipeline.getRetryPolicy()
        );
        waitBeforeRetry(pipeline);

        PipelineExecution retryExecution = executionLifecycleService.createExecution(
            pipeline.getId(),
            TriggerType.RETRY,
            triggeredBy
        );
        retryExecution.setParentExecutionId(failedExecution.getId());
        for (int i = 0; i <= failedExecution.getRetryCount(); i++) {
            retryExecution.incrementRetryCount();
        }
        return executionRepository.save(retryExecution);
    }

    private void waitBeforeRetry(Pipeline pipeline) {
        long retryDelayMs = pipeline.getRetryPolicy().getRetryDelayMs();
        if (retryDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EtlException("ETL_RETRY_INTERRUPTED", "Retry delay interrupted");
        }
    }

    private ErrorType resolveLastErrorType(PipelineExecution execution) {
        return execution.getErrors().stream()
            .reduce((first, second) -> second)
            .map(ExecutionError::getErrorType)
            .orElse(ErrorType.TECHNICAL);
    }
}
