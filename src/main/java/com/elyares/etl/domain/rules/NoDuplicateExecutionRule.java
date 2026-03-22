package com.elyares.etl.domain.rules;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.shared.exception.ExecutionConflictException;

import java.util.Optional;

/**
 * Regla de dominio que impide ejecutar un pipeline que ya tiene una ejecución activa.
 *
 * <p>Consulta el repositorio de ejecuciones en busca de una ejecución con estado
 * {@code RUNNING} o {@code RETRYING} para el pipeline dado. Si existe, lanza
 * {@link ExecutionConflictException} para evitar ejecuciones duplicadas simultáneas.</p>
 *
 * <p>Esta regla se evalúa como primer paso en
 * {@code PipelineOrchestrationService.validatePreconditions()}.</p>
 */
public class NoDuplicateExecutionRule {

    private final ExecutionRepository executionRepository;

    /**
     * Construye la regla con el repositorio de ejecuciones requerido.
     *
     * @param executionRepository repositorio para consultar ejecuciones activas; no debe ser null
     */
    public NoDuplicateExecutionRule(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    /**
     * Evalúa la regla para el pipeline dado.
     *
     * <p>Busca una ejecución activa (RUNNING o RETRYING) para el pipeline.
     * Si encuentra una, lanza {@link ExecutionConflictException}.</p>
     *
     * @param pipeline pipeline que se desea ejecutar
     * @throws ExecutionConflictException si ya existe una ejecución activa para el pipeline
     */
    public void evaluate(Pipeline pipeline) {
        PipelineId pipelineId = pipeline.getId();
        Optional<PipelineExecution> activeExecution =
            executionRepository.findActiveByPipelineId(pipelineId);

        if (activeExecution.isPresent()) {
            throw new ExecutionConflictException(pipelineId.toString());
        }
    }
}
