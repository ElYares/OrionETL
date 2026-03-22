package com.elyares.etl.domain.enums;

/**
 * Define la estrategia de escritura utilizada durante la fase de carga del pipeline ETL.
 *
 * <p>La estrategia seleccionada determina el comportamiento ante registros ya existentes
 * en el destino y tiene implicaciones directas sobre la idempotencia y el rendimiento
 * de la operación de carga.</p>
 */
public enum LoadStrategy {

    /**
     * Inserta nuevos registros en el destino sin verificar duplicados.
     * Apropiada cuando se garantiza que los registros de entrada son siempre nuevos.
     */
    INSERT,

    /**
     * Inserta el registro si no existe o lo actualiza si ya está presente,
     * utilizando una clave de negocio como criterio de coincidencia.
     * Garantiza idempotencia en la carga.
     */
    UPSERT,

    /**
     * Elimina todos los registros existentes en el destino antes de insertar
     * los nuevos. Produce una sustitución completa del conjunto de datos.
     */
    REPLACE
}
