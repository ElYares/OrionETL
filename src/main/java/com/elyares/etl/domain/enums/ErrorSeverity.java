package com.elyares.etl.domain.enums;

/**
 * Define el nivel de severidad de un error registrado durante la ejecución de un pipeline ETL.
 *
 * <p>La severidad determina el impacto del error sobre el resultado final de la ejecución
 * y la urgencia de la respuesta operativa requerida. Únicamente la severidad
 * {@link #CRITICAL} impide que la ejecución se considere exitosa.</p>
 */
public enum ErrorSeverity {

    /** Mensaje informativo que no representa un problema; solo sirve como traza diagnóstica. */
    INFO,

    /** Condición anómala que no detiene el procesamiento pero requiere atención. */
    WARNING,

    /** Fallo en el procesamiento de uno o varios registros que no compromete la ejecución global. */
    ERROR,

    /** Fallo grave que invalida el resultado de la ejecución e impide considerarla exitosa. */
    CRITICAL;

    /**
     * Indica si esta severidad bloquea la consideración de éxito de la ejecución.
     *
     * <p>Solo la severidad {@code CRITICAL} tiene este efecto; el resto de niveles
     * no impiden que la ejecución sea marcada como exitosa o parcial.</p>
     *
     * @return {@code true} si la severidad es {@code CRITICAL}; {@code false} en caso contrario.
     */
    public boolean blockSuccess() {
        return this == CRITICAL;
    }
}
