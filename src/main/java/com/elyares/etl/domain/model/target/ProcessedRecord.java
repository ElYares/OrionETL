package com.elyares.etl.domain.model.target;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Representa un registro procesado y transformado, listo para ser persistido en el destino ETL.
 *
 * <p>Encapsula el mapa de campos resultante de la fase de transformación junto con metadatos
 * de trazabilidad: el número de fila en la fuente original, la versión del pipeline que
 * generó el registro y la marca temporal de transformación.</p>
 *
 * <p>Los datos se exponen siempre mediante una copia defensiva para preservar la inmutabilidad
 * de la instancia. Esta clase es inmutable siempre que los valores contenidos en el mapa
 * {@code data} sean también inmutables.</p>
 */
public final class ProcessedRecord {

    /** Número de fila del registro en la fuente de datos de origen, para trazabilidad. */
    private final long sourceRowNumber;

    /** Mapa de campos transformados donde la clave es el nombre de columna destino y el valor es el dato. */
    private final Map<String, Object> data;

    /** Identificador de versión del pipeline ETL que produjo este registro transformado. */
    private final String pipelineVersion;

    /** Marca temporal en la que se aplicó la transformación al registro. */
    private final Instant transformedAt;

    /**
     * Construye un nuevo {@code ProcessedRecord} con los datos transformados y metadatos especificados.
     *
     * <p>Si {@code transformedAt} es {@code null}, se utiliza el instante actual como marca temporal.</p>
     *
     * @param sourceRowNumber número de fila del registro en la fuente de datos original
     * @param data            mapa de campos transformados; no puede ser {@code null}
     * @param pipelineVersion identificador de versión del pipeline ETL que generó el registro
     * @param transformedAt   marca temporal de transformación; si es {@code null} se usa {@code Instant.now()}
     * @throws NullPointerException si {@code data} es {@code null}
     */
    public ProcessedRecord(long sourceRowNumber, Map<String, Object> data,
                           String pipelineVersion, Instant transformedAt) {
        this.sourceRowNumber = sourceRowNumber;
        this.data = Objects.requireNonNull(data);
        this.pipelineVersion = pipelineVersion;
        this.transformedAt = transformedAt != null ? transformedAt : Instant.now();
    }

    /**
     * Devuelve el número de fila del registro en la fuente de datos original.
     *
     * @return número de fila de origen, para trazabilidad end-to-end
     */
    public long getSourceRowNumber() { return sourceRowNumber; }

    /**
     * Devuelve una copia inmutable del mapa de campos transformados del registro.
     *
     * @return mapa de campos transformados; nunca {@code null}
     */
    public Map<String, Object> getData() { return Map.copyOf(data); }

    /**
     * Devuelve el identificador de versión del pipeline ETL que produjo este registro.
     *
     * @return versión del pipeline, o {@code null} si no fue especificada
     */
    public String getPipelineVersion() { return pipelineVersion; }

    /**
     * Devuelve la marca temporal en la que se aplicó la transformación al registro.
     *
     * @return instante de transformación; nunca {@code null}
     */
    public Instant getTransformedAt() { return transformedAt; }
}
