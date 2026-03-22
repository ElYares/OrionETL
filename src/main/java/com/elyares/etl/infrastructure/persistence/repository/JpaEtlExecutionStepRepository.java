package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlExecutionStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaEtlExecutionStepRepository extends JpaRepository<EtlExecutionStepEntity, UUID> {

    List<EtlExecutionStepEntity> findByExecutionIdOrderByStepOrderAsc(UUID executionId);

    void deleteByExecutionId(UUID executionId);
}
