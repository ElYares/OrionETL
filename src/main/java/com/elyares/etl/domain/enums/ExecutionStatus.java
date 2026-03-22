package com.elyares.etl.domain.enums;

/**
 * Representa el estado de una ejecución de pipeline ETL a lo largo de su ciclo de vida.
 *
 * <p>Los estados se agrupan en tres categorías funcionales:
 * <ul>
 *   <li><b>Activos:</b> {@link #RUNNING}, {@link #RETRYING} — la ejecución está en curso.</li>
 *   <li><b>Terminales:</b> {@link #SUCCESS}, {@link #FAILED}, {@link #PARTIAL}, {@link #SKIPPED}
 *       — la ejecución ha finalizado y no cambiará de estado.</li>
 *   <li><b>Pendiente:</b> {@link #PENDING} — la ejecución aún no ha comenzado.</li>
 * </ul>
 * </p>
 */
public enum ExecutionStatus {

    /** La ejecución está en cola y aún no ha comenzado a procesarse. */
    PENDING,

    /** La ejecución está en curso activamente. */
    RUNNING,

    /** La ejecución finalizó correctamente procesando todos los registros. */
    SUCCESS,

    /** La ejecución terminó con un error irrecuperable. */
    FAILED,

    /** La ejecución finalizó pero con errores parciales; algunos registros no fueron procesados. */
    PARTIAL,

    /** La ejecución fue omitida intencionalmente sin procesar registros. */
    SKIPPED,

    /** La ejecución está siendo reintentada tras un fallo previo. */
    RETRYING;

    /**
     * Indica si el estado es terminal, es decir, si la ejecución ha finalizado
     * y no se producirán más transiciones de estado.
     *
     * @return {@code true} si el estado es {@code SUCCESS}, {@code FAILED},
     *         {@code PARTIAL} o {@code SKIPPED}; {@code false} en caso contrario.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL || this == SKIPPED;
    }

    /**
     * Indica si el estado refleja una ejecución actualmente en proceso.
     *
     * @return {@code true} si el estado es {@code RUNNING} o {@code RETRYING};
     *         {@code false} en caso contrario.
     */
    public boolean isActive() {
        return this == RUNNING || this == RETRYING;
    }

    /**
     * Indica si la ejecución finalizó de forma completamente exitosa.
     *
     * @return {@code true} únicamente si el estado es {@code SUCCESS};
     *         {@code false} en caso contrario.
     */
    public boolean isSuccessful() {
        return this == SUCCESS;
    }
}
