package com.elyares.etl.domain.enums;

/**
 * Representa el estado de ciclo de vida de un pipeline ETL.
 *
 * <p>Define si un pipeline está disponible para su ejecución, temporalmente desactivado
 * o marcado como obsoleto y no apto para nuevas ejecuciones.</p>
 */
public enum PipelineStatus {

    /** El pipeline está habilitado y puede ser ejecutado. */
    ACTIVE,

    /** El pipeline está deshabilitado temporalmente y no se ejecutará. */
    INACTIVE,

    /** El pipeline ha quedado obsoleto y no debe utilizarse en nuevas ejecuciones. */
    DEPRECATED
}
