package com.elyares.etl.domain.valueobject;

/**
 * Objeto de valor que representa el umbral máximo de error tolerable en una ejecución de pipeline ETL.
 *
 * <p>Encapsula un porcentaje ({@code double}) en el rango {@code [0.0, 100.0]} que define
 * la proporción máxima de registros rechazados respecto al total procesado que se considera
 * aceptable. Si la tasa real de error supera este umbral, la ejecución puede ser marcada
 * como fallida o parcial según la política configurada.</p>
 *
 * <p>Al ser un {@code record} de Java, esta clase es inmutable. El umbral por defecto
 * es del {@code 5.0%}, accesible mediante {@link #defaultThreshold()}.</p>
 *
 * @param percentValue porcentaje de error tolerable; debe estar en el rango {@code [0.0, 100.0]}.
 */
public record ErrorThreshold(double percentValue) {

    /**
     * Constructor compacto que valida que el porcentaje esté dentro del rango permitido.
     *
     * @param percentValue porcentaje de error tolerable.
     * @throws IllegalArgumentException si {@code percentValue} está fuera del rango {@code [0.0, 100.0]}.
     */
    public ErrorThreshold {
        if (percentValue < 0.0 || percentValue > 100.0) {
            throw new IllegalArgumentException(
                "ErrorThreshold must be between 0.0 and 100.0, got: " + percentValue);
        }
    }

    /**
     * Crea un {@code ErrorThreshold} con el porcentaje indicado.
     *
     * @param percentValue porcentaje de error tolerable en el rango {@code [0.0, 100.0]}.
     * @return nueva instancia de {@code ErrorThreshold}.
     * @throws IllegalArgumentException si {@code percentValue} está fuera del rango permitido.
     */
    public static ErrorThreshold of(double percentValue) {
        return new ErrorThreshold(percentValue);
    }

    /**
     * Crea un {@code ErrorThreshold} con el valor por defecto del sistema ({@code 5.0%}).
     *
     * <p>Utilizado cuando el pipeline no tiene configurado un umbral de error explícito.</p>
     *
     * @return nueva instancia de {@code ErrorThreshold} con valor {@code 5.0}.
     */
    public static ErrorThreshold defaultThreshold() {
        return new ErrorThreshold(5.0);
    }

    /**
     * Determina si la tasa de error calculada supera este umbral.
     *
     * <p>Si el total de registros es cero, se considera que el umbral no ha sido superado
     * para evitar divisiones por cero.</p>
     *
     * @param totalRecords    número total de registros procesados en la ejecución.
     * @param rejectedRecords número de registros rechazados en la ejecución.
     * @return {@code true} si la tasa de error real ({@code rejectedRecords / totalRecords * 100})
     *         es estrictamente mayor que {@code percentValue}; {@code false} en caso contrario.
     */
    public boolean isBreached(long totalRecords, long rejectedRecords) {
        if (totalRecords == 0) return false;
        double errorRate = (double) rejectedRecords / totalRecords * 100.0;
        return errorRate > percentValue;
    }

    /**
     * Calcula la tasa de error real como porcentaje sobre el total de registros.
     *
     * <p>Si el total de registros es cero, devuelve {@code 0.0} para evitar divisiones por cero.</p>
     *
     * @param totalRecords    número total de registros procesados en la ejecución.
     * @param rejectedRecords número de registros rechazados en la ejecución.
     * @return tasa de error en porcentaje ({@code [0.0, 100.0]}), o {@code 0.0} si no hay registros.
     */
    public double calculateRate(long totalRecords, long rejectedRecords) {
        if (totalRecords == 0) return 0.0;
        return (double) rejectedRecords / totalRecords * 100.0;
    }

    /**
     * Devuelve la representación en cadena del umbral como porcentaje.
     *
     * @return cadena con el formato {@code "<valor>%"} (p. ej. {@code "5.0%"}).
     */
    @Override
    public String toString() {
        return percentValue + "%";
    }
}
