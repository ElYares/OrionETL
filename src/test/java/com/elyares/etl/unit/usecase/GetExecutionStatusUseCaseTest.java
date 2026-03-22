package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.ExecutionStatusDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.application.usecase.execution.GetExecutionStatusUseCase;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.EtlException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GetExecutionStatusUseCaseTest {

    @Test
    void shouldReturnStatusDtoWhenExecutionExists() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        ExecutionStatusDto dto = new ExecutionStatusDto(
            execution.getExecutionId().toString(),
            ExecutionStatus.RUNNING,
            1,
            0,
            0,
            Instant.now()
        );

        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);
        when(executionRepository.findByExecutionId(execution.getExecutionId())).thenReturn(Optional.of(execution));
        when(executionMapper.toStatusDto(execution)).thenReturn(dto);

        GetExecutionStatusUseCase useCase = new GetExecutionStatusUseCase(executionRepository, executionMapper);

        assertThat(useCase.execute(execution.getExecutionId().toString())).isEqualTo(dto);
    }

    @Test
    void shouldThrowWhenExecutionNotFound() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        ExecutionMapper executionMapper = mock(ExecutionMapper.class);
        when(executionRepository.findByExecutionId(any())).thenReturn(Optional.empty());

        GetExecutionStatusUseCase useCase = new GetExecutionStatusUseCase(executionRepository, executionMapper);

        assertThatThrownBy(() -> useCase.execute(SampleDataFactory.anExecutionId().toString()))
            .isInstanceOf(EtlException.class)
            .hasMessageContaining("Execution not found");
    }
}
