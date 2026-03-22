package com.elyares.etl.application.usecase.execution;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.orchestrator.ETLOrchestrator;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.service.PipelineOrchestrationService;

/**
 * Caso de uso de entrada para ejecutar un pipeline.
 */
public class ExecutePipelineUseCase {

    private final GetPipelineUseCase getPipelineUseCase;
    private final PipelineOrchestrationService pipelineOrchestrationService;
    private final ExecutionLifecycleService executionLifecycleService;
    private final ETLOrchestrator etlOrchestrator;
    private final ExecutionMapper executionMapper;

    public ExecutePipelineUseCase(GetPipelineUseCase getPipelineUseCase,
                                  PipelineOrchestrationService pipelineOrchestrationService,
                                  ExecutionLifecycleService executionLifecycleService,
                                  ETLOrchestrator etlOrchestrator,
                                  ExecutionMapper executionMapper) {
        this.getPipelineUseCase = getPipelineUseCase;
        this.pipelineOrchestrationService = pipelineOrchestrationService;
        this.executionLifecycleService = executionLifecycleService;
        this.etlOrchestrator = etlOrchestrator;
        this.executionMapper = executionMapper;
    }

    public PipelineExecutionDto execute(ExecutionRequestDto request) {
        Pipeline pipeline = getPipelineUseCase.getDomainById(request.pipelineId());
        pipelineOrchestrationService.validatePreconditions(pipeline, request);

        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            request.triggerType(),
            request.triggeredBy()
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());
        execution = etlOrchestrator.orchestrate(pipeline, execution);

        return executionMapper.toDto(execution, pipeline.getName());
    }
}
