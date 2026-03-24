package com.elyares.etl.domain.model.validation;

import com.elyares.etl.domain.valueobject.ErrorThreshold;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final List<String> mandatoryColumns;
    private final Map<String, String> columnTypes;
    private final List<String> uniqueKeyColumns;
    private final Map<String, String> columnPatterns;
    private final String dateFormat;
    private final boolean acceptIso8601Fallback;
    private final List<String> amountFields;
    private final boolean allowNegativeAmounts;
    private final Map<String, RangeRule> rangeRules;
    private final List<String> futureDateFields;
    private final Map<String, FutureDateRule> futureDateRules;
    private final Map<String, Set<String>> catalogValues;
    private final Map<String, Set<String>> activeCatalogValues;
    private final boolean rejectAllDuplicates;
    private final ErrorThreshold errorThreshold;
    private final boolean abortOnThresholdBreach;

    public ValidationConfig(List<String> mandatoryColumns, Map<String, String> columnTypes,
                            List<String> uniqueKeyColumns, ErrorThreshold errorThreshold,
                            boolean abortOnThresholdBreach) {
        this(mandatoryColumns, columnTypes, uniqueKeyColumns, Map.of(), null, true, List.of(),
            false, Map.of(), List.of(), Map.of(), Map.of(), Map.of(), false, errorThreshold,
            abortOnThresholdBreach);
    }

    public ValidationConfig(List<String> mandatoryColumns,
                            Map<String, String> columnTypes,
                            List<String> uniqueKeyColumns,
                            Map<String, String> columnPatterns,
                            String dateFormat,
                            boolean acceptIso8601Fallback,
                            List<String> amountFields,
                            boolean allowNegativeAmounts,
                            Map<String, RangeRule> rangeRules,
                            List<String> futureDateFields,
                            Map<String, Set<String>> catalogValues,
                            Map<String, Set<String>> activeCatalogValues,
                            boolean rejectAllDuplicates,
                            ErrorThreshold errorThreshold,
                            boolean abortOnThresholdBreach) {
        this(mandatoryColumns, columnTypes, uniqueKeyColumns, columnPatterns, dateFormat,
            acceptIso8601Fallback, amountFields, allowNegativeAmounts, rangeRules,
            futureDateFields, Map.of(), catalogValues, activeCatalogValues, rejectAllDuplicates,
            errorThreshold, abortOnThresholdBreach);
    }

    public ValidationConfig(List<String> mandatoryColumns,
                            Map<String, String> columnTypes,
                            List<String> uniqueKeyColumns,
                            Map<String, String> columnPatterns,
                            String dateFormat,
                            boolean acceptIso8601Fallback,
                            List<String> amountFields,
                            boolean allowNegativeAmounts,
                            Map<String, RangeRule> rangeRules,
                            List<String> futureDateFields,
                            Map<String, FutureDateRule> futureDateRules,
                            Map<String, Set<String>> catalogValues,
                            Map<String, Set<String>> activeCatalogValues,
                            boolean rejectAllDuplicates,
                            ErrorThreshold errorThreshold,
                            boolean abortOnThresholdBreach) {
        this.mandatoryColumns = mandatoryColumns != null ? List.copyOf(mandatoryColumns) : List.of();
        this.columnTypes = columnTypes != null ? Map.copyOf(columnTypes) : Map.of();
        this.uniqueKeyColumns = uniqueKeyColumns != null ? List.copyOf(uniqueKeyColumns) : List.of();
        this.columnPatterns = columnPatterns != null ? Map.copyOf(columnPatterns) : Map.of();
        this.dateFormat = dateFormat;
        this.acceptIso8601Fallback = acceptIso8601Fallback;
        this.amountFields = amountFields != null ? List.copyOf(amountFields) : List.of();
        this.allowNegativeAmounts = allowNegativeAmounts;
        this.rangeRules = rangeRules != null ? Map.copyOf(rangeRules) : Map.of();
        this.futureDateFields = futureDateFields != null ? List.copyOf(futureDateFields) : List.of();
        this.futureDateRules = futureDateRules != null ? Map.copyOf(futureDateRules) : Map.of();
        this.catalogValues = copyNestedSets(catalogValues);
        this.activeCatalogValues = copyNestedSets(activeCatalogValues);
        this.rejectAllDuplicates = rejectAllDuplicates;
        this.errorThreshold = Objects.requireNonNullElse(errorThreshold, ErrorThreshold.defaultThreshold());
        this.abortOnThresholdBreach = abortOnThresholdBreach;
    }

    public List<String> getMandatoryColumns() { return mandatoryColumns; }

    public Map<String, String> getColumnTypes() { return columnTypes; }

    public List<String> getUniqueKeyColumns() { return uniqueKeyColumns; }

    public Map<String, String> getColumnPatterns() { return columnPatterns; }

    public String getDateFormat() { return dateFormat; }

    public boolean isAcceptIso8601Fallback() { return acceptIso8601Fallback; }

    public List<String> getAmountFields() { return amountFields; }

    public boolean isAllowNegativeAmounts() { return allowNegativeAmounts; }

    public Map<String, RangeRule> getRangeRules() { return rangeRules; }

    public List<String> getFutureDateFields() { return futureDateFields; }

    public Map<String, FutureDateRule> getFutureDateRules() { return futureDateRules; }

    public Map<String, Set<String>> getCatalogValues() { return catalogValues; }

    public Map<String, Set<String>> getActiveCatalogValues() { return activeCatalogValues; }

    public boolean isRejectAllDuplicates() { return rejectAllDuplicates; }

    public ErrorThreshold getErrorThreshold() { return errorThreshold; }

    public boolean isAbortOnThresholdBreach() { return abortOnThresholdBreach; }

    private Map<String, Set<String>> copyNestedSets(Map<String, Set<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Set.copyOf(entry.getValue())
        ));
    }

    public record RangeRule(BigDecimal min, BigDecimal max, boolean inclusive) {
        public RangeRule {
            Objects.requireNonNull(min, "min must not be null");
            Objects.requireNonNull(max, "max must not be null");
        }
    }

    public record FutureDateRule(int allowedDaysInFuture,
                                 String conditionField,
                                 Set<String> allowedValues) {
        public FutureDateRule {
            if (allowedDaysInFuture < 0) {
                throw new IllegalArgumentException("allowedDaysInFuture must be >= 0");
            }
            allowedValues = allowedValues != null ? Set.copyOf(allowedValues) : Set.of();
        }

        public boolean appliesTo(Object candidateValue) {
            if (allowedValues.isEmpty()) {
                return true;
            }
            return candidateValue != null && allowedValues.contains(String.valueOf(candidateValue));
        }
    }
}
