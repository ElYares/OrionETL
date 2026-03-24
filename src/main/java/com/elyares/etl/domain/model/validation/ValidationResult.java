package com.elyares.etl.domain.model.validation;

import com.elyares.etl.domain.enums.ErrorSeverity;

import com.elyares.etl.domain.model.source.RawRecord;

import java.util.List;

/**
 * Resultado inmutable de la validación de un registro individual del pipeline ETL.
 *
 * <p>Encapsula el estado de validación ({@code válido}/{@code inválido}) junto con la lista
 * de errores detectados durante el proceso. Un registro se considera válido únicamente
 * cuando no contiene errores de severidad {@link ErrorSeverity#ERROR} ni
 * {@link ErrorSeverity#CRITICAL}; las advertencias ({@link ErrorSeverity#WARNING}) no afectan
 * a la validez del registro.</p>
 *
 * <p>La construcción se realiza a través de los métodos de fábrica estáticos:
 * {@link #ok()}, {@link #failed(List)} y {@link #of(List)}, que garantizan la coherencia
 * interna del objeto. La lista de errores es copiada de forma defensiva.</p>
 */
public final class ValidationResult {

    /** Indica si el registro superó la validación sin errores bloqueantes. */
    private final boolean valid;

    /** Registros que sí pueden seguir al siguiente paso. */
    private final List<RawRecord> validRecords;

    /** Registros rechazados durante la validación. */
    private final List<RejectedRecord> rejectedRecords;

    /** Lista inmutable de errores de validación detectados en el registro. */
    private final List<ValidationError> errors;

    /** Reporte agregado de calidad para el lote validado. */
    private final DataQualityReport dataQualityReport;

    /**
     * Constructor privado. Las instancias deben crearse a través de los métodos de fábrica
     * {@link #ok()}, {@link #failed(List)} y {@link #of(List)}.
     *
     * @param valid  {@code true} si el registro es válido
     * @param errors lista de errores de validación; puede ser {@code null} (se tratará como vacía)
     */
    private ValidationResult(boolean valid,
                             List<RawRecord> validRecords,
                             List<RejectedRecord> rejectedRecords,
                             List<ValidationError> errors,
                             DataQualityReport dataQualityReport) {
        this.valid = valid;
        this.validRecords = validRecords != null ? List.copyOf(validRecords) : List.of();
        this.rejectedRecords = rejectedRecords != null ? List.copyOf(rejectedRecords) : List.of();
        this.errors = errors != null ? List.copyOf(errors) : List.of();
        this.dataQualityReport = dataQualityReport;
    }

    /**
     * Crea un {@code ValidationResult} que representa una validación exitosa sin errores.
     *
     * @return nueva instancia con {@code valid = true} y lista de errores vacía
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of(), List.of(), null);
    }

    public static ValidationResult ok(List<RawRecord> validRecords) {
        return new ValidationResult(true, validRecords, List.of(), List.of(), null);
    }

    /**
     * Crea un {@code ValidationResult} que representa una validación fallida con la lista
     * de errores especificada.
     *
     * @param errors lista de errores de validación detectados; no debe ser {@code null}
     * @return nueva instancia con {@code valid = false} y los errores proporcionados
     */
    public static ValidationResult failed(List<ValidationError> errors) {
        return new ValidationResult(false, List.of(), List.of(), errors, null);
    }

    /**
     * Crea un {@code ValidationResult} a partir de una lista de errores, calculando automáticamente
     * el estado de validez.
     *
     * <p>El resultado será válido ({@code valid = true}) si ninguno de los errores tiene
     * severidad {@link ErrorSeverity#ERROR} o {@link ErrorSeverity#CRITICAL}. Las advertencias
     * ({@link ErrorSeverity#WARNING}) se registran pero no invalidan el resultado.</p>
     *
     * @param errors lista de errores y advertencias de validación; no debe ser {@code null}
     * @return nueva instancia con el estado de validez calculado a partir de los errores
     */
    public static ValidationResult of(List<ValidationError> errors) {
        boolean allPass = errors.stream().noneMatch(e -> e.severity() == ErrorSeverity.ERROR
            || e.severity() == ErrorSeverity.CRITICAL);
        return new ValidationResult(allPass, List.of(), List.of(), errors, null);
    }

    public static ValidationResult batch(List<RawRecord> validRecords,
                                         List<RejectedRecord> rejectedRecords,
                                         List<ValidationError> errors,
                                         DataQualityReport dataQualityReport) {
        boolean batchValid = rejectedRecords == null || rejectedRecords.isEmpty();
        return new ValidationResult(batchValid, validRecords, rejectedRecords, errors, dataQualityReport);
    }

    /**
     * Indica si el registro superó la validación sin errores bloqueantes.
     *
     * @return {@code true} si el registro es válido; {@code false} si contiene errores {@code ERROR} o {@code CRITICAL}
     */
    public boolean isValid() { return valid; }

    public List<RawRecord> getValidRecords() { return validRecords; }

    public List<RejectedRecord> getRejectedRecords() { return rejectedRecords; }

    /**
     * Devuelve la lista inmutable de errores de validación detectados en el registro.
     *
     * @return lista de {@link ValidationError}; nunca {@code null}, vacía si no hay errores
     */
    public List<ValidationError> getErrors() { return errors; }

    public DataQualityReport getDataQualityReport() { return dataQualityReport; }

    /**
     * Devuelve el número de errores no críticos (severidad {@link ErrorSeverity#ERROR}) presentes
     * en el resultado. Los errores de severidad {@link ErrorSeverity#CRITICAL} se excluyen de
     * este conteo.
     *
     * @return cantidad de errores no críticos
     */
    public long getErrorCount() { return errors.stream().filter(e -> !e.isCritical()).count(); }

    public long getInvalidCount() { return rejectedRecords.size(); }

    /**
     * Devuelve el número de errores de severidad {@link ErrorSeverity#CRITICAL} presentes
     * en el resultado.
     *
     * @return cantidad de errores críticos
     */
    public long getCriticalCount() { return errors.stream().filter(ValidationError::isCritical).count(); }

    /**
     * Indica si el resultado contiene al menos un error de severidad {@link ErrorSeverity#CRITICAL}.
     *
     * @return {@code true} si existe al menos un error crítico; {@code false} en caso contrario
     */
    public boolean hasCriticalErrors() { return getCriticalCount() > 0; }

    public double getErrorRate() {
        return dataQualityReport != null ? dataQualityReport.errorRatePercent() : 0.0;
    }
}
