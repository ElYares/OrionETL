package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlExecutionErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaEtlExecutionErrorRepository extends JpaRepository<EtlExecutionErrorEntity, UUID> {

    List<EtlExecutionErrorEntity> findByExecutionId(UUID executionId);

    void deleteByExecutionId(UUID executionId);
}
