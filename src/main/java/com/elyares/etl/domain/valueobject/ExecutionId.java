package com.elyares.etl.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Objeto de valor que representa el identificador único de una ejecución de pipeline ETL.
 *
 * <p>Encapsula un {@link UUID} como identificador de dominio para la entidad {@code PipelineExecution},
 * garantizando su no nulidad en construcción y proporcionando métodos de fábrica para los
 * distintos puntos de creación (desde UUID existente, desde cadena de texto o generación nueva).</p>
 *
 * <p>Al ser un {@code record} de Java, esta clase es inmutable, y la igualdad estructural
 * está basada en el valor del UUID encapsulado.</p>
 *
 * @param value UUID que identifica de forma única a la ejecución.
 */
public record ExecutionId(UUID value) {

    /**
     * Constructor compacto que valida que el valor del identificador no sea nulo.
     *
     * @param value UUID de la ejecución; no puede ser {@code null}.
     * @throws NullPointerException si {@code value} es {@code null}.
     */
    public ExecutionId {
        Objects.requireNonNull(value, "ExecutionId value must not be null");
    }

    /**
     * Crea un {@code ExecutionId} a partir de un {@link UUID} ya instanciado.
     *
     * @param value UUID de la ejecución.
     * @return nueva instancia de {@code ExecutionId} con el valor indicado.
     */
    public static ExecutionId of(UUID value) {
        return new ExecutionId(value);
    }

    /**
     * Crea un {@code ExecutionId} a partir de su representación en cadena de texto.
     *
     * @param value representación en formato UUID estándar (p. ej. {@code "550e8400-e29b-41d4-a716-446655440000"}).
     * @return nueva instancia de {@code ExecutionId} con el UUID parseado.
     * @throws IllegalArgumentException si la cadena no tiene formato UUID válido.
     */
    public static ExecutionId of(String value) {
        return new ExecutionId(UUID.fromString(value));
    }

    /**
     * Genera un nuevo {@code ExecutionId} con un UUID aleatorio.
     *
     * <p>Utilizado al iniciar una nueva ejecución de pipeline que aún no tiene identificador asignado.</p>
     *
     * @return nueva instancia de {@code ExecutionId} con un UUID generado aleatoriamente.
     */
    public static ExecutionId generate() {
        return new ExecutionId(UUID.randomUUID());
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
