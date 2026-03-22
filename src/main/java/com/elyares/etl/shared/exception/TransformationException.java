package com.elyares.etl.shared.exception;

/**
 * Excepción lanzada cuando ocurre un fallo durante el paso de transformación de datos.
 *
 * <p>Cubre situaciones como incompatibilidad de tipos entre el campo fuente y el
 * campo destino, o la imposibilidad de aplicar una regla de mapeo a un registro
 * concreto. Por defecto utiliza el código de error {@code ETL_TRANSFORM_001};
 * los constructores con {@code errorCode} explícito permiten especificar causas
 * adicionales definidas en
 * {@link com.elyares.etl.shared.constants.ErrorCodes}.</p>
 */
public class TransformationException extends EtlException {

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_TRANSFORM_001} y el mensaje proporcionado.
     *
     * @param message descripción del fallo de transformación
     */
    public TransformationException(String message) {
        super("ETL_TRANSFORM_001", message);
    }

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_TRANSFORM_001}, el mensaje y la causa subyacente.
     *
     * @param message descripción del fallo de transformación
     * @param cause   excepción original que provocó el fallo
     */
    public TransformationException(String message, Throwable cause) {
        super("ETL_TRANSFORM_001", message, cause);
    }

    /**
     * Construye la excepción con un código de error específico, mensaje
     * y causa subyacente.
     *
     * @param errorCode código de error canónico que identifica el tipo de fallo
     * @param message   descripción del fallo de transformación
     * @param cause     excepción original que provocó el fallo
     */
    public TransformationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
