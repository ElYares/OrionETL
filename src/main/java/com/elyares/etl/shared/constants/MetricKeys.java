package com.elyares.etl.shared.constants;

/**
 * Claves canónicas para las métricas de ejecución del pipeline ETL.
 *
 * <p>Estas constantes se utilizan como nombres de métricas al registrar
 * estadísticas de procesamiento en el sistema de monitorización. Siguen
 * una convención de nombres en notación punto ({@code dominio.nombre}) para
 * facilitar su agrupación y filtrado en herramientas de observabilidad.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class MetricKeys {

    /** Número total de registros leídos desde la fuente de datos. */
    public static final String RECORDS_READ        = "records.read";

    /** Número de registros que superaron el paso de transformación. */
    public static final String RECORDS_TRANSFORMED = "records.transformed";

    /** Número de registros rechazados durante la validación o transformación. */
    public static final String RECORDS_REJECTED    = "records.rejected";

    /** Número de registros cargados exitosamente en el destino. */
    public static final String RECORDS_LOADED      = "records.loaded";

    /** Porcentaje de error calculado sobre el total de registros procesados. */
    public static final String ERROR_RATE_PCT      = "error.rate.percent";

    /** Duración total de la ejecución del pipeline en milisegundos. */
    public static final String DURATION_MS         = "duration.ms";

    /** Duración del paso de extracción en milisegundos. */
    public static final String EXTRACT_DURATION_MS = "extract.duration.ms";

    /** Duración del paso de transformación en milisegundos. */
    public static final String TRANSFORM_DURATION_MS = "transform.duration.ms";

    /** Duración del paso de carga en milisegundos. */
    public static final String LOAD_DURATION_MS    = "load.duration.ms";

    private MetricKeys() {}
}
