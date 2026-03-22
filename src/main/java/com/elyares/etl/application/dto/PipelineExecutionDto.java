package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import java.time.Instant;
import java.util.List;

/**
 * DTO de estado completo de una ejecución de pipeline ETL.
 *
 * <p>Representa la vista externa del agregado {@code PipelineExecution}. Incluye conteos
 * acumulados de registros en cada etapa del flujo ETL (lectura, transformación, rechazo y
 * carga), el estado global de la ejecución, los metadatos de disparo y la lista detallada
 * de pasos individuales.</p>
 *
 * <p>Es la respuesta principal al consultar el estado de una ejecución en curso o
 * terminada (por ejemplo, {@code GET /executions/{executionId}}). Para consultas de
 * polling ligeras se recomienda usar {@link ExecutionStatusDto} en su lugar.</p>
 *
 * <p>La lista {@code steps} está ordenada por {@code stepOrder} ascendente y nunca es
 * {@code null}; puede estar vacía si aún no se ha iniciado ningún paso.</p>
 *
 * @param executionId      identificador público UUID de la ejecución; corresponde al
 *                         valor interno de {@code ExecutionId}
 * @param pipelineId       identificador UUID del pipeline ejecutado; corresponde al
 *                         valor interno de {@code PipelineId}
 * @param pipelineName     nombre del pipeline ejecutado en el momento de la ejecución
 * @param status           estado actual de la ejecución
 *                         ({@code PENDING}, {@code RUNNING}, {@code COMPLETED},
 *                         {@code FAILED}, {@code CANCELLED})
 * @param triggerType      mecanismo que originó la ejecución
 *                         ({@code MANUAL}, {@code SCHEDULED}, {@code RETRY}, {@code CLI})
 * @param triggeredBy      actor que inició la ejecución (nombre de usuario,
 *                         {@code "scheduler"} o identificador de proceso)
 * @param startedAt        marca de tiempo UTC en que comenzó la ejecución;
 *                         nunca {@code null}
 * @param finishedAt       marca de tiempo UTC en que finalizó la ejecución;
 *                         {@code null} si la ejecución aún está en curso
 * @param totalRead        número total de registros leídos de la fuente de datos
 * @param totalTransformed número total de registros transformados exitosamente
 * @param totalRejected    número total de registros rechazados en cualquier paso del
 *                         pipeline (validación de esquema, transformación, carga, etc.)
 * @param totalLoaded      número total de registros cargados con éxito en el destino
 * @param errorSummary     mensaje resumido del error principal si la ejecución terminó en
 *                         estado {@code FAILED}; {@code null} si fue exitosa o está en curso
 * @param steps            lista de {@link ExecutionStepDto} con el estado individual de
 *                         cada paso; nunca {@code null}
 *
 * @see ExecutionStatus
 * @see TriggerType
 * @see ExecutionStepDto
 * @see ExecutionStatusDto
 */
public record PipelineExecutionDto(
    String executionId,
    String pipelineId,
    String pipelineName,
    ExecutionStatus status,
    TriggerType triggerType,
    String triggeredBy,
    Instant startedAt,
    Instant finishedAt,
    long totalRead,
    long totalTransformed,
    long totalRejected,
    long totalLoaded,
    String errorSummary,
    List<ExecutionStepDto> steps
) {}
