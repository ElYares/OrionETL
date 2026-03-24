package com.elyares.etl.infrastructure.monitoring;

import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlPipelineExecutionRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicador de salud del motor ETL para Actuator.
 */
@Component("etlEngine")
public class ETLEngineHealthIndicator implements HealthIndicator {

    private final PipelineRepository pipelineRepository;
    private final JpaEtlPipelineExecutionRepository executionRepository;

    public ETLEngineHealthIndicator(PipelineRepository pipelineRepository,
                                    JpaEtlPipelineExecutionRepository executionRepository) {
        this.pipelineRepository = pipelineRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    public Health health() {
        var pipelines = pipelineRepository.findAll();
        long activeExecutions = executionRepository.countByStatusIn(List.of(
            ExecutionStatus.RUNNING.name(),
            ExecutionStatus.RETRYING.name()
        ));

        Map<String, Object> lastSuccessfulExecutionByPipeline = new LinkedHashMap<>();
        pipelines.forEach(pipeline -> lastSuccessfulExecutionByPipeline.put(
            pipeline.getName(),
            executionRepository.findFirstByPipelineIdAndStatusInOrderByFinishedAtDesc(
                    UUID.fromString(pipeline.getId().toString()),
                    List.of(ExecutionStatus.SUCCESS.name(), ExecutionStatus.PARTIAL.name())
                )
                .map(entity -> entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null)
                .orElse(null)
        ));

        return Health.up()
            .withDetail("activeExecutions", activeExecutions)
            .withDetail("registeredPipelines", pipelines.size())
            .withDetail("lastSuccessfulExecutionByPipeline", lastSuccessfulExecutionByPipeline)
            .build();
    }
}
