package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaEtlPipelineRepository extends JpaRepository<EtlPipelineEntity, UUID> {

    Optional<EtlPipelineEntity> findByName(String name);

    List<EtlPipelineEntity> findByStatus(String status);
}
