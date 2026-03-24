package com.elyares.etl.domain.service;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.rules.AllowedExecutionWindowRule;
import com.elyares.etl.domain.rules.CriticalErrorBlocksSuccessRule;
import com.elyares.etl.domain.rules.NoDuplicateExecutionRule;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.domain.enums.TriggerType;

import java.time.Instant;

/**
 * Servicio de dominio que coordina la validación de precondiciones antes de
 * ejecutar un pipeline y determina el estado final de una ejecución.
 *
 * <p>Actúa como punto de entrada de validación previo a la creación de la
 * ejecución. Evalúa en orden:
 * <ol>
 *   <li>{@link NoDuplicateExecutionRule} — no puede haber otra ejecución activa</li>
 *   <li>{@link AllowedExecutionWindowRule} — la hora actual debe estar en ventana permitida</li>
 * </ol>
 * Si cualquier regla falla, lanza una excepción adecuada y la ejecución no se crea.</p>
 *
 * <p>También determina el {@link ExecutionStatus} final al cerrar una ejecución,
 * aplicando {@link CriticalErrorBlocksSuccessRule}.</p>
 *
 * <p>Clase pura de dominio — sin dependencias de Spring.</p>
 */
public class PipelineOrchestrationService {

    private final NoDuplicateExecutionRule noDuplicateRule;
    private final AllowedExecutionWindowRule windowRule;
    private final CriticalErrorBlocksSuccessRule criticalErrorRule;
    private final RetryEligibilityRule retryEligibilityRule;

    /**
     * Construye el servicio con las reglas de negocio requeridas.
     *
     * @param noDuplicateRule  regla de no-duplicado de ejecuciones
     * @param windowRule       regla de ventana horaria permitida
     * @param criticalErrorRule regla que bloquea éxito ante errores críticos
     */
    public PipelineOrchestrationService(NoDuplicateExecutionRule noDuplicateRule,
                                         AllowedExecutionWindowRule windowRule,
                                         CriticalErrorBlocksSuccessRule criticalErrorRule,
                                         RetryEligibilityRule retryEligibilityRule) {
        this.noDuplicateRule = noDuplicateRule;
        this.windowRule = windowRule;
        this.criticalErrorRule = criticalErrorRule;
        this.retryEligibilityRule = retryEligibilityRule;
    }

    /**
     * Valida todas las precondiciones necesarias para iniciar la ejecución de un pipeline.
     *
     * <p>Evalúa las reglas de negocio en orden. Si cualquiera falla, lanza
     * la excepción correspondiente y la ejecución no se inicia.</p>
     *
     * @param pipeline pipeline que se desea ejecutar
     * @param request  DTO con los datos de la solicitud de ejecución
     * @throws com.elyares.etl.shared.exception.ExecutionConflictException si ya hay una ejecución activa
     * @throws com.elyares.etl.shared.exception.EtlException si la ejecución no está en ventana permitida
     */
    public void validatePreconditions(Pipeline pipeline, ExecutionRequestDto request) {
        noDuplicateRule.evaluate(pipeline);
        if (request.triggerType() == TriggerType.SCHEDULED || request.triggerType() == TriggerType.RETRY) {
            windowRule.evaluate(pipeline.getScheduleConfig(), Instant.now());
        }
        if (request.triggerType() == TriggerType.RETRY) {
            int retryCount = parseRetryCount(request);
            ErrorType lastErrorType = parseErrorType(request);
            retryEligibilityRule.evaluate(retryCount, lastErrorType, pipeline.getRetryPolicy());
        }
    }

    /**
     * Determina el {@link ExecutionStatus} final que debe asignarse al cerrar una ejecución.
     *
     * <p>La lógica es:
     * <ul>
     *   <li>Si la ejecución ya tiene estado terminal distinto de SUCCESS → conservar ese estado</li>
     *   <li>Si hay errores críticos → {@code FAILED}</li>
     *   <li>Si hay registros rechazados pero no errores críticos → {@code PARTIAL}</li>
     *   <li>En caso contrario → {@code SUCCESS}</li>
     * </ul>
     *
     * @param execution ejecución cuyo estado final se debe determinar
     * @return estado final calculado para la ejecución
     */
    public ExecutionStatus determineExecutionStatus(PipelineExecution execution) {
        return determineExecutionStatus(execution, execution.getTotalRejected().value());
    }

    public ExecutionStatus determineExecutionStatus(PipelineExecution execution, long totalRejected) {
        if (execution.getStatus().isTerminal() &&
            execution.getStatus() != ExecutionStatus.SUCCESS) {
            return execution.getStatus();
        }
        if (criticalErrorRule.hasCriticalErrors(execution)) {
            return ExecutionStatus.FAILED;
        }
        if (totalRejected > 0) {
            return ExecutionStatus.PARTIAL;
        }
        return ExecutionStatus.SUCCESS;
    }

    private int parseRetryCount(ExecutionRequestDto request) {
        String value = request.parameters() != null ? request.parameters().get("retryCount") : null;
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private ErrorType parseErrorType(ExecutionRequestDto request) {
        String value = request.parameters() != null ? request.parameters().get("lastErrorType") : null;
        if (value == null || value.isBlank()) {
            return ErrorType.TECHNICAL;
        }
        return ErrorType.valueOf(value);
    }
}
