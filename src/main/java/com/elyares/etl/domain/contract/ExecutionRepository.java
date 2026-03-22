package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de dominio para la persistencia y recuperación de ejecuciones de pipeline ETL.
 *
 * <p>Define las operaciones de acceso a datos necesarias sobre la entidad {@code PipelineExecution}.
 * Siguiendo el patrón de arquitectura hexagonal, las implementaciones concretas residen en
 * la capa de infraestructura, manteniéndose el dominio libre de dependencias tecnológicas.</p>
 */
public interface ExecutionRepository {

    /**
     * Persiste o actualiza una ejecución de pipeline en el almacenamiento.
     *
     * <p>Si la ejecución ya existe (mismo {@code ExecutionId}), su estado es actualizado;
     * en caso contrario, se crea un nuevo registro.</p>
     *
     * @param execution instancia de {@code PipelineExecution} a guardar.
     * @return la instancia persistida, potencialmente enriquecida con datos generados
     *         por la capa de persistencia (timestamps, versiones, etc.).
     */
    PipelineExecution save(PipelineExecution execution);

    /**
     * Busca una ejecución de pipeline por su identificador único de ejecución.
     *
     * @param executionId identificador único de la ejecución a recuperar.
     * @return {@code Optional} con la ejecución encontrada, o vacío si no existe.
     */
    Optional<PipelineExecution> findByExecutionId(ExecutionId executionId);

    /**
     * Busca la ejecución activa (en estado {@code RUNNING} o {@code RETRYING}) asociada
     * a un pipeline específico.
     *
     * <p>Un pipeline no debería tener más de una ejecución activa simultáneamente.
     * Si no existe ninguna ejecución activa para el pipeline dado, el resultado estará vacío.</p>
     *
     * @param pipelineId identificador del pipeline cuya ejecución activa se desea encontrar.
     * @return {@code Optional} con la ejecución activa, o vacío si el pipeline no tiene ninguna.
     */
    Optional<PipelineExecution> findActiveByPipelineId(PipelineId pipelineId);

    /**
     * Recupera el historial de ejecuciones de un pipeline, ordenadas por fecha descendente.
     *
     * @param pipelineId identificador del pipeline cuyas ejecuciones se desean recuperar.
     * @param limit      número máximo de ejecuciones a devolver.
     * @return lista con las ejecuciones más recientes del pipeline, limitada a {@code limit} elementos.
     */
    List<PipelineExecution> findByPipelineId(PipelineId pipelineId, int limit);
}
