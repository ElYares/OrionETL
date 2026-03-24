package com.elyares.etl.unit.rules;

import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.shared.exception.RetryExhaustedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryEligibilityRuleTest {

    @Test
    void shouldThrowWhenRetriesExhausted() {
        RetryEligibilityRule rule = new RetryEligibilityRule();
        RetryPolicy retryPolicy = new RetryPolicy(2, 1000, List.of(ErrorType.TECHNICAL));

        assertThatThrownBy(() -> rule.evaluate(2, ErrorType.TECHNICAL, retryPolicy))
            .isInstanceOf(RetryExhaustedException.class);
    }

    @Test
    void shouldRejectNonRetryableErrorType() {
        RetryEligibilityRule rule = new RetryEligibilityRule();
        RetryPolicy retryPolicy = new RetryPolicy(3, 1000, List.of(ErrorType.TECHNICAL));

        assertThatThrownBy(() -> rule.evaluate(1, ErrorType.DATA_QUALITY, retryPolicy))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBeEligibleForConfiguredErrorType() {
        RetryEligibilityRule rule = new RetryEligibilityRule();
        RetryPolicy retryPolicy = new RetryPolicy(3, 1000, List.of(ErrorType.TECHNICAL));

        assertThat(rule.isEligible(1, ErrorType.TECHNICAL, retryPolicy)).isTrue();
    }

    @Test
    void shouldNotBeEligibleWhenRetriesAlreadyExhausted() {
        RetryEligibilityRule rule = new RetryEligibilityRule();
        RetryPolicy retryPolicy = new RetryPolicy(2, 1000, List.of(ErrorType.TECHNICAL));

        assertThat(rule.isEligible(2, ErrorType.TECHNICAL, retryPolicy)).isFalse();
    }

    @Test
    void shouldNotBeEligibleForDataQualityErrors() {
        RetryEligibilityRule rule = new RetryEligibilityRule();
        RetryPolicy retryPolicy = new RetryPolicy(3, 1000, List.of(ErrorType.TECHNICAL, ErrorType.EXTERNAL_INTEGRATION));

        assertThat(rule.isEligible(1, ErrorType.DATA_QUALITY, retryPolicy)).isFalse();
    }
}
