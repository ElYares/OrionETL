package com.elyares.etl.interfaces.rest.controller;

import com.elyares.etl.application.dto.ExecutionAcceptedDto;
import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.facade.PipelineExecutionFacade;
import com.elyares.etl.application.usecase.execution.ListExecutionsUseCase;
import com.elyares.etl.application.usecase.pipeline.GetPipelineUseCase;
import com.elyares.etl.application.usecase.pipeline.ListPipelinesUseCase;
import com.elyares.etl.interfaces.rest.request.ExecutePipelineRequest;
import com.elyares.etl.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST para consulta y disparo manual de pipelines.
 */
@Validated
@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

    private final ListPipelinesUseCase listPipelinesUseCase;
    private final GetPipelineUseCase getPipelineUseCase;
    private final ListExecutionsUseCase listExecutionsUseCase;
    private final PipelineExecutionFacade pipelineExecutionFacade;

    public PipelineController(ListPipelinesUseCase listPipelinesUseCase,
                              GetPipelineUseCase getPipelineUseCase,
                              ListExecutionsUseCase listExecutionsUseCase,
                              PipelineExecutionFacade pipelineExecutionFacade) {
        this.listPipelinesUseCase = listPipelinesUseCase;
        this.getPipelineUseCase = getPipelineUseCase;
        this.listExecutionsUseCase = listExecutionsUseCase;
        this.pipelineExecutionFacade = pipelineExecutionFacade;
    }

    @GetMapping
    public ApiResponse<List<PipelineDto>> listPipelines() {
        return ApiResponse.ok(listPipelinesUseCase.listActive());
    }

    @GetMapping("/{pipelineRef}")
    public ApiResponse<PipelineDto> getPipeline(@PathVariable String pipelineRef) {
        return ApiResponse.ok(getPipelineUseCase.getByReference(pipelineRef));
    }

    @GetMapping("/{pipelineRef}/executions")
    public ApiResponse<List<PipelineExecutionDto>> listExecutions(@PathVariable String pipelineRef,
                                                                  @RequestParam(defaultValue = "20")
                                                                  @Min(value = 1, message = "limit must be at least 1")
                                                                  @Max(value = 200, message = "limit must be at most 200")
                                                                  int limit) {
        return ApiResponse.ok(listExecutionsUseCase.execute(pipelineRef, limit));
    }

    @PostMapping("/{pipelineRef}/execute")
    public ResponseEntity<ApiResponse<ExecutionAcceptedDto>> execute(@PathVariable String pipelineRef,
                                                                     @Valid @RequestBody ExecutePipelineRequest request) {
        ExecutionAcceptedDto response = pipelineExecutionFacade.trigger(
            pipelineRef,
            request.triggeredBy(),
            request.parameters()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }
}
