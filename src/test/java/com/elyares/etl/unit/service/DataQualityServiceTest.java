package com.elyares.etl.unit.service;

import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.service.DataQualityService;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityServiceTest {

    @Test
    void shouldEvaluateQualityAndAbortWhenBreached() {
        DataQualityService service = new DataQualityService();
        ValidationConfig config = new ValidationConfig(List.of(), Map.of(), List.of(), ErrorThreshold.of(5.0), true);

        DataQualityReport report = service.evaluateQuality(100, 10, ErrorThreshold.of(5.0));

        assertThat(report.thresholdBreached()).isTrue();
        assertThat(service.isAbortRequired(report, config)).isTrue();
    }

    @Test
    void shouldNotAbortWhenWithinThreshold() {
        DataQualityService service = new DataQualityService();
        ValidationConfig config = new ValidationConfig(List.of(), Map.of(), List.of(), ErrorThreshold.of(5.0), true);

        DataQualityReport report = service.evaluateQuality(100, 5, ErrorThreshold.of(5.0));

        assertThat(report.thresholdBreached()).isFalse();
        assertThat(service.isAbortRequired(report, config)).isFalse();
    }
}
