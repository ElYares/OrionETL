package com.elyares.etl.application.orchestrator;

import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationContextTest {

    @Test
    void shouldUseEmptyCollectionsWhenNullIsProvided() {
        OrchestrationContext context = new OrchestrationContext();

        context.setRawRecords(null);
        context.setProcessedRecords(null);

        assertThat(context.getRawRecords()).isEmpty();
        assertThat(context.getProcessedRecords()).isEmpty();
    }

    @Test
    void shouldTrackCountersAndCollections() {
        OrchestrationContext context = new OrchestrationContext();
        var rawRecords = List.of(SampleDataFactory.aRawRecord());
        var processedRecords = List.of(new ProcessedRecord(1L, Map.of("id", "1"), "1.0.0", Instant.now()));
        RejectedRecord rejected = new RejectedRecord(
            SampleDataFactory.aRawRecord(),
            "VALIDATE_SCHEMA",
            "invalid",
            List.of()
        );

        context.setRawRecords(rawRecords);
        context.setProcessedRecords(processedRecords);
        context.getRejectedRecords().add(rejected);
        context.setTotalRead(10);
        context.setTotalTransformed(8);
        context.addRejected(2);
        context.setTotalLoaded(8);

        assertThat(context.getRawRecords()).hasSize(1);
        assertThat(context.getProcessedRecords()).hasSize(1);
        assertThat(context.getRejectedRecords()).containsExactly(rejected);
        assertThat(context.getTotalRead()).isEqualTo(10);
        assertThat(context.getTotalTransformed()).isEqualTo(8);
        assertThat(context.getTotalRejected()).isEqualTo(2);
        assertThat(context.getTotalLoaded()).isEqualTo(8);
    }
}
