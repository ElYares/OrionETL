package com.elyares.etl.domain.model.execution;

import com.elyares.etl.domain.valueobject.ExecutionId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Record de dominio que representa una métrica cuantitativa registrada durante
 * la ejecución de un pipeline ETL.
 *
 * <p>Cada {@code ExecutionMetric} asocia un nombre de métrica con un valor numérico
 * de precisión arbitraria ({@link BigDecimal}) y la marca de tiempo exacta en que
 * fue registrada. Ejemplos de métricas habituales son el número de registros leídos,
 * el tiempo de procesamiento por fase o el porcentaje de registros rechazados.</p>
 *
 * <p>Al ser un {@code record} de Java, las instancias son implícitamente inmutables.
 * El constructor compacto aplica validaciones y asigna valores por defecto:
 * si {@code id} es {@code null} se genera un {@link UUID} aleatorio;
 * si {@code recordedAt} es {@code null} se usa {@link Instant#now()}.</p>
 *
 * <p>Se ofrecen dos métodos de fábrica estáticos {@link #of(ExecutionId, String, long)}
 * y {@link #of(ExecutionId, String, double)} para construir métricas de forma conveniente
 * a partir de valores primitivos.</p>
 *
 * @param id           identificador único de la métrica
 * @param executionId  identificador de la ejecución a la que pertenece la métrica
 * @param metricName   nombre descriptivo de la métrica (p. ej. {@code "records_read"})
 * @param metricValue  valor numérico de la métrica con precisión arbitraria
 * @param recordedAt   marca de tiempo en que se registró la métrica
 */
public record ExecutionMetric(
    UUID id,
    ExecutionId executionId,
    String metricName,
    BigDecimal metricValue,
    Instant recordedAt
) {
    /**
     * Constructor compacto que valida los campos obligatorios y aplica valores por defecto
     * para los opcionales.
     *
     * @throws NullPointerException si {@code executionId}, {@code metricName}
     *                              o {@code metricValue} son {@code null}
     */
    public ExecutionMetric {
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(metricName);
        Objects.requireNonNull(metricValue);
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = Instant.now();
    }

    /**
     * Crea una nueva {@code ExecutionMetric} a partir de un valor entero largo.
     *
     * <p>El identificador y la marca de tiempo se generan automáticamente.</p>
     *
     * @param executionId identificador de la ejecución; no puede ser {@code null}
     * @param metricName  nombre de la métrica; no puede ser {@code null}
     * @param value       valor entero de la métrica
     * @return nueva instancia de {@code ExecutionMetric} con el valor convertido a {@link BigDecimal}
     */
    public static ExecutionMetric of(ExecutionId executionId, String metricName, long value) {
        return new ExecutionMetric(UUID.randomUUID(), executionId, metricName,
                                   BigDecimal.valueOf(value), Instant.now());
    }

    /**
     * Crea una nueva {@code ExecutionMetric} a partir de un valor de punto flotante.
     *
     * <p>El identificador y la marca de tiempo se generan automáticamente.</p>
     *
     * @param executionId identificador de la ejecución; no puede ser {@code null}
     * @param metricName  nombre de la métrica; no puede ser {@code null}
     * @param value       valor decimal de la métrica
     * @return nueva instancia de {@code ExecutionMetric} con el valor convertido a {@link BigDecimal}
     */
    public static ExecutionMetric of(ExecutionId executionId, String metricName, double value) {
        return new ExecutionMetric(UUID.randomUUID(), executionId, metricName,
                                   BigDecimal.valueOf(value), Instant.now());
    }
}
