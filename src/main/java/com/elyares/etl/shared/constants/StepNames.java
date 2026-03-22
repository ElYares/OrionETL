package com.elyares.etl.shared.constants;

/**
 * Constantes que identifican los pasos del pipeline ETL.
 *
 * <p>Cada constante representa el nombre canónico de una etapa dentro del
 * ciclo de ejecución de un pipeline. Estos valores se utilizan en el MDC de
 * logging, en métricas y en el trazado de auditoría para identificar en qué
 * fase del proceso se produce un evento.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class StepNames {

    /** Paso de inicialización del pipeline. */
    public static final String INIT              = "INIT";

    /** Paso de extracción de datos desde la fuente configurada. */
    public static final String EXTRACT           = "EXTRACT";

    /** Paso de validación estructural del esquema de los registros extraídos. */
    public static final String VALIDATE_SCHEMA   = "VALIDATE_SCHEMA";

    /** Paso de transformación y mapeo de los registros al modelo de destino. */
    public static final String TRANSFORM         = "TRANSFORM";

    /** Paso de validación de reglas de negocio sobre los registros transformados. */
    public static final String VALIDATE_BUSINESS = "VALIDATE_BUSINESS";

    /** Paso de carga de los registros validados en el destino configurado. */
    public static final String LOAD              = "LOAD";

    /** Paso de cierre y liberación de recursos del pipeline. */
    public static final String CLOSE             = "CLOSE";

    /** Paso de registro de auditoría de la ejecución. */
    public static final String AUDIT             = "AUDIT";

    private StepNames() {}
}
