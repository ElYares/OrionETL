package com.elyares.etl.application.usecase.loading;

import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;

import java.util.List;

/**
 * Caso de uso para persistir registros rechazados acumulados en la ejecución.
 */
public class PersistRejectedRecordsUseCase {

    private final RejectedRecordRepository rejectedRecordRepository;

    public PersistRejectedRecordsUseCase(RejectedRecordRepository rejectedRecordRepository) {
        this.rejectedRecordRepository = rejectedRecordRepository;
    }

    public void execute(List<RejectedRecord> rejectedRecords, ExecutionId executionId) {
        if (rejectedRecords == null || rejectedRecords.isEmpty()) {
            return;
        }
        rejectedRecordRepository.saveAll(rejectedRecords, executionId);
    }
}
