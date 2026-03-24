package com.elyares.etl.interfaces.rest.controller;

import com.elyares.etl.application.dto.ExecutionMetricDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.dto.RejectedRecordDto;
import com.elyares.etl.application.facade.ExecutionMonitoringFacade;
import com.elyares.etl.shared.response.ApiResponse;
import com.elyares.etl.shared.response.PagedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de monitoreo para ejecuciones ETL.
 */
@Validated
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ExecutionMonitoringFacade executionMonitoringFacade;

    public ExecutionController(ExecutionMonitoringFacade executionMonitoringFacade) {
        this.executionMonitoringFacade = executionMonitoringFacade;
    }

    @GetMapping("/{executionId}")
    public ApiResponse<PipelineExecutionDto> getExecution(@PathVariable String executionId) {
        return ApiResponse.ok(executionMonitoringFacade.getExecution(executionId));
    }

    @GetMapping("/{executionId}/metrics")
    public ApiResponse<List<ExecutionMetricDto>> getMetrics(@PathVariable String executionId) {
        return ApiResponse.ok(executionMonitoringFacade.getMetrics(executionId));
    }

    @GetMapping("/{executionId}/rejected")
    public ApiResponse<PagedResponse<RejectedRecordDto>> getRejected(@PathVariable String executionId,
                                                                     @RequestParam(defaultValue = "0")
                                                                     @Min(value = 0, message = "page must be at least 0")
                                                                     int page,
                                                                     @RequestParam(defaultValue = "50")
                                                                     @Min(value = 1, message = "size must be at least 1")
                                                                     @Max(value = 200, message = "size must be at most 200")
                                                                     int size) {
        return ApiResponse.ok(executionMonitoringFacade.getRejectedRecords(executionId, page, size));
    }
}
