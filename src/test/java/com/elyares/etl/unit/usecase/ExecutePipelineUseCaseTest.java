package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.application.usecase.execution.PipelineExecutionRunner;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExecutePipelineUseCaseTest {

    @Test
    void shouldDelegateToOrchestratorAndReturnDto() {
        var pipeline = SampleDataFactory.aPipeline();
        var request = new ExecutionRequestDto(
            pipeline.getId().toString(),
            TriggerType.MANUAL,
            "tester",
            java.util.Map.of()
        );

        GetPipelineUseCase getPipelineUseCase = mock(GetPipelineUseCase.class);
        PipelineOrchestrationService orchestrationService = mock(PipelineOrchestrationService.class);
        PipelineExecutionRunner pipelineExecutionRunner = mock(PipelineExecutionRunner.class);
        ExecutionMapper mapper = mock(ExecutionMapper.class);

        PipelineExecution created = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        PipelineExecutionDto dto = new PipelineExecutionDto(
            created.getExecutionId().toString(),
            pipeline.getId().toString(),
            pipeline.getName(),
            ExecutionStatus.SUCCESS,
            TriggerType.MANUAL,
            "tester",
            Instant.now(),
            Instant.now(),
            1,
            1,
            0,
            1,
            null,
            List.of()
        );

        when(getPipelineUseCase.getDomainById(request.pipelineId())).thenReturn(pipeline);
        when(pipelineExecutionRunner.run(pipeline, TriggerType.MANUAL, "tester")).thenReturn(created);
        when(mapper.toDto(created, pipeline.getName())).thenReturn(dto);

        ExecutePipelineUseCase useCase = new ExecutePipelineUseCase(
            getPipelineUseCase,
            orchestrationService,
            pipelineExecutionRunner,
            mapper
        );

        PipelineExecutionDto result = useCase.execute(request);

        verify(orchestrationService, times(1)).validatePreconditions(pipeline, request);
        assertThat(result.executionId()).isEqualTo(dto.executionId());
    }
}
