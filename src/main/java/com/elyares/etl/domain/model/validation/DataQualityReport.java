package com.elyares.etl.domain.model.validation;

/**
 * Informe inmutable de calidad de datos generado al finalizar la fase de validación del pipeline ETL.
 *
 * <p>Consolida las métricas de calidad calculadas sobre el conjunto total de registros procesados:
 * cantidad de registros válidos y rechazados, tasa de error calculada como porcentaje, el umbral
 * de error configurado y si dicho umbral fue superado.</p>
 *
 * <p>Es un {@code record} de Java, por lo que es inmutable por definición. La construcción se
 * realiza a través del método de fábrica estático {@link #of(long, long, double)}, que calcula
 * automáticamente los valores derivados ({@code validRecords}, {@code errorRatePercent} y
 * {@code thresholdBreached}) a partir de los datos de entrada.</p>
 */
public record DataQualityReport(
    /** Número total de registros procesados durante la fase de validación. */
    long totalRecords,
    /** Número de registros que superaron la validación sin errores bloqueantes. */
    long validRecords,
    /** Número de registros rechazados por no superar la validación. */
    long rejectedRecords,
    /** Porcentaje de registros rechazados sobre el total procesado. */
    double errorRatePercent,
    /** Indica si la tasa de error supera el umbral máximo configurado. */
    boolean thresholdBreached,
    /** Umbral máximo de tasa de error permitido, expresado como porcentaje. */
    double thresholdPercent
) {
    /**
     * Crea un {@code DataQualityReport} calculando automáticamente las métricas derivadas.
     *
     * <p>Los valores calculados son:</p>
     * <ul>
     *   <li>{@code validRecords} = {@code totalRecords} - {@code rejectedRecords}</li>
     *   <li>{@code errorRatePercent} = {@code (rejectedRecords / totalRecords) * 100}, o {@code 0.0}
     *       si {@code totalRecords} es cero (para evitar división por cero)</li>
     *   <li>{@code thresholdBreached} = {@code true} si {@code errorRatePercent > thresholdPercent}</li>
     * </ul>
     *
     * @param totalRecords     número total de registros procesados durante la validación
     * @param rejectedRecords  número de registros que no superaron la validación
     * @param thresholdPercent porcentaje máximo de error aceptable configurado en el pipeline
     * @return nueva instancia de {@code DataQualityReport} con todas las métricas calculadas
     */
    public static DataQualityReport of(long totalRecords, long rejectedRecords,
                                       double thresholdPercent) {
        long validRecords = totalRecords - rejectedRecords;
        double errorRate = totalRecords == 0 ? 0.0 :
            (double) rejectedRecords / totalRecords * 100.0;
        boolean breached = errorRate > thresholdPercent;
        return new DataQualityReport(totalRecords, validRecords, rejectedRecords,
                                     errorRate, breached, thresholdPercent);
    }

    /**
     * Indica si la calidad de los datos se encuentra dentro del umbral de error aceptable.
     *
     * <p>El conjunto de datos se considera "saludable" cuando la tasa de error no supera
     * el umbral configurado ({@code thresholdBreached == false}).</p>
     *
     * @return {@code true} si la tasa de error no supera el umbral; {@code false} si fue superado
     */
    public boolean isHealthy() {
        return !thresholdBreached;
    }
}
