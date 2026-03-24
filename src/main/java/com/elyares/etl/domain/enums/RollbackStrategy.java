package com.elyares.etl.domain.enums;

/**
 * Estrategias de rollback disponibles para revertir una promoción fallida.
 */
public enum RollbackStrategy {
    NONE,
    DELETE_BY_EXECUTION
}
