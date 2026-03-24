package com.elyares.etl.application.facade;

import com.elyares.etl.application.dto.ExecutionMetricDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.dto.RejectedRecordDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.shared.constants.MetricKeys;
import com.elyares.etl.shared.exception.ExecutionNotFoundException;
import com.elyares.etl.shared.response.PagedResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fachada de consulta para monitoreo de ejecuciones.
 */
public class ExecutionMonitoringFacade {

    private final ExecutionRepository executionRepository;
    private final PipelineRepository pipelineRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final ExecutionMapper executionMapper;

    public ExecutionMonitoringFacade(ExecutionRepository executionRepository,
                                     PipelineRepository pipelineRepository,
                                     RejectedRecordRepository rejectedRecordRepository,
                                     ExecutionMapper executionMapper) {
        this.executionRepository = executionRepository;
        this.pipelineRepository = pipelineRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.executionMapper = executionMapper;
    }

    public PipelineExecutionDto getExecution(String executionId) {
        PipelineExecution execution = getExecutionDomain(executionId);
        String pipelineName = pipelineRepository.findById(execution.getPipelineId())
            .map(com.elyares.etl.domain.model.pipeline.Pipeline::getName)
            .orElse(execution.getPipelineId().toString());
        return executionMapper.toDto(execution, pipelineName);
    }

    public List<ExecutionMetricDto> getMetrics(String executionId) {
        PipelineExecution execution = getExecutionDomain(executionId);
        Instant recordedAt = execution.getFinishedAt() != null ? execution.getFinishedAt() : Instant.now();

        List<ExecutionMetricDto> metrics = new ArrayList<>();
        metrics.add(metric(executionId, MetricKeys.RECORDS_READ, execution.getTotalRead().value(), recordedAt));
        metrics.add(metric(executionId, MetricKeys.RECORDS_TRANSFORMED, execution.getTotalTransformed().value(), recordedAt));
        metrics.add(metric(executionId, MetricKeys.RECORDS_REJECTED, execution.getTotalRejected().value(), recordedAt));
        metrics.add(metric(executionId, MetricKeys.RECORDS_LOADED, execution.getTotalLoaded().value(), recordedAt));
        metrics.add(metric(executionId, MetricKeys.ERROR_RATE_PCT, calculateErrorRate(execution), recordedAt));

        long totalDurationMs = calculateDurationMs(execution.getStartedAt(), execution.getFinishedAt());
        if (totalDurationMs >= 0) {
            metrics.add(metric(executionId, MetricKeys.DURATION_MS, totalDurationMs, recordedAt));
        }

        addStepDurationMetric(metrics, executionId, execution.getSteps(), "EXTRACT", MetricKeys.EXTRACT_DURATION_MS);
        addStepDurationMetric(metrics, executionId, execution.getSteps(), "TRANSFORM", MetricKeys.TRANSFORM_DURATION_MS);
        addStepDurationMetric(metrics, executionId, execution.getSteps(), "LOAD", MetricKeys.LOAD_DURATION_MS);

        return metrics.stream()
            .sorted(Comparator.comparing(ExecutionMetricDto::metricName))
            .toList();
    }

    public PagedResponse<RejectedRecordDto> getRejectedRecords(String executionId, int page, int size) {
        getExecutionDomain(executionId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        List<RejectedRecordDto> all = rejectedRecordRepository.findByExecutionId(ExecutionId.of(executionId)).stream()
            .map(record -> toDto(executionId, record))
            .toList();

        int fromIndex = Math.min(safePage * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        return PagedResponse.of(all.subList(fromIndex, toIndex), safePage, safeSize, all.size());
    }

    private PipelineExecution getExecutionDomain(String executionId) {
        return executionRepository.findByExecutionId(ExecutionId.of(executionId))
            .orElseThrow(() -> new ExecutionNotFoundException(executionId));
    }

    private ExecutionMetricDto metric(String executionId, String metricName, long value, Instant recordedAt) {
        return new ExecutionMetricDto(executionId, metricName, BigDecimal.valueOf(value), recordedAt);
    }

    private ExecutionMetricDto metric(String executionId, String metricName, BigDecimal value, Instant recordedAt) {
        return new ExecutionMetricDto(executionId, metricName, value, recordedAt);
    }

    private BigDecimal calculateErrorRate(PipelineExecution execution) {
        long totalRead = execution.getTotalRead().value();
        if (totalRead <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(execution.getTotalRejected().value())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalRead), 2, RoundingMode.HALF_UP);
    }

    private void addStepDurationMetric(List<ExecutionMetricDto> metrics,
                                       String executionId,
                                       List<PipelineExecutionStep> steps,
                                       String stepName,
                                       String metricKey) {
        steps.stream()
            .filter(step -> stepName.equals(step.getStepName()))
            .findFirst()
            .ifPresent(step -> {
                long durationMs = calculateDurationMs(step.getStartedAt(), step.getFinishedAt());
                if (durationMs >= 0) {
                    metrics.add(metric(
                        executionId,
                        metricKey,
                        durationMs,
                        step.getFinishedAt() != null ? step.getFinishedAt() : Instant.now()
                    ));
                }
            });
    }

    private long calculateDurationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null) {
            return -1L;
        }
        Instant end = finishedAt != null ? finishedAt : Instant.now();
        return Duration.between(startedAt, end).toMillis();
    }

    private RejectedRecordDto toDto(String executionId, RejectedRecord record) {
        return new RejectedRecordDto(
            executionId,
            record.getStepName(),
            record.getOriginalRecord().getRowNumber(),
            record.getOriginalRecord().getData(),
            record.getRejectionReason(),
            record.getRejectedAt()
        );
    }
}
