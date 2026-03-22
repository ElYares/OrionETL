package com.elyares.etl.domain.model.target;

import java.time.Instant;

/**
 * Resultado inmutable de una operación de carga de datos en el destino ETL.
 *
 * <p>Consolida las métricas de la operación de escritura: registros insertados, actualizados
 * y rechazados, junto con el estado de éxito o fallo y la marca temporal de finalización.
 * En caso de fallo, incluye el detalle del error que impidió la carga.</p>
 *
 * <p>La construcción se realiza exclusivamente a través de los métodos de fábrica estáticos
 * {@link #success(long, long, long)} y {@link #failure(String)}, que garantizan la coherencia
 * interna del objeto.</p>
 */
public final class LoadResult {

    /** Número de registros insertados como nuevas filas en la tabla destino. */
    private final long totalInserted;

    /** Número de registros actualizados sobre filas existentes en la tabla destino. */
    private final long totalUpdated;

    /** Número de registros rechazados y no persistidos durante la operación de carga. */
    private final long totalRejected;

    /** Indica si la operación de carga finalizó sin errores fatales. */
    private final boolean successful;

    /** Descripción técnica del error que causó el fallo; {@code null} si la carga fue exitosa. */
    private final String errorDetail;

    /** Marca temporal en la que se completó la operación de carga. */
    private final Instant loadedAt;

    /**
     * Constructor privado. Las instancias deben crearse a través de los métodos de fábrica
     * {@link #success(long, long, long)} y {@link #failure(String)}.
     *
     * @param totalInserted número de registros insertados
     * @param totalUpdated  número de registros actualizados
     * @param totalRejected número de registros rechazados
     * @param successful    {@code true} si la carga fue exitosa
     * @param errorDetail   descripción del error; {@code null} si la carga fue exitosa
     */
    private LoadResult(long totalInserted, long totalUpdated, long totalRejected,
                       boolean successful, String errorDetail) {
        this.totalInserted = totalInserted;
        this.totalUpdated = totalUpdated;
        this.totalRejected = totalRejected;
        this.successful = successful;
        this.errorDetail = errorDetail;
        this.loadedAt = Instant.now();
    }

    /**
     * Crea un {@code LoadResult} que representa una carga exitosa con las métricas especificadas.
     *
     * <p>La marca temporal de carga se establece al instante actual.</p>
     *
     * @param inserted número de registros insertados como nuevas filas
     * @param updated  número de registros actualizados sobre filas existentes
     * @param rejected número de registros rechazados y no persistidos
     * @return nueva instancia representando una carga exitosa
     */
    public static LoadResult success(long inserted, long updated, long rejected) {
        return new LoadResult(inserted, updated, rejected, true, null);
    }

    /**
     * Crea un {@code LoadResult} que representa una carga fallida.
     *
     * <p>Todos los contadores de registros se establecen en cero.
     * La marca temporal de carga se establece al instante actual.</p>
     *
     * @param errorDetail descripción técnica del error que causó el fallo de la carga
     * @return nueva instancia representando una carga fallida
     */
    public static LoadResult failure(String errorDetail) {
        return new LoadResult(0, 0, 0, false, errorDetail);
    }

    /**
     * Devuelve el número total de registros insertados como nuevas filas en la tabla destino.
     *
     * @return contador de inserciones; {@code 0} si la carga falló
     */
    public long getTotalInserted() { return totalInserted; }

    /**
     * Devuelve el número total de registros actualizados sobre filas existentes en la tabla destino.
     *
     * @return contador de actualizaciones; {@code 0} si la carga falló
     */
    public long getTotalUpdated() { return totalUpdated; }

    /**
     * Devuelve el número total de registros rechazados y no persistidos durante la carga.
     *
     * @return contador de rechazos; {@code 0} si la carga falló
     */
    public long getTotalRejected() { return totalRejected; }

    /**
     * Devuelve la suma de registros insertados y actualizados, representando el total
     * de registros efectivamente persistidos en el destino.
     *
     * @return suma de {@code totalInserted} y {@code totalUpdated}
     */
    public long getTotalLoaded() { return totalInserted + totalUpdated; }

    /**
     * Indica si la operación de carga se completó sin errores fatales.
     *
     * @return {@code true} si la carga fue exitosa; {@code false} en caso contrario
     */
    public boolean isSuccessful() { return successful; }

    /**
     * Devuelve el detalle técnico del error que causó el fallo de la carga.
     *
     * @return descripción del error, o {@code null} si la carga fue exitosa
     */
    public String getErrorDetail() { return errorDetail; }

    /**
     * Devuelve la marca temporal en la que se completó la operación de carga.
     *
     * @return instante de finalización de la carga; nunca {@code null}
     */
    public Instant getLoadedAt() { return loadedAt; }
}
