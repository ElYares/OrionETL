package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.ExecutionStatus;
import java.time.Instant;

/**
 * DTO de resumen rápido del estado de una ejecución ETL.
 *
 * <p>Diseñado específicamente para consultas de polling de alta frecuencia: contiene
 * únicamente los campos necesarios para determinar si una ejecución ha terminado y
 * cuál fue su resultado, sin incluir el detalle completo de pasos que provee
 * {@link PipelineExecutionDto}. Esto reduce el tamaño de la respuesta y el coste de
 * serialización en escenarios donde se consulta repetidamente hasta alcanzar un estado
 * terminal.</p>
 *
 * <p>Un estado se considera terminal cuando {@link ExecutionStatus#isTerminal()} devuelve
 * {@code true}, lo que puede verificarse convenientemente a través del método
 * {@link #isTerminal()}.</p>
 *
 * @param executionId   identificador público UUID de la ejecución; corresponde al valor
 *                      interno de {@code ExecutionId}
 * @param status        estado actual de la ejecución
 *                      ({@code PENDING}, {@code RUNNING}, {@code COMPLETED},
 *                      {@code FAILED}, {@code CANCELLED})
 * @param totalRead     número total de registros leídos de la fuente hasta el momento
 * @param totalLoaded   número total de registros cargados con éxito en el destino
 *                      hasta el momento
 * @param totalRejected número total de registros rechazados en cualquier paso hasta
 *                      el momento
 * @param finishedAt    marca de tiempo UTC en que finalizó la ejecución;
 *                      {@code null} si la ejecución aún está en curso
 *
 * @see ExecutionStatus
 * @see PipelineExecutionDto
 */
public record ExecutionStatusDto(
    String executionId,
    ExecutionStatus status,
    long totalRead,
    long totalLoaded,
    long totalRejected,
    Instant finishedAt
) {

    /**
     * Indica si la ejecución ha alcanzado un estado terminal.
     *
     * <p>Delega en {@link ExecutionStatus#isTerminal()} para determinar si el estado actual
     * es definitivo (por ejemplo {@code COMPLETED}, {@code FAILED} o {@code CANCELLED}).
     * Un estado terminal implica que la ejecución ya no cambiará y que {@code finishedAt}
     * no es {@code null}.</p>
     *
     * @return {@code true} si {@code status} no es {@code null} y representa un estado
     *         terminal; {@code false} en caso contrario
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }
}
