package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.transformation.TransformDataUseCase;
import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.TransformationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransformDataUseCaseTest {

    @Test
    void shouldUsePipelineTransformerIgnoringCase() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        var rawRecords = List.of(SampleDataFactory.aRawRecord());
        var expected = TransformationResult.of(
            List.of(new ProcessedRecord(1L, Map.of("id", "1"), "1.0.0", Instant.now())),
            List.of()
        );

        DataTransformer transformer = mock(DataTransformer.class);
        when(transformer.getPipelineName()).thenReturn(pipeline.getName().toUpperCase());
        when(transformer.transform(rawRecords, pipeline, execution)).thenReturn(expected);

        TransformDataUseCase useCase = new TransformDataUseCase(List.of(transformer));

        assertThat(useCase.execute(rawRecords, pipeline, execution)).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenNoTransformerRegisteredForPipeline() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        var rawRecords = List.of(SampleDataFactory.aRawRecord());

        DataTransformer transformer = mock(DataTransformer.class);
        when(transformer.getPipelineName()).thenReturn("other-pipeline");

        TransformDataUseCase useCase = new TransformDataUseCase(List.of(transformer));

        assertThatThrownBy(() -> useCase.execute(rawRecords, pipeline, execution))
            .isInstanceOf(TransformationException.class)
            .hasMessageContaining("No transformer registered");
    }
}
