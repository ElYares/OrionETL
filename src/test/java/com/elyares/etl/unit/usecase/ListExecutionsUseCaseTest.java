package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.usecase.execution.ListExecutionsUseCase;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.PipelineNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ListExecutionsUseCaseTest {

    @Test
    void shouldReturnMappedExecutionsForPipeline() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        PipelineExecutionDto dto = new PipelineExecutionDto(
            execution.getExecutionId().toString(),
            pipeline.getId().toString(),
            pipeline.getName(),
            ExecutionStatus.PENDING,
            TriggerType.MANUAL,
            "tester",
            execution.getCreatedAt(),
            null,
            0,
            0,
            0,
            0,
            null,
            List.of()
        );

        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);

        when(pipelineRepository.findById(pipeline.getId())).thenReturn(Optional.of(pipeline));
        when(executionRepository.findByPipelineId(pipeline.getId(), 10)).thenReturn(List.of(execution));
        when(executionMapper.toDto(execution, pipeline.getName())).thenReturn(dto);

        ListExecutionsUseCase useCase = new ListExecutionsUseCase(executionRepository, pipelineRepository, executionMapper);

        assertThat(useCase.execute(pipeline.getId().toString(), 10)).containsExactly(dto);
    }

    @Test
    void shouldThrowWhenPipelineNotFound() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);

        when(pipelineRepository.findById(any())).thenReturn(Optional.empty());

        ListExecutionsUseCase useCase = new ListExecutionsUseCase(executionRepository, pipelineRepository, executionMapper);

        assertThatThrownBy(() -> useCase.execute(SampleDataFactory.aPipelineId().toString(), 10))
            .isInstanceOf(PipelineNotFoundException.class);
    }
}
