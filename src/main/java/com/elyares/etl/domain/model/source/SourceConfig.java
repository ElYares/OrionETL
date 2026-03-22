package com.elyares.etl.domain.model.source;

import com.elyares.etl.domain.enums.SourceType;

import java.util.Map;
import java.util.Objects;

/**
 * Configuración inmutable de una fuente de datos para el proceso de extracción ETL.
 *
 * <p>Encapsula todos los parámetros necesarios para conectarse y leer desde una fuente
 * de datos, incluyendo el tipo de fuente, la ubicación, la codificación de caracteres,
 * el delimitador de campos, la presencia de encabezado y las propiedades de conexión
 * adicionales.</p>
 *
 * <p>El mapa de propiedades de conexión se copia de forma defensiva en la construcción
 * para garantizar la inmutabilidad de la instancia.</p>
 */
public final class SourceConfig {

    /** Tipo de fuente de datos (p. ej. CSV, DATABASE, API). */
    private final SourceType type;

    /** Ruta o URI que identifica la ubicación física de la fuente de datos. */
    private final String location;

    /** Codificación de caracteres utilizada para leer la fuente. Por defecto {@code UTF-8}. */
    private final String encoding;

    /** Carácter delimitador de campos en fuentes de texto plano. Por defecto {@code ','}. */
    private final Character delimiter;

    /** Indica si la primera fila de la fuente corresponde a un encabezado con nombres de columna. */
    private final boolean hasHeader;

    /** Propiedades adicionales de conexión (p. ej. credenciales, parámetros JDBC). */
    private final Map<String, String> connectionProperties;

    /**
     * Construye una nueva instancia de {@code SourceConfig} con los parámetros especificados.
     *
     * <p>Si {@code encoding} es {@code null}, se asigna {@code "UTF-8"} por defecto.
     * Si {@code delimiter} es {@code null}, se asigna {@code ','} por defecto.
     * Si {@code connectionProperties} es {@code null}, se asigna un mapa vacío inmutable.</p>
     *
     * @param type                 tipo de fuente de datos; no puede ser {@code null}
     * @param location             ruta o URI de la fuente de datos
     * @param encoding             codificación de caracteres; si es {@code null} se usa {@code "UTF-8"}
     * @param delimiter            carácter separador de campos; si es {@code null} se usa {@code ','}
     * @param hasHeader            {@code true} si la fuente contiene fila de encabezado
     * @param connectionProperties propiedades adicionales de conexión; puede ser {@code null}
     * @throws NullPointerException si {@code type} es {@code null}
     */
    public SourceConfig(SourceType type, String location, String encoding,
                        Character delimiter, boolean hasHeader,
                        Map<String, String> connectionProperties) {
        this.type = Objects.requireNonNull(type, "SourceType must not be null");
        this.location = location;
        this.encoding = encoding != null ? encoding : "UTF-8";
        this.delimiter = delimiter != null ? delimiter : ',';
        this.hasHeader = hasHeader;
        this.connectionProperties = connectionProperties != null
            ? Map.copyOf(connectionProperties) : Map.of();
    }

    /**
     * Devuelve el tipo de fuente de datos.
     *
     * @return tipo de fuente; nunca {@code null}
     */
    public SourceType getType() { return type; }

    /**
     * Devuelve la ubicación (ruta o URI) de la fuente de datos.
     *
     * @return ubicación de la fuente, o {@code null} si no fue especificada
     */
    public String getLocation() { return location; }

    /**
     * Devuelve la codificación de caracteres configurada para la fuente.
     *
     * @return nombre del charset (p. ej. {@code "UTF-8"}); nunca {@code null}
     */
    public String getEncoding() { return encoding; }

    /**
     * Devuelve el carácter delimitador de campos para fuentes de texto plano.
     *
     * @return carácter delimitador; nunca {@code null}
     */
    public Character getDelimiter() { return delimiter; }

    /**
     * Indica si la fuente contiene una fila de encabezado con nombres de columna.
     *
     * @return {@code true} si existe fila de encabezado; {@code false} en caso contrario
     */
    public boolean isHasHeader() { return hasHeader; }

    /**
     * Devuelve las propiedades adicionales de conexión como un mapa inmutable.
     *
     * @return mapa de propiedades de conexión; nunca {@code null}, puede estar vacío
     */
    public Map<String, String> getConnectionProperties() { return connectionProperties; }
}
