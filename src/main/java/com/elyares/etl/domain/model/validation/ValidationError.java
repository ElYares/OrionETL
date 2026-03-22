package com.elyares.etl.domain.model.validation;

import com.elyares.etl.domain.enums.ErrorSeverity;

import java.util.Objects;

/**
 * Representa un error de validación detectado sobre un campo concreto de un registro.
 *
 * <p>Encapsula el nombre del campo afectado, el valor que no superó la validación, el
 * identificador de la regla que fue violada, un mensaje descriptivo del problema y la
 * severidad del error ({@link ErrorSeverity#WARNING}, {@link ErrorSeverity#ERROR} o
 * {@link ErrorSeverity#CRITICAL}).</p>
 *
 * <p>Es un {@code record} de Java, por lo que es inmutable por definición. La validación
 * del bloque compacto garantiza que {@code rule} y {@code message} no sean {@code null},
 * y asigna {@link ErrorSeverity#ERROR} como severidad por defecto si se recibe {@code null}.</p>
 *
 * <p>Los métodos de fábrica estáticos {@link #error}, {@link #critical} y {@link #warning}
 * simplifican la creación de instancias con la severidad correcta sin necesidad de
 * referenciar el enum directamente.</p>
 */
public record ValidationError(
    /** Nombre del campo del registro en el que se detectó el error de validación. */
    String field,
    /** Valor del campo que incumplió la regla de validación. */
    Object value,
    /** Identificador de la regla de validación que fue violada. */
    String rule,
    /** Mensaje descriptivo que explica la causa del error de validación. */
    String message,
    /** Nivel de severidad del error de validación. */
    ErrorSeverity severity
) {
    /**
     * Bloque de validación compacto del record.
     *
     * <p>Verifica que {@code rule} y {@code message} no sean {@code null}.
     * Si {@code severity} es {@code null}, la establece a {@link ErrorSeverity#ERROR} por defecto.</p>
     *
     * @throws NullPointerException si {@code rule} o {@code message} son {@code null}
     */
    public ValidationError {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (severity == null) severity = ErrorSeverity.ERROR;
    }

    /**
     * Crea un {@code ValidationError} con severidad {@link ErrorSeverity#ERROR}.
     *
     * @param field   nombre del campo que incumplió la regla
     * @param value   valor del campo que causó el error
     * @param rule    identificador de la regla de validación violada
     * @param message descripción del error de validación
     * @return nueva instancia con severidad {@code ERROR}
     */
    public static ValidationError error(String field, Object value, String rule, String message) {
        return new ValidationError(field, value, rule, message, ErrorSeverity.ERROR);
    }

    /**
     * Crea un {@code ValidationError} con severidad {@link ErrorSeverity#CRITICAL}.
     *
     * <p>Los errores críticos implican que el registro afectado no puede ser procesado
     * y debe ser rechazado de forma obligatoria.</p>
     *
     * @param field   nombre del campo que incumplió la regla
     * @param value   valor del campo que causó el error crítico
     * @param rule    identificador de la regla de validación violada
     * @param message descripción del error crítico de validación
     * @return nueva instancia con severidad {@code CRITICAL}
     */
    public static ValidationError critical(String field, Object value, String rule, String message) {
        return new ValidationError(field, value, rule, message, ErrorSeverity.CRITICAL);
    }

    /**
     * Crea un {@code ValidationError} con severidad {@link ErrorSeverity#WARNING}.
     *
     * <p>Las advertencias no bloquean el procesamiento del registro pero deben ser registradas
     * para su revisión posterior.</p>
     *
     * @param field   nombre del campo que generó la advertencia
     * @param value   valor del campo que originó la advertencia
     * @param rule    identificador de la regla de validación que emitió la advertencia
     * @param message descripción de la advertencia de validación
     * @return nueva instancia con severidad {@code WARNING}
     */
    public static ValidationError warning(String field, Object value, String rule, String message) {
        return new ValidationError(field, value, rule, message, ErrorSeverity.WARNING);
    }

    /**
     * Indica si este error de validación tiene severidad crítica.
     *
     * @return {@code true} si la severidad es {@link ErrorSeverity#CRITICAL}; {@code false} en caso contrario
     */
    public boolean isCritical() {
        return severity == ErrorSeverity.CRITICAL;
    }
}
