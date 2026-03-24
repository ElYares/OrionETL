package com.elyares.etl.unit.validator;

import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.validator.BusinessValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessValidatorTest {

    private final BusinessValidator businessValidator = new BusinessValidator();

    @Test
    void shouldRejectNegativeAmountAndMissingCatalogValue() {
        ValidationConfig config = new ValidationConfig(
            List.of("transaction_id", "product_id", "amount"),
            Map.of("amount", "DECIMAL"),
            List.of("transaction_id"),
            Map.of(),
            "yyyy-MM-dd",
            true,
            List.of("amount"),
            false,
            Map.of(),
            List.of(),
            Map.of("product_id", Set.of("P-1", "P-2")),
            Map.of("product_id", Set.of("P-1")),
            false,
            ErrorThreshold.of(5.0),
            true
        );

        var result = businessValidator.validate(List.of(
            SampleDataFactory.aRawRecord(1L, Map.of("transaction_id", "TX-1", "product_id", "P-9", "amount", "-10.00"))
        ), config);

        assertThat(result.getInvalidCount()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("NON_NEGATIVE_AMOUNT"));
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("CATALOG_LOOKUP"));
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("ACTIVE_REFERENCE"));
    }

    @Test
    void shouldRejectSubsequentDuplicateBusinessKey() {
        ValidationConfig config = new ValidationConfig(
            List.of("transaction_id"),
            Map.of(),
            List.of("transaction_id"),
            Map.of(),
            null,
            true,
            List.of(),
            false,
            Map.of("discount_rate", new ValidationConfig.RangeRule(BigDecimal.ZERO, BigDecimal.valueOf(100), true)),
            List.of(),
            Map.of(),
            Map.of(),
            false,
            ErrorThreshold.of(5.0),
            true
        );

        var result = businessValidator.validate(List.of(
            SampleDataFactory.aRawRecord(1L, Map.of("transaction_id", "TX-1", "discount_rate", "10")),
            SampleDataFactory.aRawRecord(2L, Map.of("transaction_id", "TX-1", "discount_rate", "20"))
        ), config);

        assertThat(result.getValidRecords()).hasSize(1);
        assertThat(result.getRejectedRecords()).hasSize(1);
        assertThat(result.getErrors()).anyMatch(error -> error.rule().equals("UNIQUE_BUSINESS_KEY"));
    }
}
