package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando se intenta iniciar una ejecución de pipeline que ya
 * tiene una ejecución activa en curso.
 *
 * <p>Garantiza la exclusión mutua de ejecuciones concurrentes para el mismo
 * pipeline. Utiliza el código de error {@code ETL_EXEC_CONFLICT} e incluye el
 * identificador del pipeline en el mensaje para facilitar el diagnóstico.</p>
 */
public class ExecutionConflictException extends EtlException {

    /**
     * Construye la excepción con el código {@code ETL_EXEC_CONFLICT} y un
     * mensaje que incluye el identificador del pipeline en conflicto.
     *
     * @param pipelineId identificador del pipeline que ya tiene una ejecución activa
     */
    public ExecutionConflictException(String pipelineId) {
        super("ETL_EXEC_CONFLICT", "Pipeline already has an active execution: " + pipelineId);
    }

    /**
     * Construye la excepción con un código de error y un mensaje personalizados.
     *
     * @param errorCode código de error canónico
     * @param message   descripción del conflicto
     */
    public ExecutionConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
