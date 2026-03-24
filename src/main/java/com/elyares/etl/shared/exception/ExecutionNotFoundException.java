package com.elyares.etl.shared.exception;

/**
 * Excepcion lanzada cuando se solicita una ejecucion que no existe en el sistema.
 */
public class ExecutionNotFoundException extends EtlException {

    public ExecutionNotFoundException(String executionId) {
        super("ETL_EXEC_NOT_FOUND", "Execution not found: " + executionId);
    }
}
