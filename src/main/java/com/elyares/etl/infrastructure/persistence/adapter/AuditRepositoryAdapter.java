package com.elyares.etl.infrastructure.persistence.adapter;

import com.elyares.etl.domain.contract.AuditRepository;
import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.infrastructure.persistence.entity.EtlAuditRecordEntity;
import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineExecutionEntity;
import com.elyares.etl.infrastructure.persistence.mapper.PersistenceJsonMapper;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlAuditRecordRepository;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlPipelineExecutionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditRepositoryAdapter implements AuditRepository {

    private final JpaEtlAuditRecordRepository repository;
    private final JpaEtlPipelineExecutionRepository executionRepository;
    private final PersistenceJsonMapper jsonMapper;

    public AuditRepositoryAdapter(JpaEtlAuditRecordRepository repository,
                                  JpaEtlPipelineExecutionRepository executionRepository,
                                  PersistenceJsonMapper jsonMapper) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public AuditRecord save(AuditRecord record) {
        EtlAuditRecordEntity saved = repository.save(toEntity(record));
        return toDomain(saved);
    }

    @Override
    public List<AuditRecord> findByExecutionId(ExecutionId executionId) {
        Optional<EtlPipelineExecutionEntity> execution = executionRepository
            .findByExecutionRef(UUID.fromString(executionId.toString()));
        if (execution.isEmpty()) {
            return List.of();
        }
        return repository.findByExecutionIdOrderByRecordedAtAsc(execution.orElseThrow().getId()).stream()
            .map(this::toDomain)
            .toList();
    }

    private EtlAuditRecordEntity toEntity(AuditRecord record) {
        UUID executionPkId = null;
        if (record.getExecutionId() != null) {
            executionPkId = executionRepository
                .findByExecutionRef(UUID.fromString(record.getExecutionId().toString()))
                .map(EtlPipelineExecutionEntity::getId)
                .orElse(null);
        }
        return EtlAuditRecordEntity.builder()
            .id(record.getId())
            .executionId(executionPkId)
            .pipelineId(record.getPipelineId() != null ? UUID.fromString(record.getPipelineId().toString()) : null)
            .action(record.getAction())
            .actorType(record.getActorType())
            .details(jsonMapper.toJson(record.getDetails()))
            .recordedAt(record.getRecordedAt())
            .build();
    }

    private AuditRecord toDomain(EtlAuditRecordEntity entity) {
        Map<String, Object> details = entity.getDetails() != null ? jsonMapper.toMap(entity.getDetails()) : Map.of();
        ExecutionId domainExecutionId = null;
        if (entity.getExecutionId() != null) {
            domainExecutionId = executionRepository.findById(entity.getExecutionId())
                .map(exec -> ExecutionId.of(exec.getExecutionRef().toString()))
                .orElse(ExecutionId.of(entity.getExecutionId().toString()));
        }
        return new AuditRecord(
            entity.getId(),
            domainExecutionId,
            entity.getPipelineId() != null ? PipelineId.of(entity.getPipelineId().toString()) : null,
            entity.getAction(),
            entity.getActorType(),
            details
        );
    }
}
