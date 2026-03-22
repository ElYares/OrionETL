package com.elyares.etl.domain.model.target;

import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.TargetType;

import java.util.List;
import java.util.Objects;

/**
 * Configuración inmutable del destino de carga para el proceso ETL.
 *
 * <p>Define todos los parámetros que determinan cómo y dónde se persisten los registros
 * procesados: el tipo de destino, el esquema y tablas de base de datos, la estrategia de
 * carga, las columnas que conforman la clave de negocio y el tamaño de lote para inserciones.</p>
 *
 * <p>La configuración admite el uso de una tabla de staging intermedia antes de la promoción
 * a la tabla final. Si {@code stagingTable} es {@code null} o está en blanco,
 * {@link #hasStagingTable()} retornará {@code false} y la carga se realizará directamente
 * sobre la tabla final.</p>
 */
public final class TargetConfig {

    /** Tipo de destino de datos (p. ej. POSTGRESQL, FILE). */
    private final TargetType type;

    /** Nombre del esquema de base de datos. Por defecto {@code "public"}. */
    private final String schema;

    /** Nombre de la tabla de staging utilizada como paso intermedio de carga. */
    private final String stagingTable;

    /** Nombre de la tabla final donde se persisten definitivamente los registros. */
    private final String finalTable;

    /** Estrategia de carga aplicada (p. ej. UPSERT, INSERT, TRUNCATE_INSERT). */
    private final LoadStrategy loadStrategy;

    /** Columnas que identifican de forma única un registro a efectos de negocio (clave natural). */
    private final List<String> businessKeyColumns;

    /** Número de registros procesados por lote en cada operación de inserción. */
    private final int chunkSize;

    /**
     * Construye una nueva instancia de {@code TargetConfig} con los parámetros especificados.
     *
     * <p>Valores por defecto aplicados cuando el parámetro es {@code null} o inválido:</p>
     * <ul>
     *   <li>{@code schema}: {@code "public"}</li>
     *   <li>{@code loadStrategy}: {@link LoadStrategy#UPSERT}</li>
     *   <li>{@code businessKeyColumns}: lista vacía inmutable</li>
     *   <li>{@code chunkSize}: {@code 1000} si el valor proporcionado es menor o igual a cero</li>
     * </ul>
     *
     * @param type               tipo de destino de datos; no puede ser {@code null}
     * @param schema             nombre del esquema de base de datos; si es {@code null} se usa {@code "public"}
     * @param stagingTable       nombre de la tabla de staging; puede ser {@code null}
     * @param finalTable         nombre de la tabla final; no puede ser {@code null}
     * @param loadStrategy       estrategia de carga; si es {@code null} se usa {@link LoadStrategy#UPSERT}
     * @param businessKeyColumns columnas de clave de negocio; puede ser {@code null}
     * @param chunkSize          tamaño del lote de inserción; si es {@code <= 0} se usa {@code 1000}
     * @throws NullPointerException si {@code type} o {@code finalTable} son {@code null}
     */
    public TargetConfig(TargetType type, String schema, String stagingTable, String finalTable,
                        LoadStrategy loadStrategy, List<String> businessKeyColumns, int chunkSize) {
        this.type = Objects.requireNonNull(type);
        this.schema = schema != null ? schema : "public";
        this.stagingTable = stagingTable;
        this.finalTable = Objects.requireNonNull(finalTable);
        this.loadStrategy = loadStrategy != null ? loadStrategy : LoadStrategy.UPSERT;
        this.businessKeyColumns = businessKeyColumns != null ? List.copyOf(businessKeyColumns) : List.of();
        this.chunkSize = chunkSize > 0 ? chunkSize : 1000;
    }

    /**
     * Devuelve el tipo de destino de datos.
     *
     * @return tipo de destino; nunca {@code null}
     */
    public TargetType getType() { return type; }

    /**
     * Devuelve el nombre del esquema de base de datos configurado.
     *
     * @return nombre del esquema; nunca {@code null}
     */
    public String getSchema() { return schema; }

    /**
     * Devuelve el nombre de la tabla de staging utilizada como paso intermedio de carga.
     *
     * @return nombre de la tabla de staging, o {@code null} si no se utiliza tabla intermedia
     */
    public String getStagingTable() { return stagingTable; }

    /**
     * Devuelve el nombre de la tabla final donde se persisten los registros procesados.
     *
     * @return nombre de la tabla final; nunca {@code null}
     */
    public String getFinalTable() { return finalTable; }

    /**
     * Devuelve la estrategia de carga configurada para la escritura en el destino.
     *
     * @return estrategia de carga; nunca {@code null}
     */
    public LoadStrategy getLoadStrategy() { return loadStrategy; }

    /**
     * Devuelve la lista inmutable de columnas que conforman la clave de negocio del registro.
     *
     * @return lista de nombres de columnas de clave de negocio; nunca {@code null}, puede estar vacía
     */
    public List<String> getBusinessKeyColumns() { return businessKeyColumns; }

    /**
     * Devuelve el tamaño de lote configurado para las operaciones de inserción.
     *
     * @return número de registros por lote; siempre mayor que cero
     */
    public int getChunkSize() { return chunkSize; }

    /**
     * Indica si esta configuración define una tabla de staging para carga intermedia.
     *
     * @return {@code true} si {@code stagingTable} es no nula y no está en blanco;
     *         {@code false} en caso contrario
     */
    public boolean hasStagingTable() { return stagingTable != null && !stagingTable.isBlank(); }
}
