package com.elyares.etl.unit.rules;

import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.rules.ErrorThresholdRule;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorThresholdRuleTest {

    @Test
    void shouldAbortAtThresholdPlus001() {
        ErrorThresholdRule rule = new ErrorThresholdRule();
        ValidationConfig config = new ValidationConfig(List.of(), Map.of(), List.of(), ErrorThreshold.of(5.0), true);

        DataQualityReport report = DataQualityReport.of(10000, 501, 5.0);

        assertThat(rule.shouldAbort(report, config)).isTrue();
    }

    @Test
    void shouldAllowAtExactThreshold() {
        ErrorThresholdRule rule = new ErrorThresholdRule();
        ValidationConfig config = new ValidationConfig(List.of(), Map.of(), List.of(), ErrorThreshold.of(5.0), true);

        DataQualityReport report = DataQualityReport.of(10000, 500, 5.0);

        assertThat(rule.shouldAbort(report, config)).isFalse();
    }
}
