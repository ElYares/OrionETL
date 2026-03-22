package com.elyares.etl.shared.logging;

import org.slf4j.MDC;

import java.io.Closeable;

/**
 * Contexto MDC (Mapped Diagnostic Context) vinculado a la ejecución de un pipeline ETL.
 *
 * <p>Gestiona el ciclo de vida de las entradas MDC relacionadas con una ejecución
 * concreta ({@code executionId}, {@code pipelineId} y {@code stepName}). Implementa
 * {@link Closeable} para permitir su uso en bloques {@code try-with-resources},
 * garantizando que las claves MDC se eliminen automáticamente al finalizar el bloque,
 * incluso en presencia de excepciones.</p>
 *
 * <p>Esta clase no puede ser instanciada directamente; use los métodos de fábrica
 * {@link #of(String, String)} u {@link #of(String, String, String)}.</p>
 *
 * <p>Ejemplo de uso:</p>
 * <pre>{@code
 * try (ExecutionMdcContext ctx = ExecutionMdcContext.of(executionId, pipelineId)) {
 *     // El log incluirá executionId y pipelineId en cada línea
 * }
 * }</pre>
 */
public final class ExecutionMdcContext implements Closeable {

    private static final String KEY_EXECUTION_ID = "executionId";
    private static final String KEY_PIPELINE_ID  = "pipelineId";
    private static final String KEY_STEP_NAME    = "stepName";

    private ExecutionMdcContext() {}

    /**
     * Crea e inicializa un contexto MDC con el identificador de ejecución
     * y el identificador de pipeline.
     *
     * @param executionId identificador único de la ejecución en curso
     * @param pipelineId  identificador del pipeline que se está ejecutando
     * @return instancia {@link Closeable} que elimina las entradas MDC al cerrarse
     */
    public static ExecutionMdcContext of(String executionId, String pipelineId) {
        MDC.put(KEY_EXECUTION_ID, executionId);
        MDC.put(KEY_PIPELINE_ID, pipelineId);
        return new ExecutionMdcContext();
    }

    /**
     * Crea e inicializa un contexto MDC con el identificador de ejecución,
     * el identificador de pipeline y el nombre del paso activo.
     *
     * @param executionId identificador único de la ejecución en curso
     * @param pipelineId  identificador del pipeline que se está ejecutando
     * @param stepName    nombre del paso ETL activo (ver {@link com.elyares.etl.shared.constants.StepNames})
     * @return instancia {@link Closeable} que elimina las entradas MDC al cerrarse
     */
    public static ExecutionMdcContext of(String executionId, String pipelineId, String stepName) {
        MDC.put(KEY_EXECUTION_ID, executionId);
        MDC.put(KEY_PIPELINE_ID, pipelineId);
        MDC.put(KEY_STEP_NAME, stepName);
        return new ExecutionMdcContext();
    }

    /**
     * Establece o actualiza la clave {@code stepName} en el MDC del hilo actual.
     *
     * <p>Permite actualizar el paso activo sin necesidad de recrear el contexto
     * completo.</p>
     *
     * @param stepName nombre del paso ETL que se inicia
     */
    public static void setStep(String stepName) {
        MDC.put(KEY_STEP_NAME, stepName);
    }

    /**
     * Elimina la clave {@code stepName} del MDC del hilo actual.
     *
     * <p>Debe invocarse al finalizar un paso individual cuando el contexto
     * completo no va a ser cerrado todavía.</p>
     */
    public static void clearStep() {
        MDC.remove(KEY_STEP_NAME);
    }

    /**
     * Elimina del MDC todas las claves gestionadas por este contexto
     * ({@code executionId}, {@code pipelineId} y {@code stepName}).
     *
     * <p>Se invoca automáticamente al salir de un bloque {@code try-with-resources}.</p>
     */
    @Override
    public void close() {
        MDC.remove(KEY_EXECUTION_ID);
        MDC.remove(KEY_PIPELINE_ID);
        MDC.remove(KEY_STEP_NAME);
    }
}
