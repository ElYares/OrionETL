package com.elyares.etl.domain.enums;

/**
 * Enumera los tipos de origen de datos soportados por el sistema ETL para la fase de extracción.
 *
 * <p>El tipo de origen determina qué implementación de {@code DataExtractor} se utilizará
 * para leer los datos en bruto antes de su transformación y carga.</p>
 */
public enum SourceType {

    /** Archivo de texto con valores separados por comas (Comma-Separated Values). */
    CSV,

    /** Archivo en formato Microsoft Excel (.xls / .xlsx). */
    EXCEL,

    /** Archivo o flujo de datos en formato JSON (JavaScript Object Notation). */
    JSON,

    /** Fuente de datos expuesta mediante una API HTTP/REST externa. */
    API,

    /** Base de datos relacional o no relacional accesible mediante conexión JDBC u ORM. */
    DATABASE
}
