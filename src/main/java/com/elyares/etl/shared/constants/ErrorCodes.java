package com.elyares.etl.shared.constants;

/**
 * Catálogo centralizado de códigos de error del sistema ETL.
 *
 * <p>Cada constante representa un código único y estable que identifica la
 * causa raíz de un fallo dentro del pipeline. Los códigos siguen la
 * convención {@code ETL_<MÓDULO>_<NÚMERO>} y son transportados por las
 * excepciones que extienden {@link com.elyares.etl.shared.exception.EtlException}
 * así como por las respuestas de error de la API.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class ErrorCodes {

    // Extraction

    /** La fuente de datos no está disponible o no puede ser alcanzada. */
    public static final String EXTRACT_SOURCE_UNAVAILABLE  = "ETL_EXTRACT_001";

    /** Error al analizar sintácticamente el contenido de la fuente. */
    public static final String EXTRACT_PARSE_FAILURE       = "ETL_EXTRACT_002";

    /** La fuente de datos no contiene registros. */
    public static final String EXTRACT_EMPTY_SOURCE        = "ETL_EXTRACT_003";

    // Validation

    /** El esquema del registro no contiene una columna requerida. */
    public static final String VALIDATION_MISSING_COLUMN   = "ETL_VALID_001";

    /** Un campo obligatorio contiene un valor nulo. */
    public static final String VALIDATION_NULL_FIELD       = "ETL_VALID_002";

    /** El valor de un campo no cumple el formato esperado. */
    public static final String VALIDATION_INVALID_FORMAT   = "ETL_VALID_003";

    /** Se detectó una clave duplicada en el conjunto de registros. */
    public static final String VALIDATION_DUPLICATE_KEY    = "ETL_VALID_004";

    /** El valor de un campo no existe como referencia válida en el catálogo. */
    public static final String VALIDATION_CATALOG_REF      = "ETL_VALID_005";

    /** El porcentaje de errores supera el umbral de tolerancia configurado. */
    public static final String VALIDATION_THRESHOLD_BREACH = "ETL_VALID_006";

    // Transformation

    /** El tipo de un campo no coincide con el tipo esperado en el mapeo. */
    public static final String TRANSFORM_TYPE_MISMATCH     = "ETL_TRANS_001";

    /** El mapeo de transformación no pudo aplicarse al registro. */
    public static final String TRANSFORM_MAPPING_FAILURE   = "ETL_TRANS_002";

    // Loading

    /** Error al persistir los registros en la base de datos de destino. */
    public static final String LOAD_DB_FAILURE             = "ETL_LOAD_001";

    /** La validación en la tabla de staging falló antes de la carga final. */
    public static final String LOAD_STAGING_VALIDATION     = "ETL_LOAD_002";

    /** Falló la carga de uno de los lotes (chunks) de registros. */
    public static final String LOAD_CHUNK_FAILURE          = "ETL_LOAD_003";

    // Pipeline

    /** El pipeline solicitado no fue encontrado en el sistema. */
    public static final String PIPELINE_NOT_FOUND          = "ETL_PIPELINE_NOT_FOUND";

    /** El pipeline existe pero se encuentra en estado inactivo. */
    public static final String PIPELINE_INACTIVE           = "ETL_PIPELINE_INACTIVE";

    /** Ya existe una ejecución activa para el mismo pipeline. */
    public static final String EXECUTION_CONFLICT          = "ETL_EXEC_CONFLICT";

    /** Se agotaron todos los intentos de reintento del pipeline. */
    public static final String RETRY_EXHAUSTED             = "ETL_RETRY_EXHAUSTED";

    /** La ejecución fue solicitada fuera de la ventana de tiempo permitida. */
    public static final String EXECUTION_WINDOW_DENIED     = "ETL_EXEC_WINDOW_DENIED";

    private ErrorCodes() {}
}
