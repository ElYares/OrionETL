package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando se solicita un pipeline que no existe en el sistema.
 *
 * <p>Utiliza el código de error {@code ETL_PIPELINE_NOT_FOUND} e incluye el
 * identificador del pipeline en el mensaje para facilitar el diagnóstico.
 * El constructor de conveniencia formatea automáticamente el mensaje a partir
 * del identificador recibido.</p>
 */
public class PipelineNotFoundException extends EtlException {

    /**
     * Construye la excepción con el código {@code ETL_PIPELINE_NOT_FOUND}
     * y un mensaje que incluye el identificador del pipeline no encontrado.
     *
     * @param pipelineId identificador del pipeline que no pudo ser localizado
     */
    public PipelineNotFoundException(String pipelineId) {
        super("ETL_PIPELINE_NOT_FOUND", "Pipeline not found: " + pipelineId);
    }

    /**
     * Construye la excepción con un código de error y un mensaje personalizados.
     *
     * @param errorCode código de error canónico
     * @param message   descripción del error
     */
    public PipelineNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
