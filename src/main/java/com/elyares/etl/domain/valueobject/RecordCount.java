package com.elyares.etl.domain.valueobject;

/**
 * Objeto de valor que representa un conteo de registros en el contexto de un pipeline ETL.
 *
 * <p>Encapsula un valor {@code long} no negativo que cuantifica registros extraídos,
 * transformados, cargados, rechazados o procesados en cualquier fase del pipeline.
 * La invariante de no negatividad se hace cumplir en construcción.</p>
 *
 * <p>Al ser un {@code record} de Java, esta clase es inmutable. Las operaciones aritméticas
 * devuelven nuevas instancias, preservando la inmutabilidad.</p>
 *
 * @param value número de registros; debe ser mayor o igual a cero.
 */
public record RecordCount(long value) {

    /**
     * Constructor compacto que valida que el conteo no sea negativo.
     *
     * @param value número de registros.
     * @throws IllegalArgumentException si {@code value} es negativo.
     */
    public RecordCount {
        if (value < 0) throw new IllegalArgumentException("RecordCount cannot be negative: " + value);
    }

    /**
     * Crea un {@code RecordCount} con el valor indicado.
     *
     * @param value número de registros; debe ser mayor o igual a cero.
     * @return nueva instancia de {@code RecordCount}.
     * @throws IllegalArgumentException si {@code value} es negativo.
     */
    public static RecordCount of(long value) {
        return new RecordCount(value);
    }

    /**
     * Crea un {@code RecordCount} con valor cero.
     *
     * <p>Utilizado como valor inicial al comenzar a acumular conteos.</p>
     *
     * @return nueva instancia de {@code RecordCount} con valor {@code 0}.
     */
    public static RecordCount zero() {
        return new RecordCount(0);
    }

    /**
     * Devuelve un nuevo {@code RecordCount} con el valor incrementado en el delta indicado.
     *
     * @param delta incremento a añadir; puede ser cero pero el resultado no debe producir un valor negativo.
     * @return nueva instancia de {@code RecordCount} con el valor resultante.
     * @throws IllegalArgumentException si el resultado es negativo.
     */
    public RecordCount add(long delta) {
        return new RecordCount(this.value + delta);
    }

    /**
     * Devuelve un nuevo {@code RecordCount} con el valor suma de esta instancia y la indicada.
     *
     * @param other {@code RecordCount} cuyos registros se suman a los de esta instancia.
     * @return nueva instancia de {@code RecordCount} con el valor agregado.
     */
    public RecordCount add(RecordCount other) {
        return new RecordCount(this.value + other.value);
    }

    /**
     * Indica si el conteo de registros es igual a cero.
     *
     * @return {@code true} si el valor es {@code 0}; {@code false} en caso contrario.
     */
    public boolean isZero() {
        return value == 0;
    }

    /**
     * Devuelve la representación en cadena del conteo como número entero.
     *
     * @return representación textual del valor numérico.
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
