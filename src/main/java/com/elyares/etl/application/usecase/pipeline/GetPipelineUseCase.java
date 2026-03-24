package com.elyares.etl.application.usecase.pipeline;

import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.application.mapper.PipelineMapper;
import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.shared.exception.PipelineNotFoundException;

/**
 * Caso de uso para recuperar un pipeline por ID o por nombre.
 */
public class GetPipelineUseCase {

    private final PipelineRepository pipelineRepository;
    private final PipelineMapper pipelineMapper;

    public GetPipelineUseCase(PipelineRepository pipelineRepository, PipelineMapper pipelineMapper) {
        this.pipelineRepository = pipelineRepository;
        this.pipelineMapper = pipelineMapper;
    }

    public Pipeline getDomainById(String pipelineId) {
        return pipelineRepository.findById(PipelineId.of(pipelineId))
            .orElseThrow(() -> new PipelineNotFoundException(pipelineId));
    }

    public Pipeline getDomainByReference(String pipelineRef) {
        try {
            return getDomainById(pipelineRef);
        } catch (IllegalArgumentException | PipelineNotFoundException ex) {
            return getDomainByName(pipelineRef);
        }
    }

    public PipelineDto getById(String pipelineId) {
        return pipelineMapper.toDto(getDomainById(pipelineId));
    }

    public PipelineDto getByReference(String pipelineRef) {
        return pipelineMapper.toDto(getDomainByReference(pipelineRef));
    }

    public Pipeline getDomainByName(String pipelineName) {
        return pipelineRepository.findByName(pipelineName)
            .orElseThrow(() -> new PipelineNotFoundException("ETL_PIPELINE_NOT_FOUND", "Pipeline not found: " + pipelineName));
    }

    public PipelineDto getByName(String pipelineName) {
        return pipelineMapper.toDto(getDomainByName(pipelineName));
    }
}
