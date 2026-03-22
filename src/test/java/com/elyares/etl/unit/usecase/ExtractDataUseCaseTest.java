package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.extraction.ExtractDataUseCase;
import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.shared.exception.ExtractionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExtractDataUseCaseTest {

    @Test
    void shouldUseFirstSupportingExtractor() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        ExtractionResult expected = ExtractionResult.success(List.of(SampleDataFactory.aRawRecord()), "src");

        DataExtractor unsupported = mock(DataExtractor.class);
        when(unsupported.supports(pipeline.getSourceConfig().getType())).thenReturn(false);

        DataExtractor supported = mock(DataExtractor.class);
        when(supported.supports(pipeline.getSourceConfig().getType())).thenReturn(true);
        when(supported.extract(pipeline.getSourceConfig(), execution)).thenReturn(expected);

        ExtractDataUseCase useCase = new ExtractDataUseCase(List.of(unsupported, supported));

        assertThat(useCase.execute(pipeline, execution)).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenNoExtractorSupportsSourceType() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );

        DataExtractor unsupported = mock(DataExtractor.class);
        when(unsupported.supports(pipeline.getSourceConfig().getType())).thenReturn(false);

        ExtractDataUseCase useCase = new ExtractDataUseCase(List.of(unsupported));

        assertThatThrownBy(() -> useCase.execute(pipeline, execution))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("No extractor registered");
    }
}
