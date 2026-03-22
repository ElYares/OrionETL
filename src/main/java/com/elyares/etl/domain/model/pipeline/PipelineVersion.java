package com.elyares.etl.domain.model.pipeline;

import com.elyares.etl.domain.valueobject.PipelineId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una versión concreta de un {@link Pipeline}.
 *
 * <p>Cada vez que la definición de un pipeline cambia de forma significativa
 * se genera un nuevo {@code PipelineVersion} que queda registrado con su propio
 * identificador, número de versión y registro de cambios ({@code changelog}).
 * Solo una versión puede estar activa simultáneamente para un pipeline dado.</p>
 *
 * <p>Las instancias son inmutables: todos los campos se fijan en el constructor.</p>
 */
public final class PipelineVersion {

    private final UUID id;
    private final PipelineId pipelineId;
    private final String version;
    private final String changelog;
    private final Instant createdAt;
    private final boolean active;

    /**
     * Construye una nueva {@code PipelineVersion} con los datos proporcionados.
     *
     * @param id         identificador único de esta versión; no puede ser {@code null}
     * @param pipelineId identificador del pipeline al que pertenece esta versión; no puede ser {@code null}
     * @param version    cadena que identifica la versión (p. ej. {@code "1.0.0"}); no puede ser {@code null}
     * @param changelog  descripción textual de los cambios incluidos en esta versión; puede ser {@code null}
     * @param createdAt  marca de tiempo en que se creó esta versión; no puede ser {@code null}
     * @param active     {@code true} si esta versión es la actualmente activa del pipeline
     * @throws NullPointerException si {@code id}, {@code pipelineId}, {@code version}
     *                              o {@code createdAt} son {@code null}
     */
    public PipelineVersion(UUID id, PipelineId pipelineId, String version, String changelog,
                           Instant createdAt, boolean active) {
        this.id = Objects.requireNonNull(id);
        this.pipelineId = Objects.requireNonNull(pipelineId);
        this.version = Objects.requireNonNull(version);
        this.changelog = changelog;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.active = active;
    }

    /**
     * Devuelve el identificador único de esta versión.
     *
     * @return UUID de la versión; nunca {@code null}
     */
    public UUID getId() { return id; }

    /**
     * Devuelve el identificador del pipeline al que pertenece esta versión.
     *
     * @return identificador del pipeline; nunca {@code null}
     */
    public PipelineId getPipelineId() { return pipelineId; }

    /**
     * Devuelve la cadena de versión asociada a este registro.
     *
     * @return cadena de versión; nunca {@code null}
     */
    public String getVersion() { return version; }

    /**
     * Devuelve el registro de cambios de esta versión.
     *
     * @return descripción de los cambios, o {@code null} si no se especificó
     */
    public String getChangelog() { return changelog; }

    /**
     * Devuelve la marca de tiempo en que fue creada esta versión.
     *
     * @return instante de creación; nunca {@code null}
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Indica si esta versión es la activa actualmente para el pipeline.
     *
     * @return {@code true} si la versión está activa; {@code false} en caso contrario
     */
    public boolean isActive() { return active; }
}
