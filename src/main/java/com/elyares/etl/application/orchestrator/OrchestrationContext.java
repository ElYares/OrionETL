package com.elyares.etl.application.orchestrator;

import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado en memoria de la ejecución orquestada para intercambio entre pasos.
 */
final class OrchestrationContext {

    private List<RawRecord> rawRecords = List.of();
    private List<ProcessedRecord> processedRecords = List.of();
    private final List<RejectedRecord> rejectedRecords = new ArrayList<>();

    private long totalRead;
    private long totalTransformed;
    private long totalRejected;
    private long totalLoaded;
    private boolean rejectedRecordsPersisted;

    List<RawRecord> getRawRecords() {
        return rawRecords;
    }

    void setRawRecords(List<RawRecord> rawRecords) {
        this.rawRecords = rawRecords != null ? List.copyOf(rawRecords) : List.of();
    }

    List<ProcessedRecord> getProcessedRecords() {
        return processedRecords;
    }

    void setProcessedRecords(List<ProcessedRecord> processedRecords) {
        this.processedRecords = processedRecords != null ? List.copyOf(processedRecords) : List.of();
    }

    List<RejectedRecord> getRejectedRecords() {
        return rejectedRecords;
    }

    long getTotalRead() {
        return totalRead;
    }

    void setTotalRead(long totalRead) {
        this.totalRead = totalRead;
    }

    long getTotalTransformed() {
        return totalTransformed;
    }

    void setTotalTransformed(long totalTransformed) {
        this.totalTransformed = totalTransformed;
    }

    long getTotalRejected() {
        return totalRejected;
    }

    void addRejected(long rejectedDelta) {
        this.totalRejected += rejectedDelta;
    }

    void addRejectedRecords(List<RejectedRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        this.rejectedRecords.addAll(records);
        this.totalRejected += records.size();
    }

    long getTotalLoaded() {
        return totalLoaded;
    }

    void setTotalLoaded(long totalLoaded) {
        this.totalLoaded = totalLoaded;
    }

    boolean isRejectedRecordsPersisted() {
        return rejectedRecordsPersisted;
    }

    void markRejectedRecordsPersisted() {
        this.rejectedRecordsPersisted = true;
    }
}
