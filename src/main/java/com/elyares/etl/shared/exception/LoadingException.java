package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando ocurre un fallo durante el paso de carga de datos.
 *
 * <p>Cubre escenarios como errores de persistencia en la base de datos de
 * destino, fallos en la validación de la tabla de staging y errores al
 * procesar un lote (chunk) de registros. Por defecto utiliza el código de
 * error {@code ETL_LOAD_001}; los constructores con {@code errorCode}
 * explícito permiten indicar causas más específicas definidas en
 * {@link com.elyares.etl.shared.constants.ErrorCodes}.</p>
 */
public class LoadingException extends EtlException {

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_LOAD_001} y el mensaje proporcionado.
     *
     * @param message descripción del fallo de carga
     */
    public LoadingException(String message) {
        super("ETL_LOAD_001", message);
    }

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_LOAD_001}, el mensaje y la causa subyacente.
     *
     * @param message descripción del fallo de carga
     * @param cause   excepción original que provocó el fallo
     */
    public LoadingException(String message, Throwable cause) {
        super("ETL_LOAD_001", message, cause);
    }

    /**
     * Construye la excepción con un código de error específico, mensaje
     * y causa subyacente.
     *
     * @param errorCode código de error canónico que identifica el tipo de fallo
     * @param message   descripción del fallo de carga
     * @param cause     excepción original que provocó el fallo
     */
    public LoadingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
