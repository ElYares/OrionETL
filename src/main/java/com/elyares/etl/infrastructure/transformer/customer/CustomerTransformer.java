package com.elyares.etl.infrastructure.transformer.customer;

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import com.elyares.etl.shared.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transformador del pipeline de clientes.
 */
@Component
public class CustomerTransformer implements DataTransformer {

    private static final Map<String, String> STATUS_MAPPING = Map.of(
        "A", "ACTIVE",
        "I", "INACTIVE",
        "S", "SUSPENDED",
        "C", "CLOSED",
        "ACTIVE", "ACTIVE",
        "INACTIVE", "INACTIVE",
        "SUSPENDED", "SUSPENDED",
        "CLOSED", "CLOSED"
    );

    private static final Map<String, String> COUNTRY_MAPPING = Map.ofEntries(
        Map.entry("CO", "CO"),
        Map.entry("COL", "CO"),
        Map.entry("170", "CO"),
        Map.entry("US", "US"),
        Map.entry("USA", "US"),
        Map.entry("840", "US"),
        Map.entry("MX", "MX"),
        Map.entry("MEX", "MX"),
        Map.entry("484", "MX")
    );

    private static final Map<String, String> COUNTRY_DIAL_CODES = Map.of(
        "CO", "57",
        "US", "1",
        "MX", "52"
    );

    private final CommonTransformer commonTransformer;

    public CustomerTransformer(CommonTransformer commonTransformer) {
        this.commonTransformer = commonTransformer;
    }

    @Override
    public TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution) {
        CommonTransformer.CommonTransformationOutput commonOutput = commonTransformer.transform(records, pipeline);
        Map<Long, RawRecord> rawByRow = records.stream()
            .collect(Collectors.toMap(RawRecord::getRowNumber, Function.identity(), (left, right) -> left));

        List<ProcessedRecord> processed = new ArrayList<>();
        List<RejectedRecord> rejected = new ArrayList<>(commonOutput.rejectedRecords());

        for (ProcessedRecord record : commonOutput.processedRecords()) {
            try {
                processed.add(applyCustomerRules(record));
            } catch (IllegalArgumentException ex) {
                RawRecord original = rawByRow.getOrDefault(
                    record.getSourceRowNumber(),
                    new RawRecord(record.getSourceRowNumber(), record.getData(), record.getSourceReference(), record.getTransformedAt())
                );
                rejected.add(new RejectedRecord(
                    original,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, original.getData(), "CUSTOMER_TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        return TransformationResult.of(processed, rejected);
    }

    private ProcessedRecord applyCustomerRules(ProcessedRecord record) {
        Map<String, Object> data = new LinkedHashMap<>(record.getData());
        remapCrmCustomerId(data);

        String countryCode = normalizeCountryCode(String.valueOf(data.get("country_code")));
        String email = normalizeEmail(String.valueOf(data.get("email")));
        String documentType = normalizeCode(String.valueOf(data.get("document_type")));
        String status = normalizeStatus(String.valueOf(data.get("status")));
        String firstName = normalizeName(String.valueOf(data.get("first_name")));
        String lastName = normalizeName(String.valueOf(data.get("last_name")));
        String phone = normalizePhone(data.get("phone"), countryCode);

        data.put("country_code", countryCode);
        data.put("email", email);
        data.put("document_type", documentType);
        data.put("status", status);
        data.put("first_name", firstName);
        data.put("last_name", lastName);
        data.put("phone", phone);
        data.put("customer_type", data.getOrDefault("customer_type", "INDIVIDUAL"));

        validateBirthDate(data.get("birth_date"));

        return new ProcessedRecord(
            record.getSourceRowNumber(),
            data,
            record.getPipelineVersion(),
            record.getSourceReference(),
            record.getTransformedAt()
        );
    }

    private void remapCrmCustomerId(Map<String, Object> data) {
        if (data.containsKey("crm_customer_id")) {
            return;
        }
        Object customerId = data.remove("customer_id");
        if (customerId != null) {
            data.put("crm_customer_id", customerId);
        }
    }

    private String normalizeName(String rawValue) {
        String cleaned = StringUtils.trimToNull(rawValue);
        if (cleaned == null) {
            throw new IllegalArgumentException("Customer name fields are mandatory");
        }
        cleaned = cleaned.replaceAll("[^\\p{L}\\s\\-']", " ").replaceAll("\\s+", " ").trim();
        String[] words = cleaned.toLowerCase().split("\\s+");
        List<String> normalized = new ArrayList<>(words.length);
        for (String word : words) {
            normalized.add(titleCaseWithDelimiters(word));
        }
        return String.join(" ", normalized);
    }

    private String titleCaseWithDelimiters(String value) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char current : value.toCharArray()) {
            if (current == '\'' || current == '-') {
                result.append(current);
                capitalizeNext = true;
                continue;
            }
            result.append(capitalizeNext ? Character.toUpperCase(current) : current);
            capitalizeNext = false;
        }
        return result.toString();
    }

    private String normalizeEmail(String rawValue) {
        String email = StringUtils.normalizeEmail(rawValue);
        if (!StringUtils.isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email;
    }

    private String normalizeCountryCode(String rawValue) {
        String candidate = normalizeCode(rawValue);
        String mapped = COUNTRY_MAPPING.get(candidate);
        if (mapped == null) {
            throw new IllegalArgumentException("Unknown country code: " + rawValue);
        }
        return mapped;
    }

    private String normalizeStatus(String rawValue) {
        String candidate = normalizeCode(rawValue);
        String mapped = STATUS_MAPPING.get(candidate);
        if (mapped == null) {
            throw new IllegalArgumentException("Unknown customer status: " + rawValue);
        }
        return mapped;
    }

    private String normalizeCode(String rawValue) {
        String candidate = StringUtils.trimToNull(rawValue);
        if (candidate == null) {
            throw new IllegalArgumentException("Required code field is null");
        }
        return candidate.toUpperCase();
    }

    private String normalizePhone(Object rawValue, String countryCode) {
        if (rawValue == null) {
            return null;
        }
        String value = StringUtils.trimToNull(String.valueOf(rawValue));
        if (value == null) {
            return null;
        }
        String digits = value.startsWith("+")
            ? "+" + value.substring(1).replaceAll("\\D", "")
            : value.replaceAll("\\D", "");
        if (digits.isBlank() || "+".equals(digits)) {
            return null;
        }
        if (digits.startsWith("+")) {
            return digits;
        }
        String dialCode = COUNTRY_DIAL_CODES.get(countryCode);
        if (dialCode == null) {
            return null;
        }
        if (digits.startsWith(dialCode)) {
            return "+" + digits;
        }
        return "+" + dialCode + digits;
    }

    private void validateBirthDate(Object birthDate) {
        if (!(birthDate instanceof Instant instant)) {
            return;
        }
        LocalDate localDate = instant.atZone(ZoneOffset.UTC).toLocalDate();
        long years = ChronoUnit.YEARS.between(localDate, LocalDate.now(ZoneOffset.UTC));
        if (years < 0 || years > 120) {
            throw new IllegalArgumentException("Birth date is outside valid age range");
        }
    }

    @Override
    public String getPipelineName() {
        return "customer-sync";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
