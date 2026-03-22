package com.elyares.etl.application.usecase.pipeline;

import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.application.mapper.PipelineMapper;
import com.elyares.etl.domain.contract.PipelineRepository;

import java.util.List;

/**
 * Caso de uso para listar pipelines del sistema.
 */
public class ListPipelinesUseCase {

    private final PipelineRepository pipelineRepository;
    private final PipelineMapper pipelineMapper;

    public ListPipelinesUseCase(PipelineRepository pipelineRepository, PipelineMapper pipelineMapper) {
        this.pipelineRepository = pipelineRepository;
        this.pipelineMapper = pipelineMapper;
    }

    public List<PipelineDto> listAll() {
        return pipelineRepository.findAll().stream().map(pipelineMapper::toDto).toList();
    }

    public List<PipelineDto> listActive() {
        return pipelineRepository.findAllActive().stream().map(pipelineMapper::toDto).toList();
    }
}
