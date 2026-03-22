package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.application.mapper.PipelineMapper;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.PipelineNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GetPipelineUseCaseTest {

    @Test
    void shouldReturnDomainById() {
        var pipeline = SampleDataFactory.aPipeline();
        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineMapper mapper = mock(PipelineMapper.class);
        when(repository.findById(pipeline.getId())).thenReturn(Optional.of(pipeline));

        GetPipelineUseCase useCase = new GetPipelineUseCase(repository, mapper);

        assertThat(useCase.getDomainById(pipeline.getId().toString())).isEqualTo(pipeline);
    }

    @Test
    void shouldThrowWhenPipelineNotFoundById() {
        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineMapper mapper = mock(PipelineMapper.class);
        when(repository.findById(any())).thenReturn(Optional.empty());

        GetPipelineUseCase useCase = new GetPipelineUseCase(repository, mapper);

        assertThatThrownBy(() -> useCase.getDomainById(SampleDataFactory.aPipelineId().toString()))
            .isInstanceOf(PipelineNotFoundException.class);
    }

    @Test
    void shouldReturnDtoByName() {
        var pipeline = SampleDataFactory.aPipeline();
        PipelineDto dto = new PipelineDto(
            pipeline.getId().toString(),
            pipeline.getName(),
            pipeline.getVersion(),
            pipeline.getDescription(),
            pipeline.getStatus(),
            pipeline.getSourceConfig().getType(),
            pipeline.getTargetConfig().getType(),
            pipeline.getCreatedAt(),
            pipeline.getUpdatedAt()
        );

        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineMapper mapper = mock(PipelineMapper.class);
        when(repository.findByName(pipeline.getName())).thenReturn(Optional.of(pipeline));
        when(mapper.toDto(pipeline)).thenReturn(dto);

        GetPipelineUseCase useCase = new GetPipelineUseCase(repository, mapper);

        assertThat(useCase.getByName(pipeline.getName())).isEqualTo(dto);
    }
}
