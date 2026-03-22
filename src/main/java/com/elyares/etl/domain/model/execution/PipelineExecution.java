package com.elyares.etl.domain.model.execution;

import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.domain.valueobject.RecordCount;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa una instancia concreta de ejecución de un pipeline ETL.
 *
 * <p>Un {@code PipelineExecution} modela todo el ciclo de vida de una ejecución:
 * desde su creación en estado {@link ExecutionStatus#PENDING}, pasando por
 * {@link ExecutionStatus#RUNNING}, hasta su finalización en uno de los estados
 * terminales ({@link ExecutionStatus#SUCCESS}, {@link ExecutionStatus#FAILED} o
 * {@link ExecutionStatus#PARTIAL}).</p>
 *
 * <p>Acumula los pasos individuales de ejecución ({@link PipelineExecutionStep}) y
 * los errores producidos ({@link ExecutionError}), así como las métricas de registros
 * procesados en cada etapa del pipeline (lectura, transformación, rechazo y carga).</p>
 *
 * <p>Las listas de pasos y errores se exponen de forma inmutable a través de sus
 * respectivos getters.</p>
 */
public final class PipelineExecution {

    private final UUID id;
    private final PipelineId pipelineId;
    private final ExecutionId executionId;
    private final TriggerType triggerType;
    private final String triggeredBy;
    private final Instant createdAt;

    private ExecutionStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private RecordCount totalRead;
    private RecordCount totalTransformed;
    private RecordCount totalRejected;
    private RecordCount totalLoaded;
    private String errorSummary;
    private int retryCount;
    private UUID parentExecutionId;

    private final List<PipelineExecutionStep> steps;
    private final List<ExecutionError> errors;

    /**
     * Construye una nueva {@code PipelineExecution} en estado inicial {@link ExecutionStatus#PENDING}.
     *
     * <p>Si {@code id} es {@code null}, se genera un {@link UUID} aleatorio.
     * Los contadores de registros se inicializan a cero y el campo {@code retryCount}
     * parte de {@code 0}. La marca de tiempo {@code createdAt} se fija con
     * {@link Instant#now()} en el momento de construcción.</p>
     *
     * @param id          identificador interno de la ejecución; si es {@code null} se genera automáticamente
     * @param pipelineId  identificador del pipeline que se ejecuta; no puede ser {@code null}
     * @param executionId identificador lógico de la ejecución; no puede ser {@code null}
     * @param triggerType tipo de disparador que originó la ejecución; no puede ser {@code null}
     * @param triggeredBy identificador del actor (usuario o sistema) que disparó la ejecución;
     *                    puede ser {@code null}
     * @throws NullPointerException si {@code pipelineId}, {@code executionId} o
     *                              {@code triggerType} son {@code null}
     */
    public PipelineExecution(UUID id, PipelineId pipelineId, ExecutionId executionId,
                             TriggerType triggerType, String triggeredBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.pipelineId = Objects.requireNonNull(pipelineId);
        this.executionId = Objects.requireNonNull(executionId);
        this.triggerType = Objects.requireNonNull(triggerType);
        this.triggeredBy = triggeredBy;
        this.status = ExecutionStatus.PENDING;
        this.totalRead = RecordCount.zero();
        this.totalTransformed = RecordCount.zero();
        this.totalRejected = RecordCount.zero();
        this.totalLoaded = RecordCount.zero();
        this.retryCount = 0;
        this.steps = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    /**
     * Transiciona la ejecución al estado {@link ExecutionStatus#RUNNING} y registra
     * la marca de tiempo de inicio.
     */
    public void start() {
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Finaliza la ejecución con estado {@link ExecutionStatus#SUCCESS} y actualiza
     * los contadores de registros procesados.
     *
     * @param read        total de registros leídos en la fase de extracción
     * @param transformed total de registros transformados correctamente
     * @param rejected    total de registros rechazados por las reglas de validación
     * @param loaded      total de registros cargados en el destino
     */
    public void complete(RecordCount read, RecordCount transformed,
                         RecordCount rejected, RecordCount loaded) {
        this.totalRead = read;
        this.totalTransformed = transformed;
        this.totalRejected = rejected;
        this.totalLoaded = loaded;
        this.status = ExecutionStatus.SUCCESS;
        this.finishedAt = Instant.now();
    }

    /**
     * Finaliza la ejecución con estado {@link ExecutionStatus#FAILED} y registra
     * un resumen del error que provocó el fallo.
     *
     * @param errorSummary descripción concisa del error que causó el fallo de la ejecución
     */
    public void fail(String errorSummary) {
        this.errorSummary = errorSummary;
        this.status = ExecutionStatus.FAILED;
        this.finishedAt = Instant.now();
    }

    /**
     * Finaliza la ejecución con estado {@link ExecutionStatus#PARTIAL}, indicando que
     * se completó parcialmente: algunos registros fueron procesados pero otros fallaron.
     *
     * @param read         total de registros leídos en la fase de extracción
     * @param transformed  total de registros transformados correctamente
     * @param rejected     total de registros rechazados por las reglas de validación
     * @param loaded       total de registros cargados en el destino
     * @param errorSummary descripción de los errores que impidieron la finalización completa
     */
    public void partialSuccess(RecordCount read, RecordCount transformed,
                                RecordCount rejected, RecordCount loaded,
                                String errorSummary) {
        this.totalRead = read;
        this.totalTransformed = transformed;
        this.totalRejected = rejected;
        this.totalLoaded = loaded;
        this.errorSummary = errorSummary;
        this.status = ExecutionStatus.PARTIAL;
        this.finishedAt = Instant.now();
    }

    /**
     * Añade un paso de ejecución al historial de pasos de esta ejecución.
     *
     * @param step paso de ejecución a agregar; no debería ser {@code null}
     */
    public void addStep(PipelineExecutionStep step) {
        steps.add(step);
    }

    /**
     * Registra un error ocurrido durante la ejecución.
     *
     * @param error error a registrar; no debería ser {@code null}
     */
    public void addError(ExecutionError error) {
        errors.add(error);
    }

    /**
     * Indica si la ejecución contiene algún error de severidad crítica.
     *
     * @return {@code true} si al menos un {@link ExecutionError} registrado es crítico;
     *         {@code false} en caso contrario
     * @see ExecutionError#isCritical()
     */
    public boolean hasCriticalErrors() {
        return errors.stream().anyMatch(ExecutionError::isCritical);
    }

    // Getters

    /**
     * Devuelve el identificador interno único de esta ejecución.
     *
     * @return UUID de la ejecución; nunca {@code null}
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador del pipeline que origina esta ejecución.
     *
     * @return identificador del pipeline; nunca {@code null}
     */
    public PipelineId getPipelineId() { return pipelineId; }

    /**
     * Devuelve el identificador lógico de esta ejecución.
     *
     * @return identificador de ejecución; nunca {@code null}
     */
    public ExecutionId getExecutionId() { return executionId; }

    /**
     * Devuelve el tipo de disparador que inició esta ejecución.
     *
     * @return tipo de disparador; nunca {@code null}
     */
    public TriggerType getTriggerType() { return triggerType; }

    /**
     * Devuelve el identificador del actor que disparó la ejecución.
     *
     * @return identificador del actor, o {@code null} si no fue especificado
     */
    public String getTriggeredBy() { return triggeredBy; }

    /**
     * Devuelve el estado actual de la ejecución.
     *
     * @return estado de la ejecución; nunca {@code null}
     */
    public ExecutionStatus getStatus() { return status; }

    /**
     * Devuelve la marca de tiempo en que la ejecución pasó a estado {@code RUNNING}.
     *
     * @return instante de inicio, o {@code null} si la ejecución no ha comenzado todavía
     */
    public Instant getStartedAt() { return startedAt; }

    /**
     * Devuelve la marca de tiempo en que la ejecución finalizó (con cualquier estado terminal).
     *
     * @return instante de finalización, o {@code null} si la ejecución no ha concluido
     */
    public Instant getFinishedAt() { return finishedAt; }

    /**
     * Devuelve el total de registros leídos durante la fase de extracción.
     *
     * @return conteo de registros leídos; nunca {@code null}
     */
    public RecordCount getTotalRead() { return totalRead; }

    /**
     * Devuelve el total de registros transformados correctamente.
     *
     * @return conteo de registros transformados; nunca {@code null}
     */
    public RecordCount getTotalTransformed() { return totalTransformed; }

    /**
     * Devuelve el total de registros rechazados por las reglas de validación.
     *
     * @return conteo de registros rechazados; nunca {@code null}
     */
    public RecordCount getTotalRejected() { return totalRejected; }

    /**
     * Devuelve el total de registros cargados exitosamente en el destino.
     *
     * @return conteo de registros cargados; nunca {@code null}
     */
    public RecordCount getTotalLoaded() { return totalLoaded; }

    /**
     * Devuelve el resumen del error que causó el fallo o la finalización parcial de la ejecución.
     *
     * @return resumen del error, o {@code null} si la ejecución fue exitosa
     */
    public String getErrorSummary() { return errorSummary; }

    /**
     * Devuelve el número de reintentos realizados hasta el momento.
     *
     * @return conteo de reintentos; siempre &ge; 0
     */
    public int getRetryCount() { return retryCount; }

    /**
     * Devuelve el identificador de la ejecución padre en caso de que esta ejecución
     * sea un reintento de otra anterior.
     *
     * @return UUID de la ejecución padre, o {@code null} si no existe ejecución padre
     */
    public UUID getParentExecutionId() { return parentExecutionId; }

    /**
     * Devuelve la marca de tiempo en que se creó esta instancia de ejecución.
     *
     * @return instante de creación; nunca {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Devuelve una vista inmutable de la lista de pasos de esta ejecución.
     *
     * @return lista no modificable de {@link PipelineExecutionStep}; nunca {@code null}
     */
    public List<PipelineExecutionStep> getSteps() { return Collections.unmodifiableList(steps); }

    /**
     * Devuelve una vista inmutable de la lista de errores registrados en esta ejecución.
     *
     * @return lista no modificable de {@link ExecutionError}; nunca {@code null}
     */
    public List<ExecutionError> getErrors() { return Collections.unmodifiableList(errors); }

    /**
     * Establece el identificador de la ejecución padre, vinculando esta ejecución
     * como un reintento de la indicada.
     *
     * @param parentId UUID de la ejecución padre; puede ser {@code null}
     */
    public void setParentExecutionId(UUID parentId) { this.parentExecutionId = parentId; }

    /**
     * Incrementa en uno el contador de reintentos de esta ejecución.
     */
    public void incrementRetryCount() { this.retryCount++; }

    /**
     * Sobreescribe el estado actual de la ejecución.
     *
     * <p>Debe usarse con precaución; se prefiere utilizar los métodos de transición
     * de estado ({@link #start()}, {@link #complete}, {@link #fail}, {@link #partialSuccess})
     * para mantener la consistencia del modelo.</p>
     *
     * @param status nuevo estado de la ejecución; no debería ser {@code null}
     */
    public void setStatus(ExecutionStatus status) { this.status = status; }
}
