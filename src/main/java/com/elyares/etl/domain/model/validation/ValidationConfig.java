package com.elyares.etl.domain.model.validation;

import com.elyares.etl.domain.valueobject.ErrorThreshold;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuración inmutable que define las reglas de validación aplicadas durante el proceso ETL.
 *
 * <p>Establece el conjunto de restricciones que deben cumplir los registros para ser considerados
 * válidos: columnas obligatorias, tipos de datos esperados por columna, columnas que conforman
 * claves únicas, el umbral máximo de error permitido y si se debe abortar el proceso cuando
 * dicho umbral es superado.</p>
 *
 * <p>Todas las colecciones son copiadas de forma defensiva en la construcción para garantizar
 * la inmutabilidad de la instancia. Si {@code errorThreshold} es {@code null}, se aplica el
 * umbral por defecto definido en {@link ErrorThreshold#defaultThreshold()}.</p>
 */
public final class ValidationConfig {

    /** Lista inmutable de nombres de columnas que deben estar presentes y no nulas en cada registro. */
    private final List<String> mandatoryColumns;

    /**
     * Mapa inmutable de tipo esperado por columna, donde la clave es el nombre de columna
     * y el valor es el nombre del tipo de dato (p. ej. {@code "STRING"}, {@code "INTEGER"}).
     */
    private final Map<String, String> columnTypes;

    /** Lista inmutable de columnas que en conjunto conforman una clave única de negocio. */
    private final List<String> uniqueKeyColumns;

    /** Umbral de error que define el porcentaje máximo de registros rechazados aceptable. */
    private final ErrorThreshold errorThreshold;

    /**
     * Indica si el proceso ETL debe abortarse cuando la tasa de error supera
     * el umbral configurado en {@code errorThreshold}.
     */
    private final boolean abortOnThresholdBreach;

    /**
     * Construye una nueva instancia de {@code ValidationConfig} con las reglas especificadas.
     *
     * <p>Valores por defecto aplicados cuando el parámetro es {@code null}:</p>
     * <ul>
     *   <li>{@code mandatoryColumns}: lista vacía inmutable</li>
     *   <li>{@code columnTypes}: mapa vacío inmutable</li>
     *   <li>{@code uniqueKeyColumns}: lista vacía inmutable</li>
     *   <li>{@code errorThreshold}: resultado de {@link ErrorThreshold#defaultThreshold()}</li>
     * </ul>
     *
     * @param mandatoryColumns       columnas obligatorias; puede ser {@code null}
     * @param columnTypes            mapa de tipos esperados por columna; puede ser {@code null}
     * @param uniqueKeyColumns       columnas de clave única; puede ser {@code null}
     * @param errorThreshold         umbral de tasa de error; si es {@code null} se usa el umbral por defecto
     * @param abortOnThresholdBreach {@code true} para abortar el pipeline al superar el umbral de error
     */
    public ValidationConfig(List<String> mandatoryColumns, Map<String, String> columnTypes,
                            List<String> uniqueKeyColumns, ErrorThreshold errorThreshold,
                            boolean abortOnThresholdBreach) {
        this.mandatoryColumns = mandatoryColumns != null ? List.copyOf(mandatoryColumns) : List.of();
        this.columnTypes = columnTypes != null ? Map.copyOf(columnTypes) : Map.of();
        this.uniqueKeyColumns = uniqueKeyColumns != null ? List.copyOf(uniqueKeyColumns) : List.of();
        this.errorThreshold = Objects.requireNonNullElse(errorThreshold, ErrorThreshold.defaultThreshold());
        this.abortOnThresholdBreach = abortOnThresholdBreach;
    }

    /**
     * Devuelve la lista inmutable de columnas obligatorias que deben estar presentes en cada registro.
     *
     * @return lista de nombres de columnas obligatorias; nunca {@code null}, puede estar vacía
     */
    public List<String> getMandatoryColumns() { return mandatoryColumns; }

    /**
     * Devuelve el mapa inmutable de tipos de datos esperados por columna.
     *
     * @return mapa de {@code nombre_columna -> tipo_esperado}; nunca {@code null}, puede estar vacío
     */
    public Map<String, String> getColumnTypes() { return columnTypes; }

    /**
     * Devuelve la lista inmutable de columnas que conforman la clave única de negocio.
     *
     * @return lista de nombres de columnas de clave única; nunca {@code null}, puede estar vacía
     */
    public List<String> getUniqueKeyColumns() { return uniqueKeyColumns; }

    /**
     * Devuelve el umbral de tasa de error máximo aceptable para el proceso de validación.
     *
     * @return instancia de {@link ErrorThreshold}; nunca {@code null}
     */
    public ErrorThreshold getErrorThreshold() { return errorThreshold; }

    /**
     * Indica si el proceso ETL debe abortarse cuando la tasa de error supera el umbral configurado.
     *
     * @return {@code true} si se debe abortar al superar el umbral; {@code false} para continuar
     */
    public boolean isAbortOnThresholdBreach() { return abortOnThresholdBreach; }
}
