package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.application.mapper.PipelineMapper;
import com.elyares.etl.application.usecase.pipeline.ListPipelinesUseCase;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ListPipelinesUseCaseTest {

    @Test
    void shouldListAllPipelines() {
        var pipeline = SampleDataFactory.aPipeline();
        PipelineDto dto = mock(PipelineDto.class);

        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineMapper mapper = mock(PipelineMapper.class);
        when(repository.findAll()).thenReturn(List.of(pipeline));
        when(mapper.toDto(pipeline)).thenReturn(dto);

        ListPipelinesUseCase useCase = new ListPipelinesUseCase(repository, mapper);

        assertThat(useCase.listAll()).containsExactly(dto);
    }

    @Test
    void shouldListOnlyActivePipelines() {
        var pipeline = SampleDataFactory.aPipeline();
        PipelineDto dto = mock(PipelineDto.class);

        PipelineRepository repository = mock(PipelineRepository.class);
        PipelineMapper mapper = mock(PipelineMapper.class);
        when(repository.findAllActive()).thenReturn(List.of(pipeline));
        when(mapper.toDto(pipeline)).thenReturn(dto);

        ListPipelinesUseCase useCase = new ListPipelinesUseCase(repository, mapper);

        assertThat(useCase.listActive()).containsExactly(dto);
    }
}
