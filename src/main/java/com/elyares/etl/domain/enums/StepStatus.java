package com.elyares.etl.domain.enums;

/**
 * Representa el estado de un paso individual dentro de una ejecución de pipeline ETL.
 *
 * <p>Cada paso (extracción, transformación, carga, validación, etc.) transita por estos
 * estados durante su ciclo de vida. Un paso en estado terminal no admite nuevas transiciones.</p>
 */
public enum StepStatus {

    /** El paso está en cola y aún no ha comenzado su procesamiento. */
    PENDING,

    /** El paso está siendo ejecutado actualmente. */
    RUNNING,

    /** El paso finalizó correctamente. */
    SUCCESS,

    /** El paso terminó con un error. */
    FAILED,

    /** El paso fue omitido sin ejecutarse, generalmente por condición de negocio. */
    SKIPPED;

    /**
     * Indica si el estado es terminal, es decir, si el paso ha concluido
     * y no se producirán más transiciones de estado.
     *
     * @return {@code true} si el estado es {@code SUCCESS}, {@code FAILED}
     *         o {@code SKIPPED}; {@code false} en caso contrario.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED;
    }
}
