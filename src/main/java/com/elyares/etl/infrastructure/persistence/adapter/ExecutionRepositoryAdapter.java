package com.elyares.etl.infrastructure.persistence.adapter;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.StepStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.infrastructure.persistence.entity.EtlExecutionErrorEntity;
import com.elyares.etl.infrastructure.persistence.entity.EtlExecutionStepEntity;
import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineExecutionEntity;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlExecutionErrorRepository;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlExecutionStepRepository;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlPipelineExecutionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExecutionRepositoryAdapter implements ExecutionRepository {

    private final JpaEtlPipelineExecutionRepository executionRepository;
    private final JpaEtlExecutionStepRepository stepRepository;
    private final JpaEtlExecutionErrorRepository errorRepository;

    public ExecutionRepositoryAdapter(JpaEtlPipelineExecutionRepository executionRepository,
                                      JpaEtlExecutionStepRepository stepRepository,
                                      JpaEtlExecutionErrorRepository errorRepository) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.errorRepository = errorRepository;
    }

    @Override
    @Transactional
    public PipelineExecution save(PipelineExecution execution) {
        EtlPipelineExecutionEntity savedExecution = executionRepository.save(toEntity(execution));

        stepRepository.deleteByExecutionId(savedExecution.getId());
        List<EtlExecutionStepEntity> steps = execution.getSteps().stream()
            .map(step -> toStepEntity(step, savedExecution.getId()))
            .toList();
        if (!steps.isEmpty()) {
            stepRepository.saveAll(steps);
        }

        errorRepository.deleteByExecutionId(savedExecution.getId());
        List<EtlExecutionErrorEntity> errors = execution.getErrors().stream()
            .map(error -> toErrorEntity(error, savedExecution.getId()))
            .toList();
        if (!errors.isEmpty()) {
            errorRepository.saveAll(errors);
        }

        return hydrateDomain(savedExecution);
    }

    @Override
    public Optional<PipelineExecution> findByExecutionId(ExecutionId executionId) {
        return executionRepository.findByExecutionRef(UUID.fromString(executionId.toString()))
            .map(this::hydrateDomain);
    }

    @Override
    public Optional<PipelineExecution> findActiveByPipelineId(PipelineId pipelineId) {
        return executionRepository.findByPipelineIdAndStatusIn(
                UUID.fromString(pipelineId.toString()),
                List.of(ExecutionStatus.RUNNING.name(), ExecutionStatus.RETRYING.name())
            ).stream()
            .findFirst()
            .map(this::hydrateDomain);
    }

    @Override
    public List<PipelineExecution> findByPipelineId(PipelineId pipelineId, int limit) {
        int boundedLimit = Math.max(1, limit);
        return executionRepository.findByPipelineIdOrderByCreatedAtDesc(
                UUID.fromString(pipelineId.toString()),
                PageRequest.of(0, boundedLimit)
            ).stream()
            .map(this::hydrateDomain)
            .toList();
    }

    private EtlPipelineExecutionEntity toEntity(PipelineExecution execution) {
        return EtlPipelineExecutionEntity.builder()
            .id(execution.getId())
            .pipelineId(UUID.fromString(execution.getPipelineId().toString()))
            .executionRef(UUID.fromString(execution.getExecutionId().toString()))
            .status(execution.getStatus().name())
            .triggerType(execution.getTriggerType().name())
            .triggeredBy(execution.getTriggeredBy())
            .startedAt(execution.getStartedAt())
            .finishedAt(execution.getFinishedAt())
            .totalRead(execution.getTotalRead().value())
            .totalTransformed(execution.getTotalTransformed().value())
            .totalRejected(execution.getTotalRejected().value())
            .totalLoaded(execution.getTotalLoaded().value())
            .errorSummary(execution.getErrorSummary())
            .retryCount(execution.getRetryCount())
            .parentExecutionId(execution.getParentExecutionId())
            .createdAt(execution.getCreatedAt())
            .build();
    }

    private PipelineExecution hydrateDomain(EtlPipelineExecutionEntity entity) {
        PipelineExecution execution = new PipelineExecution(
            entity.getId(),
            PipelineId.of(entity.getPipelineId().toString()),
            ExecutionId.of(entity.getExecutionRef().toString()),
            TriggerType.valueOf(entity.getTriggerType()),
            entity.getTriggeredBy()
        );

        for (int i = 0; i < entity.getRetryCount(); i++) {
            execution.incrementRetryCount();
        }
        execution.setParentExecutionId(entity.getParentExecutionId());

        applyStatusAndCounters(execution, entity);

        stepRepository.findByExecutionIdOrderByStepOrderAsc(entity.getId()).stream()
            .map(step -> toDomainStep(step, execution.getExecutionId()))
            .forEach(execution::addStep);

        errorRepository.findByExecutionId(entity.getId()).stream()
            .map(error -> toDomainError(error, execution.getExecutionId()))
            .forEach(execution::addError);

        return execution;
    }

    private void applyStatusAndCounters(PipelineExecution execution, EtlPipelineExecutionEntity entity) {
        ExecutionStatus status = ExecutionStatus.valueOf(entity.getStatus());
        switch (status) {
            case PENDING -> {
                // already pending by constructor
            }
            case RUNNING -> execution.start();
            case RETRYING -> execution.setStatus(ExecutionStatus.RETRYING);
            case SUCCESS -> execution.complete(
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalRead()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalTransformed()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalRejected()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalLoaded())
            );
            case FAILED -> execution.fail(entity.getErrorSummary());
            case PARTIAL -> execution.partialSuccess(
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalRead()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalTransformed()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalRejected()),
                com.elyares.etl.domain.valueobject.RecordCount.of(entity.getTotalLoaded()),
                entity.getErrorSummary()
            );
            case SKIPPED -> execution.setStatus(ExecutionStatus.SKIPPED);
        }
    }

    private EtlExecutionStepEntity toStepEntity(PipelineExecutionStep step, UUID executionPkId) {
        return EtlExecutionStepEntity.builder()
            .id(step.getId())
            .executionId(executionPkId)
            .stepName(step.getStepName())
            .stepOrder(step.getStepOrder())
            .status(step.getStatus().name())
            .startedAt(step.getStartedAt())
            .finishedAt(step.getFinishedAt())
            .recordsProcessed(step.getRecordsProcessed())
            .errorDetail(step.getErrorDetail())
            .createdAt(step.getStartedAt() != null ? step.getStartedAt() : Instant.now())
            .build();
    }

    private PipelineExecutionStep toDomainStep(EtlExecutionStepEntity entity, ExecutionId executionId) {
        PipelineExecutionStep step = new PipelineExecutionStep(
            entity.getId(),
            executionId,
            entity.getStepName(),
            entity.getStepOrder()
        );

        StepStatus stepStatus = StepStatus.valueOf(entity.getStatus());
        switch (stepStatus) {
            case PENDING -> {
                // default
            }
            case RUNNING -> step.markRunning();
            case SUCCESS -> step.markSuccess(entity.getRecordsProcessed());
            case FAILED -> step.markFailed(entity.getErrorDetail() != null ? entity.getErrorDetail() : "Step failed");
            case SKIPPED -> step.markSkipped();
        }

        return step;
    }

    private EtlExecutionErrorEntity toErrorEntity(ExecutionError error, UUID executionPkId) {
        return EtlExecutionErrorEntity.builder()
            .id(error.getId())
            .executionId(executionPkId)
            .stepName(error.getStepName())
            .errorType(error.getErrorType().name())
            .errorCode(error.getErrorCode())
            .message(error.getMessage())
            .stackTrace(error.getStackTrace())
            .recordReference(error.getRecordReference())
            .createdAt(error.getCreatedAt())
            .build();
    }

    private ExecutionError toDomainError(EtlExecutionErrorEntity entity, ExecutionId executionId) {
        return new ExecutionError(
            entity.getId(),
            executionId,
            entity.getStepName(),
            ErrorType.valueOf(entity.getErrorType()),
            ErrorSeverity.ERROR,
            entity.getErrorCode(),
            entity.getMessage(),
            entity.getStackTrace(),
            entity.getRecordReference()
        );
    }
}
