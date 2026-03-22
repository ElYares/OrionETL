package com.elyares.etl.domain.rules;

import com.elyares.etl.domain.model.execution.PipelineExecution;

/**
 * Regla de dominio que impide que una ejecución se marque como exitosa si contiene
 * errores de severidad {@code CRITICAL}.
 *
 * <p>Se aplica en el paso CLOSE del orquestador para determinar el estado final.
 * Un error crítico indica una condición que compromete la integridad de los datos
 * cargados y por tanto el resultado debe marcarse como {@code FAILED} o {@code PARTIAL},
 * nunca como {@code SUCCESS}.</p>
 */
public class CriticalErrorBlocksSuccessRule {

    /**
     * Evalúa si la ejecución tiene errores críticos que impidan marcarla como exitosa.
     *
     * @param execution ejecución a evaluar
     * @return {@code true} si hay al menos un error {@code CRITICAL}; {@code false} en caso contrario
     */
    public boolean hasCriticalErrors(PipelineExecution execution) {
        return execution.hasCriticalErrors();
    }

    /**
     * Determina si la ejecución puede marcarse como {@code SUCCESS} según esta regla.
     *
     * @param execution ejecución a evaluar
     * @return {@code true} si no hay errores críticos y puede marcarse como exitosa
     */
    public boolean allowsSuccess(PipelineExecution execution) {
        return !hasCriticalErrors(execution);
    }
}
