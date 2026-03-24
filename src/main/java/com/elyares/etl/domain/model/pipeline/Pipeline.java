package com.elyares.etl.domain.model.pipeline;

import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.PipelineId;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad raíz de dominio que representa un pipeline ETL.
 *
 * <p>Un {@code Pipeline} encapsula toda la configuración necesaria para ejecutar
 * un proceso de extracción, transformación y carga de datos: origen, destino,
 * validación, planificación horaria y política de reintentos.</p>
 *
 * <p>Las instancias de esta clase son inmutables; todos sus campos se establecen
 * en el constructor y no pueden modificarse posteriormente.</p>
 *
 * <p>La igualdad entre pipelines se determina exclusivamente por su {@link PipelineId}.</p>
 */
public final class Pipeline {

    private final PipelineId id;
    private final String name;
    private final String version;
    private final String description;
    private final PipelineStatus status;
    private final SourceConfig sourceConfig;
    private final TargetConfig targetConfig;
    private final TransformationConfig transformationConfig;
    private final ValidationConfig validationConfig;
    private final ScheduleConfig scheduleConfig;
    private final RetryPolicy retryPolicy;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Construye un nuevo {@code Pipeline} con todos sus parámetros de configuración.
     *
     * <p>Si {@code status} es {@code null}, se asigna {@link PipelineStatus#ACTIVE} por defecto.
     * Si {@code scheduleConfig} es {@code null}, se utiliza {@link ScheduleConfig#disabled()}.
     * Si {@code retryPolicy} es {@code null}, se utiliza {@link RetryPolicy#noRetry()}.
     * Si {@code createdAt} o {@code updatedAt} son {@code null}, se asigna {@link Instant#now()}.</p>
     *
     * @param id               identificador único del pipeline; no puede ser {@code null}
     * @param name             nombre descriptivo del pipeline; no puede ser {@code null}
     * @param version          versión semántica del pipeline; no puede ser {@code null}
     * @param description      descripción opcional del propósito del pipeline
     * @param status           estado actual del pipeline; si es {@code null} se usa {@code ACTIVE}
     * @param sourceConfig     configuración de la fuente de datos; no puede ser {@code null}
     * @param targetConfig     configuración del destino de datos; no puede ser {@code null}
     * @param transformationConfig configuración de transformación; si es {@code null} se usa configuración por defecto
     * @param validationConfig configuración de reglas de validación; no puede ser {@code null}
     * @param scheduleConfig   configuración de planificación horaria; si es {@code null} se deshabilita
     * @param retryPolicy      política de reintentos ante fallos; si es {@code null} no se reintenta
     * @param createdAt        marca de tiempo de creación; si es {@code null} se usa el instante actual
     * @param updatedAt        marca de tiempo de última actualización; si es {@code null} se usa el instante actual
     * @throws NullPointerException si {@code id}, {@code name}, {@code version},
     *                              {@code sourceConfig}, {@code targetConfig} o
     *                              {@code validationConfig} son {@code null}
     */
    public Pipeline(PipelineId id, String name, String version, String description,
                    PipelineStatus status, SourceConfig sourceConfig, TargetConfig targetConfig,
                    TransformationConfig transformationConfig, ValidationConfig validationConfig, ScheduleConfig scheduleConfig,
                    RetryPolicy retryPolicy, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "PipelineId must not be null");
        this.name = Objects.requireNonNull(name, "Pipeline name must not be null");
        this.version = Objects.requireNonNull(version, "Pipeline version must not be null");
        this.description = description;
        this.status = status != null ? status : PipelineStatus.ACTIVE;
        this.sourceConfig = Objects.requireNonNull(sourceConfig);
        this.targetConfig = Objects.requireNonNull(targetConfig);
        this.transformationConfig = transformationConfig != null
            ? transformationConfig
            : TransformationConfig.defaultConfig();
        this.validationConfig = Objects.requireNonNull(validationConfig);
        this.scheduleConfig = scheduleConfig != null ? scheduleConfig : ScheduleConfig.disabled();
        this.retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.noRetry();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    /**
     * Indica si el pipeline se encuentra en estado activo.
     *
     * @return {@code true} si el estado del pipeline es {@link PipelineStatus#ACTIVE};
     *         {@code false} en caso contrario
     */
    public boolean isActive() {
        return status == PipelineStatus.ACTIVE;
    }

    /**
     * Devuelve el identificador único del pipeline.
     *
     * @return identificador del pipeline; nunca {@code null}
     */
    public PipelineId getId() { return id; }

    /**
     * Devuelve el nombre descriptivo del pipeline.
     *
     * @return nombre del pipeline; nunca {@code null}
     */
    public String getName() { return name; }

    /**
     * Devuelve la versión semántica del pipeline.
     *
     * @return cadena de versión del pipeline; nunca {@code null}
     */
    public String getVersion() { return version; }

    /**
     * Devuelve la descripción del pipeline.
     *
     * @return descripción del pipeline, o {@code null} si no se especificó
     */
    public String getDescription() { return description; }

    /**
     * Devuelve el estado actual del pipeline.
     *
     * @return estado del pipeline; nunca {@code null}
     */
    public PipelineStatus getStatus() { return status; }

    /**
     * Devuelve la configuración de la fuente de datos del pipeline.
     *
     * @return configuración del origen; nunca {@code null}
     */
    public SourceConfig getSourceConfig() { return sourceConfig; }

    /**
     * Devuelve la configuración del destino de datos del pipeline.
     *
     * @return configuración del destino; nunca {@code null}
     */
    public TargetConfig getTargetConfig() { return targetConfig; }

    /**
     * Devuelve la configuración de transformación aplicada al pipeline.
     *
     * @return configuración de transformación; nunca {@code null}
     */
    public TransformationConfig getTransformationConfig() { return transformationConfig; }

    /**
     * Devuelve la configuración de validación aplicada al pipeline.
     *
     * @return configuración de validación; nunca {@code null}
     */
    public ValidationConfig getValidationConfig() { return validationConfig; }

    /**
     * Devuelve la configuración de planificación horaria del pipeline.
     *
     * @return configuración de schedule; nunca {@code null}
     */
    public ScheduleConfig getScheduleConfig() { return scheduleConfig; }

    /**
     * Devuelve la política de reintentos configurada para el pipeline.
     *
     * @return política de reintentos; nunca {@code null}
     */
    public RetryPolicy getRetryPolicy() { return retryPolicy; }

    /**
     * Devuelve la marca de tiempo en que fue creado el pipeline.
     *
     * @return instante de creación; nunca {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Devuelve la marca de tiempo de la última actualización del pipeline.
     *
     * @return instante de última actualización; nunca {@code null}
     */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Determina la igualdad basándose exclusivamente en el {@link PipelineId}.
     *
     * @param o objeto a comparar
     * @return {@code true} si ambos pipelines comparten el mismo {@code PipelineId}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pipeline p)) return false;
        return Objects.equals(id, p.id);
    }

    /**
     * Calcula el código hash basándose exclusivamente en el {@link PipelineId}.
     *
     * @return código hash del pipeline
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
