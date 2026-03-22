package com.elyares.etl.domain.service;

import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.domain.valueobject.RecordCount;
import com.elyares.etl.shared.exception.EtlException;

import java.util.UUID;

/**
 * Servicio de dominio que gestiona el ciclo de vida completo de una ejecución ETL.
 *
 * <p>Es responsable de crear nuevas ejecuciones y de transicionarlas entre sus
 * estados de ciclo de vida: PENDING → RUNNING → SUCCESS | FAILED | PARTIAL.</p>
 *
 * <p>Todas las operaciones de estado se persisten a través del
 * {@link ExecutionRepository}. Este servicio no contiene lógica de orquestación
 * de pasos ETL — esa responsabilidad pertenece al {@code ETLOrchestrator}.</p>
 *
 * <p>Esta clase es pura Java sin dependencias de Spring. La inyección de
 * dependencias se realiza a través del constructor.</p>
 */
public class ExecutionLifecycleService {

    private final ExecutionRepository executionRepository;

    /**
     * Construye el servicio con el repositorio de ejecuciones.
     *
     * @param executionRepository repositorio para persistir y consultar ejecuciones; no debe ser null
     */
    public ExecutionLifecycleService(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    /**
     * Crea y persiste una nueva ejecución de pipeline en estado {@code PENDING}.
     *
     * @param pipelineId  identificador del pipeline a ejecutar
     * @param triggerType origen que dispara la ejecución
     * @param triggeredBy actor que inició la ejecución (usuario, scheduler, CLI)
     * @return nueva {@link PipelineExecution} en estado PENDING, ya persistida
     */
    public PipelineExecution createExecution(PipelineId pipelineId,
                                              TriggerType triggerType,
                                              String triggeredBy) {
        PipelineExecution execution = new PipelineExecution(
            UUID.randomUUID(),
            pipelineId,
            ExecutionId.generate(),
            triggerType,
            triggeredBy
        );
        return executionRepository.save(execution);
    }

    /**
     * Transiciona una ejecución de PENDING a RUNNING y registra la hora de inicio.
     *
     * @param executionId identificador de la ejecución a iniciar
     * @return ejecución actualizada en estado RUNNING
     * @throws EtlException si la ejecución no existe
     */
    public PipelineExecution markRunning(ExecutionId executionId) {
        PipelineExecution execution = findOrThrow(executionId);
        execution.start();
        return executionRepository.save(execution);
    }

    /**
     * Transiciona una ejecución a SUCCESS con los conteos finales de registros.
     *
     * <p>Solo debe llamarse si no hay errores críticos (verificado por
     * {@code CriticalErrorBlocksSuccessRule}).</p>
     *
     * @param executionId  identificador de la ejecución
     * @param totalRead        total de registros leídos
     * @param totalTransformed total de registros transformados
     * @param totalRejected    total de registros rechazados
     * @param totalLoaded      total de registros cargados
     * @return ejecución actualizada en estado SUCCESS
     */
    public PipelineExecution markSuccess(ExecutionId executionId,
                                          long totalRead,
                                          long totalTransformed,
                                          long totalRejected,
                                          long totalLoaded) {
        PipelineExecution execution = findOrThrow(executionId);
        execution.complete(
            RecordCount.of(totalRead),
            RecordCount.of(totalTransformed),
            RecordCount.of(totalRejected),
            RecordCount.of(totalLoaded)
        );
        return executionRepository.save(execution);
    }

    /**
     * Transiciona una ejecución a FAILED con un resumen del error.
     *
     * @param executionId  identificador de la ejecución
     * @param errorSummary descripción breve de la causa del fallo
     * @return ejecución actualizada en estado FAILED
     */
    public PipelineExecution markFailed(ExecutionId executionId, String errorSummary) {
        PipelineExecution execution = findOrThrow(executionId);
        execution.fail(errorSummary);
        return executionRepository.save(execution);
    }

    /**
     * Transiciona una ejecución a PARTIAL — procesamiento completado pero con rechazos
     * o errores no críticos que impiden marcarla como SUCCESS puro.
     *
     * @param executionId      identificador de la ejecución
     * @param totalRead        total de registros leídos
     * @param totalTransformed total de registros transformados
     * @param totalRejected    total de registros rechazados
     * @param totalLoaded      total de registros cargados
     * @param errorSummary     descripción del motivo por el que no fue SUCCESS completo
     * @return ejecución actualizada en estado PARTIAL
     */
    public PipelineExecution markPartial(ExecutionId executionId,
                                          long totalRead,
                                          long totalTransformed,
                                          long totalRejected,
                                          long totalLoaded,
                                          String errorSummary) {
        PipelineExecution execution = findOrThrow(executionId);
        execution.partialSuccess(
            RecordCount.of(totalRead),
            RecordCount.of(totalTransformed),
            RecordCount.of(totalRejected),
            RecordCount.of(totalLoaded),
            errorSummary
        );
        return executionRepository.save(execution);
    }

    /**
     * Cierra explícitamente una ejecución.
     *
     * <p>Si la ejecución aún no está en estado terminal, se marca como {@code FAILED}
     * para evitar dejar ejecuciones abiertas indefinidamente.</p>
     *
     * @param executionId identificador de la ejecución a cerrar
     */
    public void closeExecution(ExecutionId executionId) {
        PipelineExecution execution = findOrThrow(executionId);
        if (!execution.getStatus().isTerminal()) {
            execution.fail("Execution closed before reaching terminal status");
        }
        executionRepository.save(execution);
    }

    /**
     * Busca una ejecución por su ID o lanza excepción si no existe.
     *
     * @param executionId identificador de la ejecución
     * @return ejecución encontrada
     * @throws EtlException si no existe ninguna ejecución con ese ID
     */
    private PipelineExecution findOrThrow(ExecutionId executionId) {
        return executionRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new EtlException(
                "ETL_EXEC_NOT_FOUND",
                "Execution not found: " + executionId
            ));
    }
}
