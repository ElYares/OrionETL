package com.elyares.etl.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Envoltorio genérico para las respuestas de la API REST del sistema ETL.
 *
 * <p>Encapsula el resultado de una operación indicando si fue exitosa
 * ({@code success}), el dato de respuesta tipado ({@code data}), un mensaje
 * opcional y la marca temporal de generación ({@code timestamp}). Los campos
 * con valor {@code null} se omiten en la serialización JSON gracias a
 * {@link JsonInclude#NON_NULL}.</p>
 *
 * <p>Se recomienda construir instancias exclusivamente a través de los métodos
 * de fábrica estáticos {@link #ok(Object)}, {@link #ok(Object, String)} y
 * {@link #error(String)}.</p>
 *
 * @param <T> tipo del objeto incluido en el campo {@code data}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    Instant timestamp
) {
    /**
     * Crea una respuesta exitosa que contiene únicamente el dato de resultado.
     *
     * @param <T>  tipo del dato de respuesta
     * @param data objeto con el resultado de la operación
     * @return instancia con {@code success=true}, {@code message=null} y
     *         {@code timestamp} establecido al instante actual UTC
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    /**
     * Crea una respuesta exitosa que contiene el dato de resultado y un mensaje informativo.
     *
     * @param <T>     tipo del dato de respuesta
     * @param data    objeto con el resultado de la operación
     * @param message mensaje informativo adicional
     * @return instancia con {@code success=true} y {@code timestamp} establecido
     *         al instante actual UTC
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    /**
     * Crea una respuesta de error sin dato de resultado.
     *
     * @param <T>     tipo del dato de respuesta (siempre {@code null} en este caso)
     * @param message descripción del error producido
     * @return instancia con {@code success=false}, {@code data=null} y
     *         {@code timestamp} establecido al instante actual UTC
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
