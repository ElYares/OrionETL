package com.elyares.etl.unit.transformer.customer;

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
import com.elyares.etl.infrastructure.transformer.customer.CustomerTransformer;
import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTransformerTest {

    @Test
    void shouldNormalizeNamesPhoneCountryAndStatus() {
        CustomerTransformer transformer = new CustomerTransformer(new CommonTransformer());
        Pipeline pipeline = samplePipeline();

        var result = transformer.transform(List.of(
            new RawRecord(1L, Map.of(
                "customer_id", "crm-1",
                "document_type", "cc",
                "document_number", "1001",
                "first_name", "mARia  elena",
                "last_name", "o'brien-smith",
                "email", " MARIA@EXAMPLE.COM ",
                "phone", "3001234567",
                "country_code", "COL",
                "registration_date", "2026-03-20",
                "status", "A"
            ), "api", Instant.now())
        ), pipeline, new PipelineExecution(null, pipeline.getId(), ExecutionId.generate(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL, "test"));

        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords()).hasSize(1);
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("first_name", "Maria Elena");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("last_name", "O'Brien-Smith");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("email", "maria@example.com");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("phone", "+573001234567");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("country_code", "CO");
        assertThat(result.getProcessedRecords().get(0).getData()).containsEntry("status", "ACTIVE");
    }

    private Pipeline samplePipeline() {
        return new Pipeline(
            PipelineId.of("7dd99f8b-19de-4730-b012-1d75c1ae6e01"),
            "customer-sync",
            "1.0.0",
            "Customer",
            PipelineStatus.ACTIVE,
            new SourceConfig(SourceType.API, "http://localhost/customers", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "customers_staging", "customers",
                com.elyares.etl.domain.enums.LoadStrategy.UPSERT, List.of("document_type", "document_number"), 200, true,
                com.elyares.etl.domain.enums.RollbackStrategy.DELETE_BY_EXECUTION, true, "status", "CLOSED"),
            new TransformationConfig(List.of("registration_date", "birth_date"), "yyyy-MM-dd", "UTC", "currency", "USD", null,
                Map.of(), List.of(), List.of("", "NULL"), Map.of(), Map.of(), Map.of(), Map.of(), 2, RoundingMode.HALF_UP, List.of()),
            new ValidationConfig(List.of(), Map.of(), List.of("document_type", "document_number"), Map.of(), null, true,
                List.of(), false, Map.of(), List.of("registration_date"), Map.of(), Map.of(), Map.of(), false,
                ErrorThreshold.of(1.0), true),
            new ScheduleConfig(null, "UTC", false, List.of()),
            RetryPolicy.noRetry(),
            Instant.now(),
            Instant.now()
        );
    }
}
