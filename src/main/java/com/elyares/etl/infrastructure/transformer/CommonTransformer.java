package com.elyares.etl.infrastructure.transformer;

import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.shared.util.DateUtils;
import com.elyares.etl.shared.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementa reglas genéricas TR-001 a TR-010.
 */
@Component
public class CommonTransformer {

    public CommonTransformationOutput transform(List<RawRecord> records, Pipeline pipeline) {
        List<ProcessedRecord> processed = new ArrayList<>();
        List<RejectedRecord> rejected = new ArrayList<>();

        for (RawRecord rawRecord : records) {
            try {
                // Cada registro se transforma de forma aislada para poder rechazar solo el fallido
                // sin romper el resto del lote.
                processed.add(transformRecord(rawRecord, pipeline));
            } catch (IllegalArgumentException ex) {
                rejected.add(new RejectedRecord(
                    rawRecord,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, rawRecord.getData(), "TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        return new CommonTransformationOutput(processed, rejected);
    }

    private ProcessedRecord transformRecord(RawRecord rawRecord, Pipeline pipeline) {
        TransformationConfig config = pipeline.getTransformationConfig();
        Map<String, Object> normalized = new LinkedHashMap<>();

        // La normalización base corre antes que cualquier regla específica para dejar un mapa
        // consistente en nombres y valores.
        rawRecord.getData().forEach((key, value) -> normalized.put(
            StringUtils.toSnakeCase(key.replace(' ', '_').replace('-', '_').replace('.', '_')),
            normalizeValue(key, value, config)
        ));

        // El orden importa: mappings y fechas pueden cambiar el valor que luego usan fórmulas
        // y redondeo.
        applyCodeMappings(normalized, config);
        applyDateNormalization(normalized, config);
        applyCurrencyNormalization(normalized, config);
        applyDerivedColumns(normalized, config);
        applyMonetaryRounding(normalized, config);

        return new ProcessedRecord(
            rawRecord.getRowNumber(),
            normalized,
            pipeline.getVersion(),
            rawRecord.getSourceReference(),
            Instant.now()
        );
    }

    private Object normalizeValue(String originalKey, Object value, TransformationConfig config) {
        if (value instanceof String stringValue) {
            String trimmed = StringUtils.trimToNull(stringValue);
            // La lista de nulls configurables permite tratar valores típicos de CSV como null real
            // antes de validar o transformar tipos.
            if (trimmed == null || config.getNullValues().stream().anyMatch(trimmed::equalsIgnoreCase)) {
                return null;
            }
            return trimmed;
        }
        return value;
    }

    private void applyCodeMappings(Map<String, Object> data, TransformationConfig config) {
        config.getCodeMappings().forEach((field, mapping) -> {
            Object rawValue = data.get(field);
            if (rawValue == null) {
                return;
            }
            String mapped = mapping.get(String.valueOf(rawValue));
            if (mapped != null) {
                data.put(field, mapped);
                return;
            }
            String policy = config.getMappingPolicies().getOrDefault(field, "REJECT");
            // REJECT es el default porque un código sin mapear normalmente significa que el dato
            // no está listo para cargarse al modelo canónico.
            if ("DEFAULT".equalsIgnoreCase(policy)) {
                data.put(field, config.getMappingDefaults().get(field));
            } else if ("PASS_THROUGH".equalsIgnoreCase(policy)) {
                data.put(field, rawValue);
            } else {
                throw new IllegalArgumentException("Missing code mapping for field " + field + " and value " + rawValue);
            }
        });
    }

    private void applyDateNormalization(Map<String, Object> data, TransformationConfig config) {
        ZoneId sourceZone = ZoneId.of(config.getSourceTimezone());
        for (String field : config.getDateFields()) {
            Object value = data.get(field);
            if (value == null) {
                continue;
            }
            // La salida interna ya queda en UTC para que validación, carga y auditoría operen
            // sobre una referencia temporal consistente.
            data.put(field, DateUtils.toUtcInstant(String.valueOf(value), sourceZone));
        }
    }

    private void applyCurrencyNormalization(Map<String, Object> data, TransformationConfig config) {
        for (String field : config.getMonetaryFields()) {
            Object value = data.get(field);
            if (value == null) {
                continue;
            }
            // Toda aritmética monetaria se hace con BigDecimal; no se usa double para evitar
            // errores de precisión acumulados.
            BigDecimal amount = new BigDecimal(String.valueOf(value));
            String sourceCurrency = resolveCurrency(data, config);
            BigDecimal rate = config.getCurrencyRates().getOrDefault(sourceCurrency, BigDecimal.ONE);
            data.put(field + "_original", amount);
            data.put("currency_original", sourceCurrency);
            data.put(field, amount.multiply(rate));
            data.put(config.getCurrencyField(), config.getBaseCurrency());
        }
    }

    private String resolveCurrency(Map<String, Object> data, TransformationConfig config) {
        Object currency = data.get(config.getCurrencyField());
        if (currency != null) {
            return String.valueOf(currency);
        }
        if (config.getDefaultCurrency() != null) {
            return config.getDefaultCurrency();
        }
        return config.getBaseCurrency();
    }

    private void applyDerivedColumns(Map<String, Object> data, TransformationConfig config) {
        // Las fórmulas se evalúan en orden de configuración para que una columna derivada pueda
        // ser usada por la siguiente.
        config.getDerivedColumns().forEach((field, formula) -> data.put(field, evaluateFormula(formula, data)));
    }

    private BigDecimal evaluateFormula(String formula, Map<String, Object> data) {
        String[] tokens = formula.trim().split("\\s+");
        if (tokens.length != 3) {
            throw new IllegalArgumentException("Unsupported derived formula: " + formula);
        }
        BigDecimal left = resolveOperand(tokens[0], data);
        BigDecimal right = resolveOperand(tokens[2], data);
        return switch (tokens[1]) {
            case "+" -> left.add(right);
            case "-" -> left.subtract(right);
            case "*" -> left.multiply(right);
            case "/" -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new IllegalArgumentException("Division by zero in formula: " + formula);
                }
                yield left.divide(right, 8, java.math.RoundingMode.HALF_UP);
            }
            default -> throw new IllegalArgumentException("Unsupported operator in formula: " + formula);
        };
    }

    private BigDecimal resolveOperand(String token, Map<String, Object> data) {
        // Si el token existe en el mapa, se toma como referencia de columna; si no, se interpreta
        // como literal numérico.
        Object value = data.getOrDefault(token, token);
        return new BigDecimal(String.valueOf(value));
    }

    private void applyMonetaryRounding(Map<String, Object> data, TransformationConfig config) {
        for (String field : config.getRoundingFields()) {
            Object value = data.get(field);
            if (value == null) {
                continue;
            }
            // El redondeo se aplica al final para no perder precisión durante el cálculo.
            data.put(field, new BigDecimal(String.valueOf(value))
                .setScale(config.getRoundingScale(), config.getRoundingMode()));
        }
    }

    public record CommonTransformationOutput(List<ProcessedRecord> processedRecords,
                                             List<RejectedRecord> rejectedRecords) {
    }
}
