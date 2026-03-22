package com.elyares.etl.domain.model.source;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Resultado inmutable de una operación de extracción de datos desde una fuente.
 *
 * <p>Encapsula tanto el conjunto de registros crudos obtenidos en una extracción exitosa
 * como la información de error en caso de fallo. La construcción se realiza exclusivamente
 * a través de los métodos de fábrica estáticos {@link #success(List, String)} y
 * {@link #failure(String, String)}, que garantizan la coherencia interna del objeto.</p>
 *
 * <p>La lista de registros es copiada de forma defensiva para asegurar la inmutabilidad
 * de la instancia.</p>
 */
public final class ExtractionResult {

    /** Lista inmutable de registros crudos extraídos de la fuente. */
    private final List<RawRecord> records;

    /** Cantidad total de registros leídos durante la extracción. */
    private final long totalRead;

    /** Identificador de la fuente de datos de origen. */
    private final String sourceReference;

    /** Marca temporal en la que se completó la extracción. */
    private final Instant extractedAt;

    /** Indica si la operación de extracción finalizó sin errores. */
    private final boolean successful;

    /** Descripción del error ocurrido; {@code null} si la extracción fue exitosa. */
    private final String errorDetail;

    /**
     * Constructor privado. Las instancias deben crearse a través de los métodos de fábrica
     * {@link #success(List, String)} y {@link #failure(String, String)}.
     *
     * @param records         lista de registros extraídos; puede ser {@code null} (se tratará como vacía)
     * @param totalRead       cantidad total de registros leídos
     * @param sourceReference identificador de la fuente de datos
     * @param extractedAt     marca temporal de la extracción
     * @param successful      {@code true} si la extracción fue exitosa
     * @param errorDetail     detalle del error; {@code null} si la extracción fue exitosa
     */
    private ExtractionResult(List<RawRecord> records, long totalRead, String sourceReference,
                             Instant extractedAt, boolean successful, String errorDetail) {
        this.records = records != null ? List.copyOf(records) : List.of();
        this.totalRead = totalRead;
        this.sourceReference = sourceReference;
        this.extractedAt = extractedAt;
        this.successful = successful;
        this.errorDetail = errorDetail;
    }

    /**
     * Crea un {@code ExtractionResult} que representa una extracción exitosa.
     *
     * <p>El total de registros leídos se calcula a partir del tamaño de la lista proporcionada.
     * La marca temporal de extracción se establece al instante actual.</p>
     *
     * @param records         lista de registros crudos extraídos; no puede ser {@code null}
     * @param sourceReference identificador de la fuente de datos de origen
     * @return nueva instancia representando una extracción exitosa
     */
    public static ExtractionResult success(List<RawRecord> records, String sourceReference) {
        return new ExtractionResult(records, records.size(), sourceReference, Instant.now(), true, null);
    }

    /**
     * Crea un {@code ExtractionResult} que representa una extracción fallida.
     *
     * <p>La lista de registros será vacía y el total leído será cero.
     * La marca temporal de extracción se establece al instante actual.</p>
     *
     * @param sourceReference identificador de la fuente de datos de origen
     * @param errorDetail     descripción técnica del error que causó el fallo
     * @return nueva instancia representando una extracción fallida
     */
    public static ExtractionResult failure(String sourceReference, String errorDetail) {
        return new ExtractionResult(List.of(), 0, sourceReference, Instant.now(), false, errorDetail);
    }

    /**
     * Devuelve la lista inmutable de registros crudos extraídos.
     *
     * @return lista de {@link RawRecord}; nunca {@code null}, vacía si la extracción falló
     */
    public List<RawRecord> getRecords() { return records; }

    /**
     * Devuelve la cantidad total de registros leídos durante la extracción.
     *
     * @return número de registros leídos; {@code 0} si la extracción falló
     */
    public long getTotalRead() { return totalRead; }

    /**
     * Devuelve el identificador de la fuente de datos de origen.
     *
     * @return referencia a la fuente, o {@code null} si no fue especificada
     */
    public String getSourceReference() { return sourceReference; }

    /**
     * Devuelve la marca temporal en la que se completó la extracción.
     *
     * @return instante de extracción; nunca {@code null}
     */
    public Instant getExtractedAt() { return extractedAt; }

    /**
     * Indica si la operación de extracción se completó sin errores.
     *
     * @return {@code true} si la extracción fue exitosa; {@code false} en caso contrario
     */
    public boolean isSuccessful() { return successful; }

    /**
     * Devuelve el detalle del error ocurrido durante la extracción.
     *
     * @return descripción del error, o {@code null} si la extracción fue exitosa
     */
    public String getErrorDetail() { return errorDetail; }
}
