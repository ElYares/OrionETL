package com.elyares.etl.domain.enums;

/**
 * Enumera los tipos de destino de datos soportados por el sistema ETL para la fase de carga.
 *
 * <p>El tipo de destino determina qué estrategia y mecanismo de escritura se emplearán
 * para persistir los registros transformados una vez superada la fase de validación.</p>
 */
public enum TargetType {

    /** Base de datos relacional o no relacional como destino final de la carga. */
    DATABASE,

    /** Archivo de texto con valores separados por comas (Comma-Separated Values). */
    CSV,

    /** Data warehouse o plataforma analítica orientada a consultas masivas (p. ej. BigQuery, Redshift). */
    WAREHOUSE
}
