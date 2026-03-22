package com.elyares.etl.domain.rules;

import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.shared.exception.RetryExhaustedException;

/**
 * Regla de dominio que determina si una ejecución fallida puede ser reintentada.
 *
 * <p>Evalúa dos condiciones:
 * <ol>
 *   <li>El número de reintentos actuales no ha superado el máximo configurado.</li>
 *   <li>El tipo de error que causó el fallo está incluido en la lista de errores
 *       reintentables de la {@link RetryPolicy}.</li>
 * </ol>
 * Si la política tiene una lista vacía de tipos de error reintentables, se considera
 * que cualquier tipo de error es elegible.</p>
 */
public class RetryEligibilityRule {

    /**
     * Evalúa si se puede realizar un reintento dado el estado actual y la política configurada.
     *
     * @param currentRetryCount número de reintentos ya realizados
     * @param errorType         tipo de error que causó el último fallo
     * @param retryPolicy       política de reintentos configurada en el pipeline
     * @throws RetryExhaustedException si se ha alcanzado el límite de reintentos
     * @throws IllegalStateException   si el tipo de error no es reintentable según la política
     */
    public void evaluate(int currentRetryCount, ErrorType errorType, RetryPolicy retryPolicy) {
        if (currentRetryCount >= retryPolicy.getMaxRetries()) {
            throw new RetryExhaustedException("pipeline", retryPolicy.getMaxRetries());
        }
        if (!retryPolicy.canRetry(currentRetryCount, errorType)) {
            throw new IllegalStateException(
                "Error type %s is not retryable under current policy".formatted(errorType));
        }
    }

    /**
     * Comprueba de forma no destructiva si un reintento sería elegible.
     *
     * @param currentRetryCount número de reintentos ya realizados
     * @param errorType         tipo de error que causó el último fallo
     * @param retryPolicy       política de reintentos del pipeline
     * @return {@code true} si el reintento está permitido; {@code false} en caso contrario
     */
    public boolean isEligible(int currentRetryCount, ErrorType errorType, RetryPolicy retryPolicy) {
        return retryPolicy.canRetry(currentRetryCount, errorType);
    }
}
