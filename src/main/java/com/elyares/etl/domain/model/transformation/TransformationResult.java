package com.elyares.etl.domain.model.transformation;

import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;

import java.util.List;

/**
 * Resultado agregado del paso de transformación.
 */
public final class TransformationResult {

    private final List<ProcessedRecord> processedRecords;
    private final List<RejectedRecord> rejectedRecords;

    private TransformationResult(List<ProcessedRecord> processedRecords, List<RejectedRecord> rejectedRecords) {
        this.processedRecords = processedRecords != null ? List.copyOf(processedRecords) : List.of();
        this.rejectedRecords = rejectedRecords != null ? List.copyOf(rejectedRecords) : List.of();
    }

    public static TransformationResult of(List<ProcessedRecord> processedRecords,
                                          List<RejectedRecord> rejectedRecords) {
        return new TransformationResult(processedRecords, rejectedRecords);
    }

    public List<ProcessedRecord> getProcessedRecords() { return processedRecords; }

    public List<RejectedRecord> getRejectedRecords() { return rejectedRecords; }
}
