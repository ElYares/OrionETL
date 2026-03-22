package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlRejectedRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaEtlRejectedRecordRepository extends JpaRepository<EtlRejectedRecordEntity, UUID> {

    List<EtlRejectedRecordEntity> findByExecutionId(UUID executionId);

    long countByExecutionId(UUID executionId);
}
