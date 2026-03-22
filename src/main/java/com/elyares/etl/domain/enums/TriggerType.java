package com.elyares.etl.domain.enums;

/**
 * Indica el mecanismo que originó el inicio de una ejecución de pipeline ETL.
 *
 * <p>El tipo de disparador queda registrado en la auditoría de la ejecución y
 * permite distinguir ejecuciones interactivas de ejecuciones automáticas o de recuperación.</p>
 */
public enum TriggerType {

    /** La ejecución fue iniciada manualmente por un operador a través de la interfaz de usuario. */
    MANUAL,

    /** La ejecución fue iniciada de forma automática por un planificador de tareas (scheduler). */
    SCHEDULED,

    /** La ejecución fue iniciada como reintento automático tras un fallo previo. */
    RETRY,

    /** La ejecución fue iniciada desde la interfaz de línea de comandos (CLI). */
    CLI
}
