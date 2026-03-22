package com.elyares.etl.domain.rules;

import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.shared.exception.EtlException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Regla de dominio que valida si la ejecución de un pipeline está permitida
 * en el momento actual según su configuración de schedule.
 *
 * <p>Si el schedule está deshabilitado ({@link ScheduleConfig#isEnabled()} es
 * {@code false}), la regla siempre permite la ejecución — se asume que el pipeline
 * se dispara manualmente sin restricción horaria.</p>
 *
 * <p>En la Fase 2, la validación de ventanas horarias específicas se implementa
 * de forma básica. La integración completa con expresiones cron se realiza en
 * fases posteriores con el scheduler.</p>
 */
public class AllowedExecutionWindowRule {

    /**
     * Evalúa si la ejecución está permitida en el instante indicado.
     *
     * <p>Si el schedule está deshabilitado, siempre permite. Si está habilitado
     * pero no tiene ventanas configuradas, también permite (sin restricción).
     * La validación de ventanas específicas es extensible en fases posteriores.</p>
     *
     * @param scheduleConfig configuración de schedule del pipeline
     * @param now            instante actual a evaluar
     * @throws EtlException si la ejecución cae fuera de la ventana permitida
     */
    public void evaluate(ScheduleConfig scheduleConfig, Instant now) {
        if (!scheduleConfig.isEnabled()) {
            return; // Sin schedule activo: siempre permitido
        }
        if (isAllowed(scheduleConfig, now)) {
            return;
        }
        throw new EtlException(
            "ETL_EXEC_WINDOW_BLOCKED",
            "Execution is outside of allowed window for timezone: " + scheduleConfig.getTimezone()
        );
    }

    /**
     * Comprueba de forma no destructiva si la ejecución está permitida.
     *
     * @param scheduleConfig configuración de schedule del pipeline
     * @param now            instante actual a evaluar
     * @return {@code true} si la ejecución está permitida en este momento
     */
    public boolean isAllowed(ScheduleConfig scheduleConfig, Instant now) {
        ZoneId zoneId = ZoneId.of(scheduleConfig.getTimezone());
        ZonedDateTime zonedNow = now.atZone(zoneId);
        return scheduleConfig.isWithinAllowedWindow(zonedNow);
    }
}
