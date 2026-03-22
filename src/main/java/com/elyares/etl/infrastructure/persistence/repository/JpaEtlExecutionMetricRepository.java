package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlExecutionMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaEtlExecutionMetricRepository extends JpaRepository<EtlExecutionMetricEntity, UUID> {

    List<EtlExecutionMetricEntity> findByExecutionId(UUID executionId);
}
