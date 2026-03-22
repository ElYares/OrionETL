package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlAuditRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaEtlAuditRecordRepository extends JpaRepository<EtlAuditRecordEntity, UUID> {

    List<EtlAuditRecordEntity> findByExecutionIdOrderByRecordedAtAsc(UUID executionId);
}
