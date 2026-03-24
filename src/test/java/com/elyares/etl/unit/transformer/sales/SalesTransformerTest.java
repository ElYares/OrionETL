package com.elyares.etl.unit.transformer.sales;

import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import com.elyares.etl.infrastructure.transformer.sales.SalesTransformer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesTransformerTest {

    private final SalesTransformer salesTransformer = new SalesTransformer(new CommonTransformer());

    @Test
    void shouldCalculateSubtotalTaxAndTotalWithDiscount() {
        TransformationResult result = salesTransformer.transform(
            List.of(rawRecord("2", "100.00", "USD", "2", "10")),
            salesPipeline(),
            execution()
        );

        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords()).hasSize(1);
        Map<String, Object> data = result.getProcessedRecords().getFirst().getData();

        assertThat(data.get("channel")).isEqualTo("IN_STORE");
        assertThat(data.get("quantity")).isEqualTo(2);
        assertThat((BigDecimal) data.get("discount_rate")).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat((BigDecimal) data.get("subtotal")).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat((BigDecimal) data.get("tax_amount")).isEqualByComparingTo(new BigDecimal("34.20"));
        assertThat((BigDecimal) data.get("total_amount")).isEqualByComparingTo(new BigDecimal("214.20"));
    }

    @Test
    void shouldMapAllValidChannelCodes() {
        assertChannel("1", "ONLINE");
        assertChannel("ONLINE", "ONLINE");
        assertChannel("2", "IN_STORE");
        assertChannel("STORE", "IN_STORE");
        assertChannel("3", "PHONE");
        assertChannel("PHONE", "PHONE");
        assertChannel("4", "PARTNER");
        assertChannel("PARTNER", "PARTNER");
        assertChannel("5", "MARKETPLACE");
        assertChannel("MARKETPLACE", "MARKETPLACE");
    }

    @Test
    void shouldRejectUnmappedChannelCode() {
        TransformationResult result = salesTransformer.transform(
            List.of(rawRecord("999", "50.00", "USD", "1", "0")),
            salesPipeline(),
            execution()
        );

        assertThat(result.getProcessedRecords()).isEmpty();
        assertThat(result.getRejectedRecords()).hasSize(1);
        assertThat(result.getRejectedRecords().getFirst().getRejectionReason())
            .contains("Missing channel mapping");
    }

    private void assertChannel(String input, String expected) {
        TransformationResult result = salesTransformer.transform(
            List.of(rawRecord(input, "10.00", "USD", "1", "0")),
            salesPipeline(),
            execution()
        );
        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords().getFirst().getData().get("channel")).isEqualTo(expected);
    }

    private RawRecord rawRecord(String channel, String amount, String currency, String quantity, String discountRate) {
        return new RawRecord(
            1L,
            Map.of(
                "transaction_id", "TX-1",
                "customer_id", "C-100",
                "product_id", "P-100",
                "amount", amount,
                "currency", currency,
                "sale_date", "2026-03-20",
                "salesperson_id", "S-001",
                "channel", channel,
                "product_quantity", quantity,
                "discount_rate", discountRate
            ),
            "sales-unit.csv",
            Instant.now()
        );
    }

    private Pipeline salesPipeline() {
        return new Pipeline(
            PipelineId.of(UUID.fromString("9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001")),
            "sales-daily",
            "1.0.0",
            "Sales pipeline",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.CSV, "/tmp/sales.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "sales_transactions_staging", "sales_transactions", LoadStrategy.UPSERT, List.of("transaction_id"), 500),
            new TransformationConfig(
                List.of("sale_date"),
                "yyyy-MM-dd",
                "UTC",
                "currency",
                "USD",
                "USD",
                Map.of(
                    "USD", new BigDecimal("1.00"),
                    "EUR", new BigDecimal("1.10"),
                    "COP", new BigDecimal("0.00025")
                ),
                List.of("amount"),
                List.of("", "NULL", "N/A", "-"),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                2,
                java.math.RoundingMode.HALF_UP,
                List.of("amount")
            ),
            new ValidationConfig(List.of("transaction_id"), Map.of(), List.of("transaction_id"), ErrorThreshold.of(5.0), true),
            ScheduleConfig.disabled(),
            RetryPolicy.noRetry(),
            Instant.now(),
            Instant.now()
        );
    }

    private PipelineExecution execution() {
        return new PipelineExecution(
            UUID.randomUUID(),
            PipelineId.of(UUID.fromString("9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001")),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "unit-test"
        );
    }
}
