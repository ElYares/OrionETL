package com.elyares.etl.unit.transformer;

import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonTransformerTest {

    @Test
    void shouldNormalizeSnakeCaseTrimConvertCurrencyAndRoundUsingBigDecimal() {
        Pipeline pipeline = SampleDataFactory.aPipeline();
        TransformationConfig transformationConfig = new TransformationConfig(
            List.of("sale_date"),
            null,
            "UTC",
            "currency",
            "USD",
            "USD",
            Map.of("EUR", new BigDecimal("1.10"), "USD", BigDecimal.ONE),
            List.of("amount"),
            List.of("", "NULL"),
            Map.of("status", Map.of("ACTV", "ACTIVE")),
            Map.of("status", "REJECT"),
            Map.of(),
            Map.of("total_amount", "amount + fee"),
            2,
            RoundingMode.HALF_UP,
            List.of("amount", "total_amount")
        );
        Pipeline configuredPipeline = new Pipeline(
            pipeline.getId(),
            pipeline.getName(),
            pipeline.getVersion(),
            pipeline.getDescription(),
            pipeline.getStatus(),
            pipeline.getSourceConfig(),
            pipeline.getTargetConfig(),
            transformationConfig,
            pipeline.getValidationConfig(),
            pipeline.getScheduleConfig(),
            pipeline.getRetryPolicy(),
            pipeline.getCreatedAt(),
            pipeline.getUpdatedAt()
        );

        CommonTransformer transformer = new CommonTransformer();
        var records = List.of(SampleDataFactory.aRawRecord(1L, Map.of(
            "Sale Date", "2026-01-15T10:00:00",
            "Amount", "10.125",
            "Fee", "2.005",
            "Currency", "EUR",
            "Status", " ACTV "
        )));

        var result = transformer.transform(records, configuredPipeline);

        assertThat(result.rejectedRecords()).isEmpty();
        assertThat(result.processedRecords()).hasSize(1);
        Map<String, Object> data = result.processedRecords().getFirst().getData();
        assertThat(data).containsEntry("status", "ACTIVE");
        assertThat(data).containsEntry("currency_original", "EUR");
        assertThat(data).containsEntry("currency", "USD");
        assertThat((BigDecimal) data.get("amount")).isEqualByComparingTo(new BigDecimal("11.14"));
        assertThat((BigDecimal) data.get("total_amount")).isEqualByComparingTo(new BigDecimal("13.14"));
    }
}
