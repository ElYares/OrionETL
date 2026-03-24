package com.elyares.etl.application.facade;

import com.elyares.etl.application.dto.ExecutionAcceptedDto;
import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.application.usecase.execution.PipelineExecutionRunner;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import org.springframework.core.task.TaskExecutor;

import java.util.Map;

/**
 * Fachada de entrada para disparar ejecuciones asincronas desde interfaces externas.
 */
public class PipelineExecutionFacade {

    private final GetPipelineUseCase getPipelineUseCase;
    private final PipelineOrchestrationService pipelineOrchestrationService;
    private final ExecutionLifecycleService executionLifecycleService;
    private final PipelineExecutionRunner pipelineExecutionRunner;
    private final TaskExecutor taskExecutor;

    public PipelineExecutionFacade(GetPipelineUseCase getPipelineUseCase,
                                   PipelineOrchestrationService pipelineOrchestrationService,
                                   ExecutionLifecycleService executionLifecycleService,
                                   PipelineExecutionRunner pipelineExecutionRunner,
                                   TaskExecutor taskExecutor) {
        this.getPipelineUseCase = getPipelineUseCase;
        this.pipelineOrchestrationService = pipelineOrchestrationService;
        this.executionLifecycleService = executionLifecycleService;
        this.pipelineExecutionRunner = pipelineExecutionRunner;
        this.taskExecutor = taskExecutor;
    }

    public ExecutionAcceptedDto trigger(String pipelineRef,
                                        String triggeredBy,
                                        Map<String, String> parameters) {
        Pipeline pipeline = getPipelineUseCase.getDomainByReference(pipelineRef);
        ExecutionRequestDto request = new ExecutionRequestDto(
            pipeline.getId().toString(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            normalizeTriggeredBy(triggeredBy),
            parameters != null ? Map.copyOf(parameters) : Map.of()
        );

        pipelineOrchestrationService.validatePreconditions(pipeline, request);

        PipelineExecution pending = executionLifecycleService.createExecution(
            pipeline.getId(),
            request.triggerType(),
            request.triggeredBy()
        );
        PipelineExecution running = executionLifecycleService.markRunning(pending.getExecutionId());

        taskExecutor.execute(() -> pipelineExecutionRunner.continueWithRetries(
            pipeline,
            running,
            request.triggeredBy()
        ));

        return new ExecutionAcceptedDto(
            running.getExecutionId().toString(),
            pipeline.getId().toString(),
            pipeline.getName(),
            running.getStatus(),
            running.getStartedAt(),
            running.getTriggeredBy()
        );
    }

    private String normalizeTriggeredBy(String triggeredBy) {
        if (triggeredBy == null || triggeredBy.isBlank()) {
            return "api:anonymous";
        }
        return triggeredBy.trim();
    }
}
