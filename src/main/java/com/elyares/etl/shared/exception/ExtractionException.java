package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando ocurre un fallo durante el paso de extracción de datos.
 *
 * <p>Los escenarios que producen esta excepción incluyen fuentes de datos no
 * disponibles, errores de parseo del contenido fuente y ausencia de registros
 * cuando se esperan datos. Por defecto utiliza el código de error
 * {@code ETL_EXTRACT_001}; los constructores con {@code errorCode} explícito
 * permiten diferenciar causas más específicas definidas en
 * {@link com.elyares.etl.shared.constants.ErrorCodes}.</p>
 */
public class ExtractionException extends EtlException {

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_EXTRACT_001} y el mensaje proporcionado.
     *
     * @param message descripción del fallo de extracción
     */
    public ExtractionException(String message) {
        super("ETL_EXTRACT_001", message);
    }

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_EXTRACT_001}, el mensaje y la causa subyacente.
     *
     * @param message descripción del fallo de extracción
     * @param cause   excepción original que provocó el fallo
     */
    public ExtractionException(String message, Throwable cause) {
        super("ETL_EXTRACT_001", message, cause);
    }

    /**
     * Construye la excepción con un código de error específico, mensaje
     * y causa subyacente.
     *
     * @param errorCode código de error canónico que identifica el tipo de fallo
     * @param message   descripción del fallo de extracción
     * @param cause     excepción original que provocó el fallo
     */
    public ExtractionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
