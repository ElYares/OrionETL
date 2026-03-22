package com.elyares.etl.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Estructura de respuesta de error para la API REST del sistema ETL.
 *
 * <p>Proporciona un formato consistente para comunicar fallos al cliente HTTP,
 * incluyendo el código de error estructurado ({@code errorCode}), el mensaje
 * descriptivo, una lista opcional de detalles adicionales (útil para errores
 * de validación), la ruta de la solicitud fallida y la marca temporal. Los
 * campos con valor {@code null} se omiten en la serialización JSON.</p>
 *
 * <p>Se recomienda construir instancias a través de los métodos de fábrica
 * estáticos {@link #of(String, String)}, {@link #of(String, String, List)}
 * y {@link #of(String, String, String)}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String errorCode,
    String message,
    List<String> details,
    String path,
    Instant timestamp
) {
    /**
     * Crea una respuesta de error con código y mensaje, sin detalles ni ruta.
     *
     * @param errorCode código de error canónico (ver
     *                  {@link com.elyares.etl.shared.constants.ErrorCodes})
     * @param message   descripción legible del error
     * @return instancia con {@code details=null}, {@code path=null} y
     *         {@code timestamp} establecido al instante actual UTC
     */
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, null, null, Instant.now());
    }

    /**
     * Crea una respuesta de error con código, mensaje y lista de detalles de validación.
     *
     * @param errorCode código de error canónico
     * @param message   descripción legible del error
     * @param details   lista de mensajes de error individuales (p. ej., errores de validación)
     * @return instancia con {@code path=null} y {@code timestamp} establecido
     *         al instante actual UTC
     */
    public static ErrorResponse of(String errorCode, String message, List<String> details) {
        return new ErrorResponse(errorCode, message, details, null, Instant.now());
    }

    /**
     * Crea una respuesta de error con código, mensaje y ruta de la solicitud.
     *
     * @param errorCode código de error canónico
     * @param message   descripción legible del error
     * @param path      ruta HTTP de la solicitud que originó el error
     * @return instancia con {@code details=null} y {@code timestamp} establecido
     *         al instante actual UTC
     */
    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, null, path, Instant.now());
    }
}
