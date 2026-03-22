package com.elyares.etl.application.dto;

import java.time.Instant;
import java.util.Map;

/**
 * DTO de una entrada del log de auditoría ETL.
 *
 * <p>Representa la vista externa de un {@code AuditRecord} del dominio. Cada entrada
 * corresponde a un evento significativo registrado de forma inmutable en el sistema de
 * auditoría: inicio y fin de ejecuciones, creación y modificación de pipelines,
 * intervenciones manuales, etc.</p>
 *
 * <p>El campo {@code action} utiliza valores de enumeración canónicos como cadenas de
 * texto (por ejemplo {@code "EXECUTION_STARTED"}, {@code "EXECUTION_COMPLETED"},
 * {@code "EXECUTION_FAILED"}, {@code "PIPELINE_CREATED"}), lo que facilita la
 * integración con sistemas externos sin dependencia del modelo de dominio.</p>
 *
 * <p>El mapa {@code details} es un contenedor extensible de contexto adicional cuya
 * estructura varía según la acción auditada. Por ejemplo, para
 * {@code "EXECUTION_COMPLETED"} puede contener {@code {"totalLoaded": 5000,
 * "durationMs": 1234}}.</p>
 *
 * <p>Instancias de este record son producidas por
 * {@code ExecutionMapper#toAuditDto(AuditRecord)}.</p>
 *
 * @param id          identificador único del registro de auditoría (UUID como cadena);
 *                    nunca {@code null}
 * @param executionId identificador UUID de la ejecución relacionada con el evento;
 *                    puede ser {@code null} si el evento no está asociado a una ejecución
 *                    específica (por ejemplo, creación de un pipeline)
 * @param pipelineId  identificador UUID del pipeline relacionado con el evento;
 *                    nunca {@code null}
 * @param action      nombre canónico de la acción auditada en mayúsculas y separada por
 *                    guiones bajos (por ejemplo {@code "EXECUTION_STARTED"}); nunca
 *                    {@code null}
 * @param actorType   categoría del actor que generó el evento:
 *                    {@code "SYSTEM"} para operaciones automáticas del motor ETL,
 *                    {@code "USER"} para acciones manuales y
 *                    {@code "SCHEDULER"} para disparadores programados; nunca {@code null}
 * @param details     mapa clave-valor con contexto adicional del evento; nunca {@code null},
 *                    puede estar vacío si no hay detalles adicionales relevantes
 * @param recordedAt  marca de tiempo UTC en que el evento fue persistido en el sistema
 *                    de auditoría; nunca {@code null}
 */
public record AuditRecordDto(
    String id,
    String executionId,
    String pipelineId,
    String action,
    String actorType,
    Map<String, Object> details,
    Instant recordedAt
) {}
