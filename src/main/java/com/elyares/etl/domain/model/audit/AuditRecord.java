package com.elyares.etl.domain.model.audit;

import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad de dominio que representa un registro de auditoría de una acción
 * realizada sobre un pipeline ETL.
 *
 * <p>Un {@code AuditRecord} captura de forma inmutable el qué ({@code action}),
 * el quién ({@code actorType}), el cuándo ({@code recordedAt}) y el contexto
 * ({@code details}) de una operación significativa del sistema, asociada
 * opcionalmente a una ejecución ({@link ExecutionId}) y a un pipeline ({@link PipelineId}).</p>
 *
 * <p>Los detalles se almacenan como un mapa inmutable copiado de forma defensiva
 * en el constructor, garantizando que el registro de auditoría no pueda ser
 * alterado tras su creación.</p>
 *
 * <p>El método de fábrica estático {@link #of(ExecutionId, PipelineId, String, Map)}
 * crea registros con actor {@code "SYSTEM"} para eventos generados automáticamente
 * por la plataforma.</p>
 */
public final class AuditRecord {

    private final UUID id;
    private final ExecutionId executionId;
    private final PipelineId pipelineId;
    private final String action;
    private final String actorType;
    private final Map<String, Object> details;
    private final Instant recordedAt;

    /**
     * Construye un nuevo {@code AuditRecord} con los parámetros proporcionados.
     *
     * <p>Si {@code id} es {@code null}, se genera un {@link UUID} aleatorio.
     * Si {@code details} es {@code null}, se asigna un mapa vacío inmutable.
     * La marca de tiempo {@code recordedAt} se fija con {@link Instant#now()}.</p>
     *
     * @param id          identificador único del registro de auditoría;
     *                    si es {@code null} se genera automáticamente
     * @param executionId identificador de la ejecución relacionada; puede ser {@code null}
     * @param pipelineId  identificador del pipeline relacionado; puede ser {@code null}
     * @param action      descripción de la acción auditada (p. ej. {@code "PIPELINE_STARTED"});
     *                    no puede ser {@code null}
     * @param actorType   tipo de actor que realizó la acción (p. ej. {@code "USER"}, {@code "SYSTEM"});
     *                    puede ser {@code null}
     * @param details     mapa de pares clave-valor con información adicional sobre la acción;
     *                    si es {@code null} se almacena un mapa vacío
     * @throws NullPointerException si {@code action} es {@code null}
     */
    public AuditRecord(UUID id, ExecutionId executionId, PipelineId pipelineId,
                       String action, String actorType, Map<String, Object> details) {
        this.id = id != null ? id : UUID.randomUUID();
        this.executionId = executionId;
        this.pipelineId = pipelineId;
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.actorType = actorType;
        this.details = details != null ? Map.copyOf(details) : Map.of();
        this.recordedAt = Instant.now();
    }

    /**
     * Crea un {@code AuditRecord} generado por el sistema ({@code actorType = "SYSTEM"}).
     *
     * <p>El identificador se genera automáticamente.</p>
     *
     * @param executionId identificador de la ejecución relacionada; puede ser {@code null}
     * @param pipelineId  identificador del pipeline relacionado; puede ser {@code null}
     * @param action      descripción de la acción auditada; no puede ser {@code null}
     * @param details     mapa con información adicional sobre la acción; puede ser {@code null}
     * @return nueva instancia de {@code AuditRecord} con actor {@code "SYSTEM"}
     */
    public static AuditRecord of(ExecutionId executionId, PipelineId pipelineId,
                                  String action, Map<String, Object> details) {
        return new AuditRecord(null, executionId, pipelineId, action, "SYSTEM", details);
    }

    /**
     * Devuelve el identificador único de este registro de auditoría.
     *
     * @return UUID del registro; nunca {@code null}
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador de la ejecución asociada a este registro.
     *
     * @return identificador de ejecución, o {@code null} si no está asociado a ninguna
     */
    public ExecutionId getExecutionId() { return executionId; }

    /**
     * Devuelve el identificador del pipeline asociado a este registro.
     *
     * @return identificador del pipeline, o {@code null} si no está asociado a ninguno
     */
    public PipelineId getPipelineId() { return pipelineId; }

    /**
     * Devuelve la descripción de la acción auditada.
     *
     * @return acción auditada; nunca {@code null}
     */
    public String getAction() { return action; }

    /**
     * Devuelve el tipo de actor que realizó la acción auditada.
     *
     * @return tipo de actor (p. ej. {@code "USER"}, {@code "SYSTEM"}),
     *         o {@code null} si no fue especificado
     */
    public String getActorType() { return actorType; }

    /**
     * Devuelve el mapa inmutable de detalles adicionales del registro de auditoría.
     *
     * @return mapa de detalles; nunca {@code null}, puede estar vacío
     */
    public Map<String, Object> getDetails() { return details; }

    /**
     * Devuelve la marca de tiempo en que fue creado este registro de auditoría.
     *
     * @return instante de registro; nunca {@code null}
     */
    public Instant getRecordedAt() { return recordedAt; }
}
