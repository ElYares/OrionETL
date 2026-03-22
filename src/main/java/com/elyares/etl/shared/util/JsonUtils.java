package com.elyares.etl.shared.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * Utilidades para la serialización, deserialización y validación de JSON.
 *
 * <p>Encapsula un {@link ObjectMapper} compartido configurado con soporte para
 * tipos de fecha y hora de Java ({@link JavaTimeModule}) y con la serialización
 * de fechas como timestamps deshabilitada, de modo que se utiliza el formato
 * ISO-8601. Todos los métodos lanzan {@link IllegalArgumentException} ante
 * fallos de procesamiento, evitando que las excepciones comprobadas de Jackson
 * se propaguen a las capas superiores.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class JsonUtils {

    /**
     * Instancia compartida del mapeador Jackson configurada con soporte para
     * tipos temporales de Java y serialización en formato ISO-8601.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtils() {}

    /**
     * Serializa un objeto Java a su representación JSON en forma de cadena.
     *
     * @param obj objeto a serializar; puede ser {@code null} (produce {@code "null"})
     * @return cadena JSON que representa el objeto
     * @throws IllegalArgumentException si el objeto no puede ser serializado
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializa una cadena JSON al tipo Java indicado.
     *
     * @param <T>  tipo de destino de la deserialización
     * @param json cadena JSON a deserializar
     * @param type clase del tipo de destino
     * @return instancia del tipo {@code T} con los datos del JSON
     * @throws IllegalArgumentException si la cadena no puede ser deserializada al tipo indicado
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot deserialize JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Convierte un objeto Java en un mapa de propiedades {@code String → Object}
     * sin pasar por serialización textual.
     *
     * <p>Utiliza {@link ObjectMapper#convertValue} para realizar la conversión
     * en memoria.</p>
     *
     * @param obj objeto a convertir
     * @return mapa con las propiedades del objeto como pares clave-valor
     */
    public static Map<String, Object> toMap(Object obj) {
        return MAPPER.convertValue(obj, new TypeReference<>() {});
    }

    /**
     * Analiza una cadena JSON y la convierte en un mapa de propiedades
     * {@code String → Object}.
     *
     * @param json cadena JSON que representa un objeto
     * @return mapa con las propiedades del JSON como pares clave-valor
     * @throws IllegalArgumentException si la cadena no es un JSON de objeto válido
     */
    public static Map<String, Object> parseToMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot parse JSON to map: " + e.getMessage(), e);
        }
    }

    /**
     * Comprueba si una cadena contiene JSON sintácticamente válido.
     *
     * @param json cadena a evaluar; puede ser {@code null} o vacía
     * @return {@code true} si la cadena puede ser analizada como JSON válido;
     *         {@code false} en caso contrario
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
