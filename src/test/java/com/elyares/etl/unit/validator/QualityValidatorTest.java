package com.elyares.etl.unit.validator;

import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.validator.QualityValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QualityValidatorTest {

    private final QualityValidator qualityValidator = new QualityValidator();

    @Test
    void shouldRecommendAbortWhenErrorRateExceedsThreshold() {
        ValidationConfig config = new ValidationConfig(
            List.of("id"),
            Map.of(),
            List.of("id"),
            ErrorThreshold.of(5.0),
            true
        );
        var rawRecords = List.of(
            SampleDataFactory.aRawRecord(1L, Map.of("id", "1")),
            SampleDataFactory.aRawRecord(2L, Map.of("id", "2")),
            SampleDataFactory.aRawRecord(3L, Map.of("id", "3")),
            SampleDataFactory.aRawRecord(4L, Map.of("id", "4")),
            SampleDataFactory.aRawRecord(5L, Map.of("id", "5")),
            SampleDataFactory.aRawRecord(6L, Map.of("id", "6")),
            SampleDataFactory.aRawRecord(7L, Map.of("id", "7")),
            SampleDataFactory.aRawRecord(8L, Map.of("id", "8")),
            SampleDataFactory.aRawRecord(9L, Map.of("id", "9")),
            SampleDataFactory.aRawRecord(10L, Map.of("id", "10"))
        );
        ValidationResult base = ValidationResult.batch(
            rawRecords.subList(0, 4),
            List.of(
                new RejectedRecord(rawRecords.get(4), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "5", "RULE", "x"))),
                new RejectedRecord(rawRecords.get(5), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "6", "RULE", "x"))),
                new RejectedRecord(rawRecords.get(6), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "7", "RULE", "x"))),
                new RejectedRecord(rawRecords.get(7), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "8", "RULE", "x"))),
                new RejectedRecord(rawRecords.get(8), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "9", "RULE", "x"))),
                new RejectedRecord(rawRecords.get(9), "VALIDATE_BUSINESS", "failed", List.of(ValidationError.critical("id", "10", "RULE", "x")))
            ),
            List.of(ValidationError.critical("id", "10", "RULE", "x")),
            null
        );

        ValidationResult enriched = qualityValidator.enrich(rawRecords, base, config);

        assertThat(enriched.getDataQualityReport().errorRatePercent()).isEqualTo(60.0);
        assertThat(enriched.getDataQualityReport().thresholdBreached()).isTrue();
    }
}
