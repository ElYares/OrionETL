package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.StepStatus;
import java.time.Instant;

/**
 * DTO que representa el estado de un paso individual dentro de una ejecución ETL.
 *
 * <p>Cada instancia corresponde a un {@code PipelineExecutionStep} del dominio y describe
 * el comportamiento de una fase concreta del pipeline: nombre canónico del paso, posición
 * en la secuencia de ejecución, estado, ventana temporal y volumen de registros procesados.</p>
 *
 * <p>Los pasos posibles siguen la secuencia estándar del pipeline:
 * {@code INIT} (1), {@code EXTRACT} (2), {@code VALIDATE_SCHEMA} (3),
 * {@code TRANSFORM} (4), {@code VALIDATE_BUSINESS} (5), {@code LOAD} (6),
 * {@code POST_PROCESS} (7), {@code FINALIZE} (8).</p>
 *
 * <p>Esta clase es parte del {@link PipelineExecutionDto} y se genera a través de
 * {@code ExecutionMapper#toStepDto(PipelineExecutionStep)}.</p>
 *
 * @param stepName         nombre canónico del paso
 *                         ({@code INIT}, {@code EXTRACT}, {@code VALIDATE_SCHEMA}, etc.)
 * @param stepOrder        posición ordinal del paso en la secuencia de ejecución (1–8);
 *                         los valores menores se ejecutan primero
 * @param status           estado actual del paso
 *                         ({@code PENDING}, {@code RUNNING}, {@code COMPLETED},
 *                         {@code FAILED}, {@code SKIPPED})
 * @param startedAt        marca de tiempo UTC en que comenzó el paso;
 *                         {@code null} si aún no ha iniciado
 * @param finishedAt       marca de tiempo UTC en que finalizó el paso;
 *                         {@code null} si aún está en curso o no ha iniciado
 * @param recordsProcessed cantidad de registros procesados (leídos, transformados o
 *                         cargados según el tipo de paso) durante este paso; {@code 0}
 *                         si el paso no manipula registros directamente (p. ej. {@code INIT})
 * @param errorDetail      descripción técnica del error que causó el fallo del paso;
 *                         {@code null} si el paso finalizó correctamente o no ha terminado
 *
 * @see StepStatus
 * @see PipelineExecutionDto
 */
public record ExecutionStepDto(
    String stepName,
    int stepOrder,
    StepStatus status,
    Instant startedAt,
    Instant finishedAt,
    long recordsProcessed,
    String errorDetail
) {}
