package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando se agota el número máximo de reintentos configurado
 * para la ejecución de un pipeline.
 *
 * <p>Utiliza el código de error {@code ETL_RETRY_EXHAUSTED}. El constructor de
 * conveniencia formatea el mensaje con el límite de reintentos y el identificador
 * del pipeline para facilitar el diagnóstico en los registros de auditoría.</p>
 */
public class RetryExhaustedException extends EtlException {

    /**
     * Construye la excepción con el código {@code ETL_RETRY_EXHAUSTED} y un
     * mensaje que indica el límite alcanzado y el pipeline afectado.
     *
     * @param pipelineId identificador del pipeline cuyos reintentos se agotaron
     * @param maxRetries número máximo de intentos configurado que fue superado
     */
    public RetryExhaustedException(String pipelineId, int maxRetries) {
        super("ETL_RETRY_EXHAUSTED",
              "Retry limit (%d) exhausted for pipeline: %s".formatted(maxRetries, pipelineId));
    }

    /**
     * Construye la excepción con un código de error y un mensaje personalizados.
     *
     * @param errorCode código de error canónico
     * @param message   descripción del agotamiento de reintentos
     */
    public RetryExhaustedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
