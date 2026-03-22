package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.TriggerType;
import java.util.Map;

/**
 * DTO de entrada para solicitar la ejecución de un pipeline ETL.
 *
 * <p>Es el objeto que llega desde la interfaz (REST, CLI o scheduler) al caso de uso
 * {@code ExecutePipelineUseCase}. Contiene el identificador del pipeline, el tipo de
 * disparador y cualquier parámetro adicional requerido por el pipeline.</p>
 *
 * <p>Los métodos de fábrica estáticos ({@link #manual} y {@link #scheduled}) cubren los
 * escenarios de invocación más comunes y garantizan que el mapa de parámetros nunca sea
 * {@code null}, simplificando la lógica de los consumidores.</p>
 *
 * @param pipelineId  identificador UUID del pipeline a ejecutar, representado como cadena;
 *                    no debe ser {@code null} ni vacío
 * @param triggerType origen de la ejecución: {@code MANUAL}, {@code SCHEDULED},
 *                    {@code RETRY} o {@code CLI}; no debe ser {@code null}
 * @param triggeredBy actor que inició la ejecución (nombre de usuario, identificador del
 *                    scheduler o nombre del proceso del sistema); no debe ser {@code null}
 * @param parameters  parámetros adicionales opcionales específicos del pipeline;
 *                    nunca {@code null}, puede ser un mapa vacío
 *
 * @see TriggerType
 */
public record ExecutionRequestDto(
    String pipelineId,
    TriggerType triggerType,
    String triggeredBy,
    Map<String, String> parameters
) {

    /**
     * Crea una solicitud de ejecución iniciada manualmente por un actor humano.
     *
     * <p>Establece {@code triggerType} en {@link TriggerType#MANUAL} y deja el mapa de
     * parámetros vacío ({@link Map#of()}). Utilizar cuando la invocación proviene de una
     * petición REST o de la CLI por parte de un usuario identificado.</p>
     *
     * @param pipelineId  identificador UUID del pipeline a ejecutar; no debe ser {@code null}
     * @param triggeredBy nombre o identificador del usuario que solicita la ejecución;
     *                    no debe ser {@code null}
     * @return nueva instancia de {@code ExecutionRequestDto} con {@code triggerType = MANUAL}
     *         y parámetros vacíos
     */
    public static ExecutionRequestDto manual(String pipelineId, String triggeredBy) {
        return new ExecutionRequestDto(pipelineId, TriggerType.MANUAL, triggeredBy, Map.of());
    }

    /**
     * Crea una solicitud de ejecución iniciada automáticamente por el scheduler del sistema.
     *
     * <p>Establece {@code triggerType} en {@link TriggerType#SCHEDULED}, asigna
     * {@code "scheduler"} como actor y deja el mapa de parámetros vacío. Utilizar cuando
     * la invocación proviene de un job programado (cron, Quartz, Spring Scheduler, etc.).</p>
     *
     * @param pipelineId identificador UUID del pipeline a ejecutar; no debe ser {@code null}
     * @return nueva instancia de {@code ExecutionRequestDto} con {@code triggerType = SCHEDULED},
     *         {@code triggeredBy = "scheduler"} y parámetros vacíos
     */
    public static ExecutionRequestDto scheduled(String pipelineId) {
        return new ExecutionRequestDto(pipelineId, TriggerType.SCHEDULED, "scheduler", Map.of());
    }
}
