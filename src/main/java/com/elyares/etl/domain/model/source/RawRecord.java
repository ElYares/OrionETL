package com.elyares.etl.domain.model.source;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Representa un registro crudo extraído de una fuente de datos, sin transformaciones aplicadas.
 *
 * <p>Encapsula los campos leídos directamente desde la fuente junto con metadatos de trazabilidad:
 * número de fila, referencia a la fuente de origen y marca temporal de extracción. Los datos
 * se exponen siempre mediante una copia defensiva para preservar la inmutabilidad.</p>
 *
 * <p>Esta clase es inmutable siempre que los valores almacenados en el mapa {@code data} sean
 * también inmutables.</p>
 */
public final class RawRecord {

    /** Número de fila secuencial dentro del archivo o resultado de consulta de origen. */
    private final long rowNumber;

    /** Mapa de campos del registro donde la clave es el nombre de columna y el valor es el dato leído. */
    private final Map<String, Object> data;

    /** Identificador de la fuente de datos de la cual se extrajo este registro. */
    private final String sourceReference;

    /** Marca temporal en la que se realizó la extracción del registro. */
    private final Instant extractedAt;

    /**
     * Construye un nuevo {@code RawRecord} con los datos y metadatos especificados.
     *
     * <p>Si {@code extractedAt} es {@code null}, se utiliza el instante actual como marca temporal.</p>
     *
     * @param rowNumber       número de fila del registro en la fuente de datos
     * @param data            mapa de campos del registro; no puede ser {@code null}
     * @param sourceReference identificador de la fuente de datos de origen
     * @param extractedAt     marca temporal de extracción; si es {@code null} se usa {@code Instant.now()}
     * @throws NullPointerException si {@code data} es {@code null}
     */
    public RawRecord(long rowNumber, Map<String, Object> data,
                     String sourceReference, Instant extractedAt) {
        this.rowNumber = rowNumber;
        this.data = Objects.requireNonNull(data, "RawRecord data must not be null");
        this.sourceReference = sourceReference;
        this.extractedAt = extractedAt != null ? extractedAt : Instant.now();
    }

    /**
     * Devuelve el número de fila del registro en la fuente de datos.
     *
     * @return número de fila; comienza en 1 o en 0 según la convención del extractor
     */
    public long getRowNumber() { return rowNumber; }

    /**
     * Devuelve una copia inmutable del mapa de campos del registro.
     *
     * @return mapa de campos; nunca {@code null}
     */
    public Map<String, Object> getData() { return Map.copyOf(data); }

    /**
     * Devuelve el identificador de la fuente de datos de origen.
     *
     * @return referencia a la fuente, o {@code null} si no fue especificada
     */
    public String getSourceReference() { return sourceReference; }

    /**
     * Devuelve la marca temporal en la que se extrajo el registro.
     *
     * @return instante de extracción; nunca {@code null}
     */
    public Instant getExtractedAt() { return extractedAt; }

    /**
     * Obtiene el valor del campo con el nombre especificado.
     *
     * @param fieldName nombre del campo a recuperar
     * @return valor asociado al campo, o {@code null} si el campo no existe en el registro
     */
    public Object getField(String fieldName) {
        return data.get(fieldName);
    }

    /**
     * Indica si el registro contiene un campo con el nombre especificado.
     *
     * @param fieldName nombre del campo a verificar
     * @return {@code true} si el campo existe en el registro; {@code false} en caso contrario
     */
    public boolean hasField(String fieldName) {
        return data.containsKey(fieldName);
    }

    /**
     * Devuelve una representación textual resumida del registro que incluye el número de fila
     * y la referencia a la fuente de datos.
     *
     * @return cadena de texto en el formato {@code RawRecord{row=N, source=S}}
     */
    @Override
    public String toString() {
        return "RawRecord{row=" + rowNumber + ", source=" + sourceReference + "}";
    }
}
