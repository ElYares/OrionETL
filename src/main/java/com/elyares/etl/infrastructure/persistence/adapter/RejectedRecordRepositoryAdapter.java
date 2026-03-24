package com.elyares.etl.infrastructure.persistence.adapter;

import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineExecutionEntity;
import com.elyares.etl.infrastructure.persistence.entity.EtlRejectedRecordEntity;
import com.elyares.etl.infrastructure.persistence.mapper.PersistenceJsonMapper;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlPipelineExecutionRepository;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlRejectedRecordRepository;
import com.elyares.etl.shared.exception.EtlException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RejectedRecordRepositoryAdapter implements RejectedRecordRepository {

    private final JpaEtlRejectedRecordRepository repository;
    private final JpaEtlPipelineExecutionRepository executionRepository;
    private final PersistenceJsonMapper jsonMapper;

    public RejectedRecordRepositoryAdapter(JpaEtlRejectedRecordRepository repository,
                                           JpaEtlPipelineExecutionRepository executionRepository,
                                           PersistenceJsonMapper jsonMapper) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void saveAll(List<RejectedRecord> rejectedRecords, ExecutionId executionId) {
        if (rejectedRecords == null || rejectedRecords.isEmpty()) {
            return;
        }

        EtlPipelineExecutionEntity execution = executionRepository
            .findByExecutionRef(UUID.fromString(executionId.toString()))
            .orElseThrow(() -> new EtlException(
                "ETL_EXEC_NOT_FOUND",
                "Execution not found: " + executionId
            ));

        List<EtlRejectedRecordEntity> entities = rejectedRecords.stream()
            .map(record -> toEntity(record, execution))
            .toList();
        repository.saveAll(entities);
    }

    @Override
    public List<RejectedRecord> findByExecutionId(ExecutionId executionId) {
        return executionRepository.findByExecutionRef(UUID.fromString(executionId.toString()))
            .map(EtlPipelineExecutionEntity::getId)
            .map(repository::findByExecutionId)
            .orElse(List.of())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countByExecutionId(ExecutionId executionId) {
        return executionRepository.findByExecutionRef(UUID.fromString(executionId.toString()))
            .map(EtlPipelineExecutionEntity::getId)
            .map(repository::countByExecutionId)
            .orElse(0L);
    }

    private EtlRejectedRecordEntity toEntity(RejectedRecord rejectedRecord, EtlPipelineExecutionEntity execution) {
        return EtlRejectedRecordEntity.builder()
            .id(UUID.randomUUID())
            .executionId(execution.getId())
            .pipelineId(execution.getPipelineId())
            .stepName(rejectedRecord.getStepName())
            .sourceRowNumber(rejectedRecord.getOriginalRecord().getRowNumber())
            .rawData(jsonMapper.toJson(rejectedRecord.getOriginalRecord().getData()))
            .rejectionReason(rejectedRecord.getRejectionReason())
            .validationErrors(jsonMapper.toJson(rejectedRecord.getValidationErrors()))
            .rejectedAt(rejectedRecord.getRejectedAt())
            .build();
    }

    @SuppressWarnings("unchecked")
    private RejectedRecord toDomain(EtlRejectedRecordEntity entity) {
        Map<String, Object> rawData = jsonMapper.toMap(entity.getRawData());
        List<Map<String, Object>> rawErrors = entity.getValidationErrors() != null
            ? jsonMapper.fromJson(entity.getValidationErrors(), List.class)
            : List.of();

        List<ValidationError> validationErrors = rawErrors.stream()
            .map(error -> new ValidationError(
                (String) error.get("field"),
                error.get("value"),
                (String) error.get("rule"),
                (String) error.get("message"),
                error.get("severity") != null
                    ? com.elyares.etl.domain.enums.ErrorSeverity.valueOf(String.valueOf(error.get("severity")))
                    : com.elyares.etl.domain.enums.ErrorSeverity.ERROR
            ))
            .toList();

        RawRecord rawRecord = new RawRecord(
            entity.getSourceRowNumber(),
            rawData,
            "persisted-rejected-record",
            entity.getRejectedAt()
        );

        return new RejectedRecord(
            rawRecord,
            entity.getStepName(),
            entity.getRejectionReason(),
            validationErrors,
            entity.getRejectedAt()
        );
    }
}
