package com.elyares.etl.domain.model.execution;

import com.elyares.etl.domain.enums.StepStatus;
import com.elyares.etl.domain.valueobject.ExecutionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa un paso individual dentro de una {@link PipelineExecution}.
 *
 * <p>Un pipeline ETL se compone de múltiples pasos ordenados (p. ej. extracción,
 * transformación, validación, carga). Esta clase modela el estado y las métricas
 * de ejecución de uno de esos pasos: su nombre, su orden dentro del pipeline,
 * las marcas de tiempo de inicio y fin, el número de registros procesados y,
 * en caso de fallo, el detalle del error.</p>
 *
 * <p>El ciclo de vida de un paso sigue la secuencia:
 * {@link StepStatus#PENDING} &rarr; {@link StepStatus#RUNNING} &rarr;
 * ({@link StepStatus#SUCCESS} | {@link StepStatus#FAILED} | {@link StepStatus#SKIPPED}).</p>
 */
public final class PipelineExecutionStep {

    private final UUID id;
    private final ExecutionId executionId;
    private final String stepName;
    private final int stepOrder;
    private StepStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private long recordsProcessed;
    private String errorDetail;

    /**
     * Construye un nuevo {@code PipelineExecutionStep} en estado inicial
     * {@link StepStatus#PENDING} con el contador de registros a cero.
     *
     * <p>Si {@code id} es {@code null}, se genera un {@link UUID} aleatorio.</p>
     *
     * @param id          identificador único del paso; si es {@code null} se genera automáticamente
     * @param executionId identificador de la ejecución a la que pertenece este paso;
     *                    no puede ser {@code null}
     * @param stepName    nombre del paso (p. ej. {@code "EXTRACT"}, {@code "TRANSFORM"});
     *                    no puede ser {@code null}
     * @param stepOrder   posición ordinal del paso dentro del pipeline; empieza en 1 por convención
     * @throws NullPointerException si {@code executionId} o {@code stepName} son {@code null}
     */
    public PipelineExecutionStep(UUID id, ExecutionId executionId, String stepName, int stepOrder) {
        this.id = id != null ? id : UUID.randomUUID();
        this.executionId = Objects.requireNonNull(executionId);
        this.stepName = Objects.requireNonNull(stepName);
        this.stepOrder = stepOrder;
        this.status = StepStatus.PENDING;
        this.recordsProcessed = 0;
    }

    /**
     * Transiciona el paso al estado {@link StepStatus#RUNNING} y registra
     * la marca de tiempo de inicio.
     */
    public void markRunning() {
        this.status = StepStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Transiciona el paso al estado {@link StepStatus#SUCCESS}, registra la marca
     * de tiempo de finalización y almacena el número de registros procesados.
     *
     * @param recordsProcessed número de registros procesados satisfactoriamente en este paso
     */
    public void markSuccess(long recordsProcessed) {
        this.status = StepStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.recordsProcessed = recordsProcessed;
    }

    /**
     * Transiciona el paso al estado {@link StepStatus#FAILED}, registra la marca
     * de tiempo de finalización y almacena el detalle del error.
     *
     * @param errorDetail descripción del error que causó el fallo del paso
     */
    public void markFailed(String errorDetail) {
        this.status = StepStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorDetail = errorDetail;
    }

    /**
     * Transiciona el paso al estado {@link StepStatus#SKIPPED} y registra
     * la marca de tiempo de finalización.
     *
     * <p>Un paso se marca como omitido cuando las condiciones de ejecución determinan
     * que no debe ejecutarse (p. ej. por una regla de filtrado o un fallo previo
     * que lo hace irrelevante).</p>
     */
    public void markSkipped() {
        this.status = StepStatus.SKIPPED;
        this.finishedAt = Instant.now();
    }

    /**
     * Devuelve el identificador único de este paso.
     *
     * @return UUID del paso; nunca {@code null}
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador de la ejecución a la que pertenece este paso.
     *
     * @return identificador de ejecución; nunca {@code null}
     */
    public ExecutionId getExecutionId() { return executionId; }

    /**
     * Devuelve el nombre del paso de ejecución.
     *
     * @return nombre del paso; nunca {@code null}
     */
    public String getStepName() { return stepName; }

    /**
     * Devuelve la posición ordinal del paso dentro del pipeline.
     *
     * @return orden del paso; empieza en 1 por convención
     */
    public int getStepOrder() { return stepOrder; }

    /**
     * Devuelve el estado actual del paso.
     *
     * @return estado del paso; nunca {@code null}
     */
    public StepStatus getStatus() { return status; }

    /**
     * Devuelve la marca de tiempo en que el paso pasó a estado {@code RUNNING}.
     *
     * @return instante de inicio, o {@code null} si el paso no ha comenzado todavía
     */
    public Instant getStartedAt() { return startedAt; }

    /**
     * Devuelve la marca de tiempo en que el paso finalizó.
     *
     * @return instante de finalización, o {@code null} si el paso no ha concluido
     */
    public Instant getFinishedAt() { return finishedAt; }

    /**
     * Devuelve el número de registros procesados por este paso.
     *
     * @return número de registros procesados; {@code 0} si el paso no ha finalizado en éxito
     */
    public long getRecordsProcessed() { return recordsProcessed; }

    /**
     * Devuelve el detalle del error en caso de que el paso haya fallado.
     *
     * @return descripción del error, o {@code null} si el paso no ha fallado
     */
    public String getErrorDetail() { return errorDetail; }
}
