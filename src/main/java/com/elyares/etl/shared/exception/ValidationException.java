package com.elyares.etl.shared.exception;

import java.util.List;

/**
 * Excepción lanzada cuando uno o más registros no superan las reglas de validación.
 *
 * <p>Además del mensaje y el código de error heredados de {@link EtlException},
 * transporta la lista completa de errores de validación individuales que
 * describeen qué campos o reglas fallaron. La lista se almacena como una copia
 * inmutable para evitar modificaciones externas.</p>
 *
 * <p>El código de error por defecto es {@code ETL_VALIDATION_001}; puede
 * sobreescribirse mediante el constructor que acepta un {@code errorCode}
 * explícito para diferenciar entre validaciones de esquema y validaciones
 * de negocio.</p>
 */
public class ValidationException extends EtlException {

    /** Lista inmutable de mensajes descriptivos de cada error de validación. */
    private final List<String> validationErrors;

    /**
     * Construye la excepción con el código de error por defecto
     * {@code ETL_VALIDATION_001}, el mensaje y la lista de errores.
     *
     * @param message          descripción general del fallo de validación
     * @param validationErrors lista de errores individuales; se copia de forma inmutable
     */
    public ValidationException(String message, List<String> validationErrors) {
        super("ETL_VALIDATION_001", message);
        this.validationErrors = List.copyOf(validationErrors);
    }

    /**
     * Construye la excepción con un código de error específico, el mensaje
     * y la lista de errores.
     *
     * @param errorCode        código de error canónico que identifica el tipo de validación
     * @param message          descripción general del fallo de validación
     * @param validationErrors lista de errores individuales; se copia de forma inmutable
     */
    public ValidationException(String errorCode, String message, List<String> validationErrors) {
        super(errorCode, message);
        this.validationErrors = List.copyOf(validationErrors);
    }

    /**
     * Devuelve la lista inmutable de errores de validación individuales.
     *
     * @return lista no nula y no modificable de mensajes de error
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
