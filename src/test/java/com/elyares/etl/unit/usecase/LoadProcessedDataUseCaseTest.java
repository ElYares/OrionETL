package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.loading.LoadProcessedDataUseCase;
import com.elyares.etl.domain.contract.DataLoader;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoadProcessedDataUseCaseTest {

    @Test
    void shouldDelegateLoadToDataLoader() {
        var pipeline = SampleDataFactory.aPipeline();
        var execution = new PipelineExecution(
            null,
            pipeline.getId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        var records = List.of(new ProcessedRecord(1L, Map.of("id", "1"), "1.0.0", Instant.now()));
        var expected = LoadResult.success(1, 0, 0);

        DataLoader dataLoader = mock(DataLoader.class);
        when(dataLoader.load(records, pipeline.getTargetConfig(), execution)).thenReturn(expected);

        LoadProcessedDataUseCase useCase = new LoadProcessedDataUseCase(dataLoader);

        assertThat(useCase.execute(records, pipeline, execution)).isEqualTo(expected);
    }
}
