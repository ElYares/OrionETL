package com.elyares.etl.infrastructure.validator;

import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.model.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validador semántico y de reglas de negocio.
 */
@Component("businessValidator")
public class BusinessValidator implements DataValidator {

    @Override
    public ValidationResult validate(List<RawRecord> records, ValidationConfig config) {
        List<RawRecord> validRecords = new ArrayList<>();
        List<RejectedRecord> rejectedRecords = new ArrayList<>();
        List<ValidationError> allErrors = new ArrayList<>();

        Map<String, List<RawRecord>> duplicates = groupByBusinessKey(records, config.getUniqueKeyColumns());
        Set<Long> rejectAllRows = config.isRejectAllDuplicates()
            ? duplicates.values().stream().filter(list -> list.size() > 1).flatMap(List::stream).map(RawRecord::getRowNumber).collect(java.util.stream.Collectors.toSet())
            : Set.of();
        Set<Long> rejectSubsequentRows = new HashSet<>();
        if (!config.isRejectAllDuplicates()) {
            duplicates.values().stream().filter(list -> list.size() > 1).forEach(list ->
                list.stream().skip(1).forEach(record -> rejectSubsequentRows.add(record.getRowNumber())));
        }

        for (RawRecord record : records) {
            List<ValidationError> recordErrors = new ArrayList<>();
            validateAmounts(record, config, recordErrors);
            validateRanges(record, config, recordErrors);
            validateFutureDates(record, config, recordErrors);
            validateCatalogs(record, config, recordErrors);
            validateDuplicates(record, rejectAllRows, rejectSubsequentRows, config, recordErrors);

            if (recordErrors.isEmpty()) {
                validRecords.add(record);
            } else {
                rejectedRecords.add(new RejectedRecord(record, "VALIDATE_BUSINESS",
                    "Business validation failed", recordErrors));
                allErrors.addAll(recordErrors);
            }
        }

        return ValidationResult.batch(validRecords, rejectedRecords, allErrors, null);
    }

    private Map<String, List<RawRecord>> groupByBusinessKey(List<RawRecord> records, List<String> keyColumns) {
        if (keyColumns == null || keyColumns.isEmpty()) {
            return Map.of();
        }
        Map<String, List<RawRecord>> grouped = new HashMap<>();
        for (RawRecord record : records) {
            String key = keyColumns.stream().map(column -> String.valueOf(record.getField(column))).collect(java.util.stream.Collectors.joining("|"));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    private void validateAmounts(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        for (String field : config.getAmountFields()) {
            Object value = record.getField(field);
            if (value == null) {
                continue;
            }
            BigDecimal amount = toBigDecimal(value);
            if (!config.isAllowNegativeAmounts() && amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(ValidationError.critical(field, value, "NON_NEGATIVE_AMOUNT",
                    "Negative amount is not allowed"));
            }
        }
    }

    private void validateRanges(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        config.getRangeRules().forEach((field, rule) -> {
            Object value = record.getField(field);
            if (value == null) {
                return;
            }
            BigDecimal numericValue = toBigDecimal(value);
            boolean below = rule.inclusive() ? numericValue.compareTo(rule.min()) < 0 : numericValue.compareTo(rule.min()) <= 0;
            boolean above = rule.inclusive() ? numericValue.compareTo(rule.max()) > 0 : numericValue.compareTo(rule.max()) >= 0;
            if (below || above) {
                errors.add(ValidationError.critical(field, value, "RANGE_CHECK",
                    "Value is outside configured range"));
            }
        });
    }

    private void validateFutureDates(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        for (String field : config.getFutureDateFields()) {
            Object value = record.getField(field);
            if (value == null) {
                continue;
            }
            LocalDate date = toUtcDate(value);
            ValidationConfig.FutureDateRule rule = config.getFutureDateRules().get(field);
            int allowedDays = resolveAllowedFutureDays(record, rule);
            if (date.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(allowedDays))) {
                errors.add(ValidationError.critical(field, value, "FUTURE_DATE",
                    "Future dates are not allowed"));
            }
        }
    }

    private int resolveAllowedFutureDays(RawRecord record, ValidationConfig.FutureDateRule rule) {
        if (rule == null) {
            return 0;
        }
        if (rule.conditionField() == null || rule.conditionField().isBlank()) {
            return rule.allowedDaysInFuture();
        }
        Object candidateValue = record.getField(rule.conditionField());
        return rule.appliesTo(candidateValue) ? rule.allowedDaysInFuture() : 0;
    }

    private void validateCatalogs(RawRecord record, ValidationConfig config, List<ValidationError> errors) {
        config.getCatalogValues().forEach((field, catalog) -> {
            Object value = record.getField(field);
            if (value != null && !catalog.contains(String.valueOf(value))) {
                errors.add(ValidationError.critical(field, value, "CATALOG_LOOKUP",
                    "Value is not present in configured catalog"));
            }
        });
        config.getActiveCatalogValues().forEach((field, activeCatalog) -> {
            Object value = record.getField(field);
            if (value != null && !activeCatalog.contains(String.valueOf(value))) {
                errors.add(ValidationError.critical(field, value, "ACTIVE_REFERENCE",
                    "Reference is not active"));
            }
        });
    }

    private void validateDuplicates(RawRecord record,
                                    Set<Long> rejectAllRows,
                                    Set<Long> rejectSubsequentRows,
                                    ValidationConfig config,
                                    List<ValidationError> errors) {
        if (config.getUniqueKeyColumns().isEmpty()) {
            return;
        }
        boolean duplicate = rejectAllRows.contains(record.getRowNumber()) || rejectSubsequentRows.contains(record.getRowNumber());
        if (duplicate) {
            errors.add(ValidationError.critical(String.join(",", config.getUniqueKeyColumns()),
                record.getRowNumber(), "UNIQUE_BUSINESS_KEY", "Duplicate business key within batch"));
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDate toUtcDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    @Override
    public String getValidatorName() {
        return "businessValidator";
    }
}
