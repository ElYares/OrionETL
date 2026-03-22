package com.elyares.etl.unit.rules;

import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.rules.AllowedExecutionWindowRule;
import com.elyares.etl.shared.exception.EtlException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedExecutionWindowRuleTest {

    @Test
    void shouldAllowWhenScheduleDisabled() {
        AllowedExecutionWindowRule rule = new AllowedExecutionWindowRule();

        assertThat(rule.isAllowed(ScheduleConfig.disabled(), Instant.now())).isTrue();
    }

    @Test
    void shouldAllowInsideConfiguredWindow() {
        AllowedExecutionWindowRule rule = new AllowedExecutionWindowRule();

        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("UTC"));
        ScheduleConfig.AllowedWindow window = new ScheduleConfig.AllowedWindow(
            now.toLocalTime().minusMinutes(5),
            now.toLocalTime().plusMinutes(5),
            List.of(now.getDayOfWeek())
        );
        ScheduleConfig config = ScheduleConfig.of("0 * * * *", "UTC", List.of(window));

        assertThat(rule.isAllowed(config, now.toInstant())).isTrue();
    }

    @Test
    void shouldThrowOutsideConfiguredWindow() {
        AllowedExecutionWindowRule rule = new AllowedExecutionWindowRule();

        DayOfWeek day = DayOfWeek.MONDAY;
        ScheduleConfig.AllowedWindow window = new ScheduleConfig.AllowedWindow(
            LocalTime.of(1, 0),
            LocalTime.of(2, 0),
            List.of(day)
        );
        ScheduleConfig config = ScheduleConfig.of("0 * * * *", "UTC", List.of(window));
        Instant now = ZonedDateTime.of(2026, 3, 24, 5, 0, 0, 0, java.time.ZoneId.of("UTC")).toInstant();

        assertThatThrownBy(() -> rule.evaluate(config, now)).isInstanceOf(EtlException.class);
    }
}
