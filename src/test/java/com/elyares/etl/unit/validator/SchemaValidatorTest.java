package com.elyares.etl.unit.validator;

import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.validator.SchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidatorTest {

    private final SchemaValidator schemaValidator = new SchemaValidator();

    @Test
    void shouldRejectAllRecordsWhenMandatoryColumnIsMissingFromDataset() {
        ValidationConfig config = new ValidationConfig(
            List.of("transaction_id", "amount"),
            Map.of("amount", "DECIMAL"),
            List.of("transaction_id"),
            null,
            true
        );

        var result = schemaValidator.validate(List.of(
            SampleDataFactory.aRawRecord(1L, Map.of("transaction_id", "A-1"))
        ), config);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getRejectedRecords()).hasSize(1);
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("MANDATORY_COLUMN_MISSING"));
    }

    @Test
    void shouldRejectRecordWithInvalidNumericValue() {
        ValidationConfig config = new ValidationConfig(
            List.of("transaction_id", "amount"),
            Map.of("amount", "DECIMAL"),
            List.of("transaction_id"),
            null,
            true
        );

        var result = schemaValidator.validate(List.of(
            SampleDataFactory.aRawRecord(1L, Map.of("transaction_id", "A-1", "amount", "abc"))
        ), config);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("TYPE_COMPATIBILITY"));
    }
}
