package com.elyares.etl.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Objeto de valor que representa el identificador único de un pipeline ETL.
 *
 * <p>Encapsula un {@link UUID} como identificador de dominio para la entidad {@code Pipeline},
 * garantizando su no nulidad en construcción y proporcionando métodos de fábrica para los
 * distintos puntos de creación (desde UUID existente, desde cadena de texto o generación nueva).</p>
 *
 * <p>Al ser un {@code record} de Java, esta clase es inmutable, y la igualdad estructural
 * está basada en el valor del UUID encapsulado.</p>
 *
 * @param value UUID que identifica de forma única al pipeline.
 */
public record PipelineId(UUID value) {

    /**
     * Constructor compacto que valida que el valor del identificador no sea nulo.
     *
     * @param value UUID del pipeline; no puede ser {@code null}.
     * @throws NullPointerException si {@code value} es {@code null}.
     */
    public PipelineId {
        Objects.requireNonNull(value, "PipelineId value must not be null");
    }

    /**
     * Crea un {@code PipelineId} a partir de un {@link UUID} ya instanciado.
     *
     * @param value UUID del pipeline.
     * @return nueva instancia de {@code PipelineId} con el valor indicado.
     */
    public static PipelineId of(UUID value) {
        return new PipelineId(value);
    }

    /**
     * Crea un {@code PipelineId} a partir de su representación en cadena de texto.
     *
     * @param value representación en formato UUID estándar (p. ej. {@code "550e8400-e29b-41d4-a716-446655440000"}).
     * @return nueva instancia de {@code PipelineId} con el UUID parseado.
     * @throws IllegalArgumentException si la cadena no tiene formato UUID válido.
     */
    public static PipelineId of(String value) {
        return new PipelineId(UUID.fromString(value));
    }

    /**
     * Genera un nuevo {@code PipelineId} con un UUID aleatorio.
     *
     * <p>Utilizado al crear un nuevo pipeline que aún no tiene identificador asignado.</p>
     *
     * @return nueva instancia de {@code PipelineId} con un UUID generado aleatoriamente.
     */
    public static PipelineId generate() {
        return new PipelineId(UUID.randomUUID());
    }

    /**
     * Devuelve la representación en cadena del UUID encapsulado.
     *
     * @return cadena en formato UUID estándar.
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
