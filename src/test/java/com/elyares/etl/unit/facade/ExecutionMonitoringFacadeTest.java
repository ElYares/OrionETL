package com.elyares.etl.unit.facade;

import com.elyares.etl.application.dto.ExecutionMetricDto;
import com.elyares.etl.application.dto.RejectedRecordDto;
import com.elyares.etl.application.facade.ExecutionMonitoringFacade;
import com.elyares.etl.application.mapper.ExecutionMapperImpl;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.contract.RejectedRecordRepository;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.shared.constants.MetricKeys;
import com.elyares.etl.shared.response.PagedResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionMonitoringFacadeTest {

    @Test
    void getMetricsShouldCalculateStandardExecutionMetrics() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RejectedRecordRepository rejectedRecordRepository = mock(RejectedRecordRepository.class);

        ExecutionId executionId = ExecutionId.of("01234567-89ab-cdef-0123-456789abcdef");
        PipelineExecution execution = sampleExecution(executionId);

        when(executionRepository.findByExecutionId(executionId)).thenReturn(Optional.of(execution));

        ExecutionMonitoringFacade facade = new ExecutionMonitoringFacade(
            executionRepository,
            pipelineRepository,
            rejectedRecordRepository,
            new ExecutionMapperImpl()
        );

        List<ExecutionMetricDto> metrics = facade.getMetrics(executionId.toString());

        assertThat(metrics).extracting(ExecutionMetricDto::metricName).contains(
            MetricKeys.RECORDS_READ,
            MetricKeys.RECORDS_TRANSFORMED,
            MetricKeys.RECORDS_REJECTED,
            MetricKeys.RECORDS_LOADED,
            MetricKeys.ERROR_RATE_PCT,
            MetricKeys.DURATION_MS,
            MetricKeys.EXTRACT_DURATION_MS,
            MetricKeys.TRANSFORM_DURATION_MS,
            MetricKeys.LOAD_DURATION_MS
        );
        assertThat(metrics.stream().filter(metric -> MetricKeys.ERROR_RATE_PCT.equals(metric.metricName())).findFirst())
            .get()
            .extracting(ExecutionMetricDto::metricValue)
            .isEqualTo(new java.math.BigDecimal("10.00"));
    }

    @Test
    void getRejectedRecordsShouldReturnPagedResponse() {
        ExecutionRepository executionRepository = mock(ExecutionRepository.class);
        PipelineRepository pipelineRepository = mock(PipelineRepository.class);
        RejectedRecordRepository rejectedRecordRepository = mock(RejectedRecordRepository.class);

        ExecutionId executionId = ExecutionId.of("01234567-89ab-cdef-0123-456789abcdef");
        when(executionRepository.findByExecutionId(executionId)).thenReturn(Optional.of(sampleExecution(executionId)));
        when(rejectedRecordRepository.findByExecutionId(executionId)).thenReturn(List.of(
            new RejectedRecord(new RawRecord(10L, Map.of("transaction_id", "A-1"), "sales.csv", Instant.now()),
                "VALIDATE_SCHEMA", "missing field", List.of(), Instant.parse("2026-03-23T10:00:00Z")),
            new RejectedRecord(new RawRecord(11L, Map.of("transaction_id", "A-2"), "sales.csv", Instant.now()),
                "VALIDATE_BUSINESS", "invalid catalog", List.of(), Instant.parse("2026-03-23T10:00:01Z"))
        ));

        ExecutionMonitoringFacade facade = new ExecutionMonitoringFacade(
            executionRepository,
            pipelineRepository,
            rejectedRecordRepository,
            new ExecutionMapperImpl()
        );

        PagedResponse<RejectedRecordDto> page = facade.getRejectedRecords(executionId.toString(), 0, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).rowNumber()).isEqualTo(10L);
    }

    private PipelineExecution sampleExecution(ExecutionId executionId) {
        PipelineExecution execution = new PipelineExecution(
            UUID.randomUUID(),
            PipelineId.of("9b4d1aa8-e5f2-4e38-b3cc-aeb89d3ab001"),
            executionId,
            TriggerType.MANUAL,
            "tester"
        );
        execution.start();

        PipelineExecutionStep extract = new PipelineExecutionStep(UUID.randomUUID(), executionId, "EXTRACT", 2);
        extract.markRunning();
        extract.markSuccess(10);

        PipelineExecutionStep transform = new PipelineExecutionStep(UUID.randomUUID(), executionId, "TRANSFORM", 4);
        transform.markRunning();
        transform.markSuccess(9);

        PipelineExecutionStep load = new PipelineExecutionStep(UUID.randomUUID(), executionId, "LOAD", 6);
        load.markRunning();
        load.markSuccess(9);

        execution.addStep(extract);
        execution.addStep(transform);
        execution.addStep(load);
        execution.partialSuccess(
            com.elyares.etl.domain.valueobject.RecordCount.of(10),
            com.elyares.etl.domain.valueobject.RecordCount.of(9),
            com.elyares.etl.domain.valueobject.RecordCount.of(1),
            com.elyares.etl.domain.valueobject.RecordCount.of(9),
            "partial"
        );
        return execution;
    }
}
