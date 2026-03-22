package com.elyares.etl.application.usecase.loading;

import com.elyares.etl.domain.contract.AuditRepository;
import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;

import java.util.Map;

/**
 * Caso de uso para registrar auditoría de ejecución ETL.
 */
public class RegisterAuditUseCase {

    private final AuditRepository auditRepository;

    public RegisterAuditUseCase(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public AuditRecord execute(PipelineExecution execution, Pipeline pipeline, Map<String, Object> details) {
        AuditRecord record = AuditRecord.of(
            execution.getExecutionId(),
            pipeline.getId(),
            "EXECUTION_COMPLETED",
            details
        );
        return auditRepository.save(record);
    }
}
