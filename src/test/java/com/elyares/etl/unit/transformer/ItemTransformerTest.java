package com.elyares.etl.unit.transformer;

import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import com.elyares.etl.infrastructure.transformer.item.ItemTransformer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTransformerTest {

    @Test
    void shouldNormalizeItemFieldsAndScalePrice() {
        ItemTransformer transformer = new ItemTransformer(new CommonTransformer());
        Pipeline pipeline = itemPipeline();
        RawRecord record = SampleDataFactory.aRawRecord(2L, Map.of(
            "item_key", "i00001",
            "item_name", "  Coke Classic 12 oz cans  ",
            "description", "  a. Beverage - Soda  ",
            "unit_price", "11.5",
            "manufacturer_country", "united states",
            "supplier_name", "  acme imports  ",
            "unit", "CANS"
        ));

        var result = transformer.transform(List.of(record), pipeline, null);

        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords()).hasSize(1);
        assertThat(result.getProcessedRecords().getFirst().getData())
            .containsEntry("item_key", "I00001")
            .containsEntry("item_name", "Coke Classic 12 oz cans")
            .containsEntry("manufacturer_country", "United States")
            .containsEntry("supplier_name", "Acme Imports")
            .containsEntry("unit", "cans");
        assertThat((BigDecimal) result.getProcessedRecords().getFirst().getData().get("unit_price"))
            .isEqualByComparingTo(new BigDecimal("11.50"));
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        ItemTransformer transformer = new ItemTransformer(new CommonTransformer());
        Pipeline pipeline = itemPipeline();
        RawRecord record = SampleDataFactory.aRawRecord(3L, Map.of(
            "item_key", "I00002",
            "item_name", "Diet Coke",
            "description", "a. Beverage - Soda",
            "unit_price", "-1.00",
            "manufacturer_country", "mexico",
            "supplier_name", "test supplier",
            "unit", "cans"
        ));

        var result = transformer.transform(List.of(record), pipeline, null);

        assertThat(result.getProcessedRecords()).isEmpty();
        assertThat(result.getRejectedRecords()).hasSize(1);
        assertThat(result.getRejectedRecords().getFirst().getRejectionReason())
            .contains("unit_price must be non-negative");
    }

    private Pipeline itemPipeline() {
        return new Pipeline(
            PipelineId.generate(),
            "item-sync",
            "1.0.0",
            "Item pipeline test",
            PipelineStatus.ACTIVE,
            new SourceConfig(com.elyares.etl.domain.enums.SourceType.CSV, "/tmp/items.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "etl_items_staging", "etl_items", LoadStrategy.UPSERT, List.of("item_key"), 100),
            new TransformationConfig(
                List.of(),
                null,
                "UTC",
                "currency",
                "USD",
                null,
                Map.of(),
                List.of(),
                List.of("", "NULL", "N/A", "-"),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                2,
                java.math.RoundingMode.HALF_UP,
                List.of("unit_price")
            ),
            new ValidationConfig(
                List.of("item_key", "item_name", "unit_price"),
                Map.of("unit_price", "DECIMAL"),
                List.of("item_key"),
                Map.of(),
                null,
                true,
                List.of("unit_price"),
                false,
                Map.of(),
                List.of(),
                Map.of(),
                Map.of(),
                false,
                ErrorThreshold.of(2.0),
                true
            ),
            ScheduleConfig.disabled(),
            RetryPolicy.noRetry(),
            Instant.now(),
            Instant.now()
        );
    }
}
