package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.application.usecase.pipeline.ResolvePipelineConfigUseCase;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ResolvePipelineConfigUseCaseTest {

    @Test
    void shouldResolveById() {
        var pipeline = SampleDataFactory.aPipeline();
        GetPipelineUseCase getPipelineUseCase = mock(GetPipelineUseCase.class);
        when(getPipelineUseCase.getDomainById(pipeline.getId().toString())).thenReturn(pipeline);

        ResolvePipelineConfigUseCase useCase = new ResolvePipelineConfigUseCase(getPipelineUseCase);

        assertThat(useCase.resolveById(pipeline.getId().toString())).isEqualTo(pipeline);
    }

    @Test
    void shouldResolveByName() {
        var pipeline = SampleDataFactory.aPipeline();
        GetPipelineUseCase getPipelineUseCase = mock(GetPipelineUseCase.class);
        when(getPipelineUseCase.getDomainByName(pipeline.getName())).thenReturn(pipeline);

        ResolvePipelineConfigUseCase useCase = new ResolvePipelineConfigUseCase(getPipelineUseCase);

        assertThat(useCase.resolveByName(pipeline.getName())).isEqualTo(pipeline);
    }
}
