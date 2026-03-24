package com.elyares.etl.domain.model.validation;

import com.elyares.etl.domain.model.source.RawRecord;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Representa un registro rechazado durante el proceso de validación del pipeline ETL.
 *
 * <p>Encapsula el registro original crudo que no superó la validación junto con el contexto
 * del rechazo: el nombre del paso del pipeline donde fue rechazado, el motivo resumido del
 * rechazo, la lista detallada de errores de validación y la marca temporal del momento
 * en que se produjo el rechazo.</p>
 *
 * <p>Esta clase es inmutable. La lista de errores de validación es copiada de forma defensiva
 * en la construcción. La marca temporal se establece automáticamente al instante de creación
 * del objeto.</p>
 */
public final class RejectedRecord {

    /** Registro crudo original que fue rechazado durante la validación. */
    private final RawRecord originalRecord;

    /** Nombre del paso del pipeline ETL en el que se produjo el rechazo. */
    private final String stepName;

    /** Descripción resumida del motivo por el que el registro fue rechazado. */
    private final String rejectionReason;

    /** Lista inmutable de errores de validación que causaron el rechazo del registro. */
    private final List<ValidationError> validationErrors;

    /** Marca temporal en la que el registro fue rechazado. */
    private final Instant rejectedAt;

    /**
     * Construye un nuevo {@code RejectedRecord} con el contexto completo del rechazo.
     *
     * <p>La marca temporal {@code rejectedAt} se establece automáticamente al instante actual.
     * Si {@code validationErrors} es {@code null}, se asigna una lista vacía inmutable.</p>
     *
     * @param originalRecord   registro crudo original que fue rechazado; no puede ser {@code null}
     * @param stepName         nombre del paso del pipeline donde ocurrió el rechazo; no puede ser {@code null}
     * @param rejectionReason  motivo resumido del rechazo; no puede ser {@code null}
     * @param validationErrors lista de errores de validación que motivaron el rechazo; puede ser {@code null}
     * @throws NullPointerException si {@code originalRecord}, {@code stepName} o {@code rejectionReason}
     *                              son {@code null}
     */
    public RejectedRecord(RawRecord originalRecord, String stepName, String rejectionReason,
                          List<ValidationError> validationErrors) {
        this(originalRecord, stepName, rejectionReason, validationErrors, Instant.now());
    }

    public RejectedRecord(RawRecord originalRecord, String stepName, String rejectionReason,
                          List<ValidationError> validationErrors, Instant rejectedAt) {
        this.originalRecord = Objects.requireNonNull(originalRecord);
        this.stepName = Objects.requireNonNull(stepName);
        this.rejectionReason = Objects.requireNonNull(rejectionReason);
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
        this.rejectedAt = rejectedAt != null ? rejectedAt : Instant.now();
    }

    /**
     * Devuelve el registro crudo original que fue rechazado.
     *
     * @return instancia de {@link RawRecord}; nunca {@code null}
     */
    public RawRecord getOriginalRecord() { return originalRecord; }

    /**
     * Devuelve el nombre del paso del pipeline ETL en el que se produjo el rechazo.
     *
     * @return nombre del paso; nunca {@code null}
     */
    public String getStepName() { return stepName; }

    /**
     * Devuelve el motivo resumido por el que el registro fue rechazado.
     *
     * @return descripción del motivo de rechazo; nunca {@code null}
     */
    public String getRejectionReason() { return rejectionReason; }

    /**
     * Devuelve la lista inmutable de errores de validación que causaron el rechazo del registro.
     *
     * @return lista de {@link ValidationError}; nunca {@code null}, puede estar vacía
     */
    public List<ValidationError> getValidationErrors() { return validationErrors; }

    /**
     * Devuelve la marca temporal en la que el registro fue rechazado.
     *
     * @return instante de rechazo; nunca {@code null}
     */
    public Instant getRejectedAt() { return rejectedAt; }
}
