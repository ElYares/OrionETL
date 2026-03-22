package com.elyares.etl.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de una métrica numérica registrada durante una ejecución ETL.
 *
 * <p>Las métricas son valores cuantitativos (conteos, duraciones, tasas, porcentajes)
 * que complementan el estado de la ejecución con información de rendimiento y calidad
 * de datos. A diferencia de los conteos agregados en {@link PipelineExecutionDto}, cada
 * métrica es un punto de dato discreto con nombre, valor y marca de tiempo propios.</p>
 *
 * <p>Convención de nomenclatura para {@code metricName} (separador de puntos,
 * identificadores en minúsculas):</p>
 * <ul>
 *   <li>{@code records.read} — número de registros leídos de la fuente</li>
 *   <li>{@code records.loaded} — número de registros cargados en el destino</li>
 *   <li>{@code duration.ms} — duración total de la ejecución en milisegundos</li>
 *   <li>{@code error.rate.percent} — porcentaje de registros rechazados sobre leídos</li>
 *   <li>{@code throughput.records_per_second} — tasa de procesamiento</li>
 * </ul>
 *
 * <p>El uso de {@link BigDecimal} para {@code metricValue} garantiza precisión arbitraria,
 * lo que resulta esencial para métricas de tipo porcentaje o tasa donde la aritmética
 * de punto flotante introduciría errores de redondeo inaceptables.</p>
 *
 * <p>Instancias de este record son producidas por
 * {@code ExecutionMapper#toMetricDto(ExecutionMetric)}.</p>
 *
 * @param executionId identificador UUID de la ejecución a la que pertenece la métrica;
 *                    corresponde al valor interno de {@code ExecutionId}; nunca {@code null}
 * @param metricName  nombre jerárquico de la métrica siguiendo la convención de puntos
 *                    (por ejemplo {@code "records.read"}, {@code "duration.ms"},
 *                    {@code "error.rate.percent"}); nunca {@code null} ni vacío
 * @param metricValue valor numérico de la métrica con precisión arbitraria;
 *                    nunca {@code null}
 * @param recordedAt  marca de tiempo UTC en que la métrica fue registrada en el sistema;
 *                    nunca {@code null}
 *
 * @see PipelineExecutionDto
 */
public record ExecutionMetricDto(
    String executionId,
    String metricName,
    BigDecimal metricValue,
    Instant recordedAt
) {}
