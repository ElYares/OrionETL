package com.elyares.etl.infrastructure.persistence.repository;

import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineExecutionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaEtlPipelineExecutionRepository extends JpaRepository<EtlPipelineExecutionEntity, UUID> {

    Optional<EtlPipelineExecutionEntity> findByExecutionRef(UUID executionRef);

    List<EtlPipelineExecutionEntity> findByPipelineIdAndStatusIn(UUID pipelineId, List<String> statuses);

    List<EtlPipelineExecutionEntity> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId, Pageable pageable);

    Optional<EtlPipelineExecutionEntity> findFirstByPipelineIdAndStatusInOrderByFinishedAtDesc(UUID pipelineId, List<String> statuses);

    long countByStatusIn(List<String> statuses);
}
