package com.elyares.etl.infrastructure.validator;

import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.model.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validador estructural de entrada.
 */
@Component("schemaValidator")
public class SchemaValidator implements DataValidator {

    @Override
    public ValidationResult validate(List<RawRecord> records, ValidationConfig config) {
        if (records == null || records.isEmpty()) {
            return ValidationResult.ok(List.of());
        }

        List<RawRecord> validRecords = new ArrayList<>();
        List<RejectedRecord> rejectedRecords = new ArrayList<>();
        List<ValidationError> allErrors = new ArrayList<>();

        List<String> missingColumns = config.getMandatoryColumns().stream()
            .filter(column -> records.stream().noneMatch(record -> record.hasField(column)))
            .toList();
        if (!missingColumns.isEmpty()) {
            // Si la columna falta a nivel dataset, todos los registros quedan inválidos aunque
            // individualmente tengan otros campos correctos.
            for (RawRecord record : records) {
                List<ValidationError> errors = missingColumns.stream()
                    .map(column -> ValidationError.critical(column, null, "MANDATORY_COLUMN_MISSING",
                        "Mandatory column is absent from dataset: " + column))
                    .toList();
                rejectedRecords.add(new RejectedRecord(record, "VALIDATE_SCHEMA",
                    "Mandatory columns missing from source header", errors));
                allErrors.addAll(errors);
            }
            return ValidationResult.batch(List.of(), rejectedRecords, allErrors, null);
        }

        for (RawRecord record : records) {
            List<ValidationError> recordErrors = new ArrayList<>();
            // Esta validación se limita a estructura: presencia, tipos y formato.
            validateMandatoryFields(record, config, recordErrors);
            validateTypes(record, config, recordErrors);
            validatePatterns(record, config, recordErrors);

            if (recordErrors.isEmpty()) {
                validRecords.add(record);
            } else {
                rejectedRecords.add(new RejectedRecord(record, "VALIDATE_SCHEMA", "Schema validation failed", recordErrors));
                allErrors.addAll(recordErrors);
            }
        }

        return ValidationResult.batch(validRecords, rejectedRecords, allErrors, null);
    }

    private void validateMandatoryFields(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        for (String column : config.getMandatoryColumns()) {
            Object value = record.getField(column);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                errors.add(ValidationError.critical(column, value, "NOT_NULL_OR_EMPTY",
                    "Mandatory field is null or empty"));
            }
        }
    }

    private void validateTypes(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        config.getColumnTypes().forEach((field, type) -> {
            Object value = record.getField(field);
            if (value == null) {
                return;
            }
            try {
                // El validador solo comprueba compatibilidad; la transformación real del valor
                // ocurre después en CommonTransformer.
                switch (type.toUpperCase()) {
                    case "INTEGER" -> Integer.parseInt(String.valueOf(value));
                    case "LONG" -> Long.parseLong(String.valueOf(value));
                    case "DECIMAL" -> new BigDecimal(String.valueOf(value));
                    case "DATE" -> parseDate(String.valueOf(value), config);
                    case "BOOLEAN" -> {
                        String normalized = String.valueOf(value).toLowerCase();
                        if (!List.of("true", "false").contains(normalized)) {
                            throw new IllegalArgumentException("Invalid boolean");
                        }
                    }
                    default -> { }
                }
            } catch (RuntimeException ex) {
                errors.add(ValidationError.critical(field, value, "TYPE_COMPATIBILITY",
                    "Value is incompatible with expected type " + type));
            }
        });
    }

    private void validatePatterns(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        config.getColumnPatterns().forEach((field, regex) -> {
            Object value = record.getField(field);
            if (value != null && !Pattern.compile(regex).matcher(String.valueOf(value)).matches()) {
                errors.add(ValidationError.error(field, value, "PATTERN_MISMATCH",
                    "Value does not match configured regex"));
            }
        });
    }

    private LocalDate parseDate(String value, ValidationConfig config) {
        if (config.getDateFormat() != null) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(config.getDateFormat()));
            } catch (DateTimeParseException ignored) {
                if (!config.isAcceptIso8601Fallback()) {
                    throw ignored;
                }
            }
        }
        return LocalDate.parse(value);
    }

    @Override
    public String getValidatorName() {
        return "schemaValidator";
    }
}
