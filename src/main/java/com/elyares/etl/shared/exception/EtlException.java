package com.elyares.etl.shared.exception;

/**
 * Excepción base no comprobada para todos los errores del sistema ETL.
 *
 * <p>Extiende {@link RuntimeException} y añade un código de error estructurado
 * ({@code errorCode}) que identifica de forma unívoca la causa del fallo.
 * Todas las excepciones específicas del dominio ETL deben heredar de esta clase
 * para garantizar un tratamiento uniforme en el manejador global de excepciones.</p>
 */
public class EtlException extends RuntimeException {

    /** Código que identifica la categoría y causa específica del error. */
    private final String errorCode;

    /**
     * Construye una nueva excepción con código de error y mensaje descriptivo.
     *
     * @param errorCode código de error canónico definido en
     *                  {@link com.elyares.etl.shared.constants.ErrorCodes}
     * @param message   descripción legible del error
     */
    public EtlException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Construye una nueva excepción con código de error, mensaje descriptivo
     * y causa subyacente.
     *
     * @param errorCode código de error canónico definido en
     *                  {@link com.elyares.etl.shared.constants.ErrorCodes}
     * @param message   descripción legible del error
     * @param cause     excepción original que provocó este error
     */
    public EtlException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Devuelve el código de error asociado a esta excepción.
     *
     * @return código de error no nulo
     */
    public String getErrorCode() {
        return errorCode;
    }
}
