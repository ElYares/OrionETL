package com.elyares.etl.domain.model.target;

import java.util.List;

/**
 * Resultado de la validación de la tabla de staging antes de promover a final.
 */
public final class StagingValidationResult {

    private final boolean valid;
    private final long expectedRows;
    private final long actualRows;
    private final List<String> details;

    private StagingValidationResult(boolean valid, long expectedRows, long actualRows, List<String> details) {
        this.valid = valid;
        this.expectedRows = expectedRows;
        this.actualRows = actualRows;
        this.details = details != null ? List.copyOf(details) : List.of();
    }

    public static StagingValidationResult success(long expectedRows, long actualRows) {
        return new StagingValidationResult(true, expectedRows, actualRows, List.of());
    }

    public static StagingValidationResult failure(long expectedRows, long actualRows, List<String> details) {
        return new StagingValidationResult(false, expectedRows, actualRows, details);
    }

    public boolean isValid() { return valid; }

    public long getExpectedRows() { return expectedRows; }

    public long getActualRows() { return actualRows; }

    public List<String> getDetails() { return details; }
}
