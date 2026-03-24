package com.elyares.etl.domain.model.transformation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuración genérica de transformación utilizada por {@code CommonTransformer}.
 */
public final class TransformationConfig {

    private final List<String> dateFields;
    private final String dateFormat;
    private final String sourceTimezone;
    private final String currencyField;
    private final String baseCurrency;
    private final String defaultCurrency;
    private final Map<String, BigDecimal> currencyRates;
    private final List<String> monetaryFields;
    private final List<String> nullValues;
    private final Map<String, Map<String, String>> codeMappings;
    private final Map<String, String> mappingPolicies;
    private final Map<String, String> mappingDefaults;
    private final Map<String, String> derivedColumns;
    private final int roundingScale;
    private final RoundingMode roundingMode;
    private final List<String> roundingFields;

    public TransformationConfig(List<String> dateFields,
                                String dateFormat,
                                String sourceTimezone,
                                String currencyField,
                                String baseCurrency,
                                String defaultCurrency,
                                Map<String, BigDecimal> currencyRates,
                                List<String> monetaryFields,
                                List<String> nullValues,
                                Map<String, Map<String, String>> codeMappings,
                                Map<String, String> mappingPolicies,
                                Map<String, String> mappingDefaults,
                                Map<String, String> derivedColumns,
                                int roundingScale,
                                RoundingMode roundingMode,
                                List<String> roundingFields) {
        this.dateFields = dateFields != null ? List.copyOf(dateFields) : List.of();
        this.dateFormat = dateFormat;
        this.sourceTimezone = sourceTimezone != null ? sourceTimezone : "UTC";
        this.currencyField = currencyField != null ? currencyField : "currency";
        this.baseCurrency = baseCurrency != null ? baseCurrency : "USD";
        this.defaultCurrency = defaultCurrency;
        this.currencyRates = currencyRates != null ? Map.copyOf(currencyRates) : Map.of();
        this.monetaryFields = monetaryFields != null ? List.copyOf(monetaryFields) : List.of();
        this.nullValues = nullValues != null ? List.copyOf(nullValues) : List.of();
        this.codeMappings = codeMappings != null ? Map.copyOf(codeMappings) : Map.of();
        this.mappingPolicies = mappingPolicies != null ? Map.copyOf(mappingPolicies) : Map.of();
        this.mappingDefaults = mappingDefaults != null ? Map.copyOf(mappingDefaults) : Map.of();
        this.derivedColumns = derivedColumns != null ? Map.copyOf(derivedColumns) : Map.of();
        this.roundingScale = roundingScale >= 0 ? roundingScale : 2;
        this.roundingMode = Objects.requireNonNullElse(roundingMode, RoundingMode.HALF_UP);
        this.roundingFields = roundingFields != null ? List.copyOf(roundingFields) : List.of();
    }

    public static TransformationConfig defaultConfig() {
        return new TransformationConfig(List.of(), null, "UTC", "currency", "USD", null, Map.of(),
            List.of(), List.of("", "NULL", "N/A", "-"), Map.of(), Map.of(), Map.of(), Map.of(), 2,
            RoundingMode.HALF_UP, List.of());
    }

    public List<String> getDateFields() { return dateFields; }
    public String getDateFormat() { return dateFormat; }
    public String getSourceTimezone() { return sourceTimezone; }
    public String getCurrencyField() { return currencyField; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public Map<String, BigDecimal> getCurrencyRates() { return currencyRates; }
    public List<String> getMonetaryFields() { return monetaryFields; }
    public List<String> getNullValues() { return nullValues; }
    public Map<String, Map<String, String>> getCodeMappings() { return codeMappings; }
    public Map<String, String> getMappingPolicies() { return mappingPolicies; }
    public Map<String, String> getMappingDefaults() { return mappingDefaults; }
    public Map<String, String> getDerivedColumns() { return derivedColumns; }
    public int getRoundingScale() { return roundingScale; }
    public RoundingMode getRoundingMode() { return roundingMode; }
    public List<String> getRoundingFields() { return roundingFields; }
}
