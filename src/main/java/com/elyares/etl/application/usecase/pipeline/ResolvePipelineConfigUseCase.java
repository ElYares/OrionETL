package com.elyares.etl.application.usecase.pipeline;

import com.elyares.etl.domain.model.pipeline.Pipeline;

/**
 * Caso de uso para resolver y validar la configuración de un pipeline.
 *
 * <p>En Fase 2 la resolución usa el repositorio ya cargado; la carga desde YAML
 * se implementa en fases posteriores de infraestructura.</p>
 */
public class ResolvePipelineConfigUseCase {

    private final GetPipelineUseCase getPipelineUseCase;

    public ResolvePipelineConfigUseCase(GetPipelineUseCase getPipelineUseCase) {
        this.getPipelineUseCase = getPipelineUseCase;
    }

    public Pipeline resolveById(String pipelineId) {
        return getPipelineUseCase.getDomainById(pipelineId);
    }

    public Pipeline resolveByName(String pipelineName) {
        return getPipelineUseCase.getDomainByName(pipelineName);
    }
}
