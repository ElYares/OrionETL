package com.elyares.etl.application.usecase.execution;

import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.mapper.ExecutionMapper;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.shared.exception.PipelineNotFoundException;

import java.util.List;

/**
 * Caso de uso para listar ejecuciones históricas de un pipeline.
 */
public class ListExecutionsUseCase {

    private final ExecutionRepository executionRepository;
    private final PipelineRepository pipelineRepository;
    private final ExecutionMapper executionMapper;

    public ListExecutionsUseCase(ExecutionRepository executionRepository,
                                 PipelineRepository pipelineRepository,
                                 ExecutionMapper executionMapper) {
        this.executionRepository = executionRepository;
        this.pipelineRepository = pipelineRepository;
        this.executionMapper = executionMapper;
    }

    public List<PipelineExecutionDto> execute(String pipelineId, int limit) {
        PipelineId id = PipelineId.of(pipelineId);
        Pipeline pipeline = pipelineRepository.findById(id)
            .orElseThrow(() -> new PipelineNotFoundException(pipelineId));

        return executionRepository.findByPipelineId(id, limit).stream()
            .map(execution -> executionMapper.toDto(execution, pipeline.getName()))
            .toList();
    }
}
