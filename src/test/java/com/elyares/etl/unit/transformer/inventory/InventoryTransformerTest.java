package com.elyares.etl.unit.transformer.inventory;

import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import com.elyares.etl.infrastructure.transformer.inventory.InventoryTransformer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTransformerTest {

    @Test
    void shouldNormalizeSkuAndConsolidateBySkuAndWarehouse() {
        InventoryTransformer transformer = new InventoryTransformer(new CommonTransformer());
        Pipeline pipeline = samplePipeline();

        var result = transformer.transform(List.of(
            new RawRecord(1L, Map.of(
                "sku", "abc 00123",
                "warehouse_id", "WH001",
                "quantity_on_hand", 5,
                "quantity_reserved", 1,
                "unit_cost", "10.50",
                "last_updated", "2026-03-23"
            ), "inventory.xlsx", Instant.now()),
            new RawRecord(2L, Map.of(
                "sku", "ABC-123",
                "warehouse_id", "CENTRAL",
                "quantity_on_hand", 7,
                "quantity_reserved", 2,
                "unit_cost", "12.00",
                "last_updated", "2026-03-24"
            ), "inventory.xlsx", Instant.now())
        ), pipeline, new PipelineExecution(null, pipeline.getId(), ExecutionId.generate(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL, "test"));

        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords()).hasSize(1);
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("sku", "ABC-123");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("warehouse_id", "W-001");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("quantity_on_hand", 12L);
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("quantity_reserved", 3L);
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("unit_cost", new BigDecimal("12.00"));
    }

    private Pipeline samplePipeline() {
        return new Pipeline(
            PipelineId.of("6a81ef7f-6c11-4b22-b9a8-7b8fa4d2a101"),
            "inventory-sync",
            "1.0.0",
            "Inventory",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.EXCEL, "/tmp/inventory.xlsx", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "inventory_levels_staging", "inventory_levels",
                com.elyares.etl.domain.enums.LoadStrategy.UPSERT, List.of("sku", "warehouse_id"), 500, true,
                com.elyares.etl.domain.enums.RollbackStrategy.DELETE_BY_EXECUTION, false, "status", "CLOSED"),
            new TransformationConfig(List.of("last_updated"), "yyyy-MM-dd", "UTC", "cost_currency", "USD", "USD",
                Map.of("USD", BigDecimal.ONE), List.of("unit_cost"), List.of("", "NULL"), Map.of(), Map.of(),
                Map.of(), Map.of(), 2, RoundingMode.HALF_UP, List.of("unit_cost")),
            new ValidationConfig(List.of(), Map.of(), List.of("sku", "warehouse_id"), Map.of(), null, true,
                List.of("unit_cost"), false, Map.of(), List.of(), Map.of(), Map.of(), Map.of(), false,
                ErrorThreshold.of(2.0), true),
            new ScheduleConfig(null, "UTC", false, List.of()),
            RetryPolicy.noRetry(),
            Instant.now(),
            Instant.now()
        );
    }
}
