package com.elyares.etl.infrastructure.transformer.item;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transformador específico para item-sync.
 *
 * Reutiliza CommonTransformer para no duplicar normalización genérica y luego aplica
 * solo las reglas particulares del dominio item: clave, nombre, precio, país,
 * proveedor y unidad.
 */
@Component
public class ItemTransformer implements DataTransformer {

    private final CommonTransformer commonTransformer;

    public ItemTransformer(CommonTransformer commonTransformer) {
        this.commonTransformer = commonTransformer;
    }

    @Override
    public TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution) {
        CommonTransformer.CommonTransformationOutput commonOutput = commonTransformer.transform(records, pipeline);
        Map<Long, RawRecord> rawByRow = records.stream()
            .collect(Collectors.toMap(RawRecord::getRowNumber, Function.identity(), (left, right) -> left));

        // Los rechazados del CommonTransformer se preservan y luego se agregan los que fallen
        // en reglas específicas del pipeline.
        List<ProcessedRecord> processed = new ArrayList<>();
        List<RejectedRecord> rejected = new ArrayList<>(commonOutput.rejectedRecords());

        for (ProcessedRecord record : commonOutput.processedRecords()) {
            try {
                processed.add(applyItemRules(record));
            } catch (IllegalArgumentException ex) {
                RawRecord original = rawByRow.getOrDefault(
                    record.getSourceRowNumber(),
                    new RawRecord(record.getSourceRowNumber(), record.getData(), record.getSourceReference(), record.getTransformedAt())
                );
                rejected.add(new RejectedRecord(
                    original,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, original.getData(), "ITEM_TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        return TransformationResult.of(processed, rejected);
    }

    private ProcessedRecord applyItemRules(ProcessedRecord record) {
        // Se parte de una copia mutable porque el ProcessedRecord original es inmutable.
        Map<String, Object> data = new LinkedHashMap<>(record.getData());

        String itemKey = normalizeRequiredUpperCode(data.get("item_key"), "item_key");
        String itemName = normalizeRequiredText(data.get("item_name"), "item_name");
        BigDecimal unitPrice = normalizeUnitPrice(data.get("unit_price"));
        String description = normalizeOptionalText(data.get("description"));
        String manufacturerCountry = normalizeOptionalTitle(data.get("manufacturer_country"));
        String supplierName = normalizeOptionalTitle(data.get("supplier_name"));
        String unit = normalizeOptionalLower(data.get("unit"));

        data.put("item_key", itemKey);
        data.put("item_name", itemName);
        // El precio se persiste ya escalado para evitar discrepancias entre transformación,
        // staging y validación posterior.
        data.put("unit_price", unitPrice.setScale(2, RoundingMode.HALF_UP));
        data.put("description", description);
        data.put("manufacturer_country", manufacturerCountry);
        data.put("supplier_name", supplierName);
        data.put("unit", unit);

        return new ProcessedRecord(
            record.getSourceRowNumber(),
            data,
            record.getPipelineVersion(),
            record.getSourceReference(),
            record.getTransformedAt()
        );
    }

    private String normalizeRequiredUpperCode(Object value, String fieldName) {
        String candidate = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (candidate == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return candidate.toUpperCase();
    }

    private String normalizeRequiredText(Object value, String fieldName) {
        String candidate = normalizeOptionalText(value);
        if (candidate == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return candidate;
    }

    private String normalizeOptionalText(Object value) {
        String candidate = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (candidate == null) {
            return null;
        }
        return candidate.replaceAll("\\s+", " ");
    }

    private String normalizeOptionalTitle(Object value) {
        String candidate = normalizeOptionalText(value);
        if (candidate == null) {
            return null;
        }
        return StringUtils.toTitleCase(candidate);
    }

    private String normalizeOptionalLower(Object value) {
        String candidate = normalizeOptionalText(value);
        return candidate == null ? null : candidate.toLowerCase();
    }

    private BigDecimal normalizeUnitPrice(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("unit_price is required");
        }
        BigDecimal parsed;
        try {
            parsed = value instanceof BigDecimal bigDecimal
                ? bigDecimal
                : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("unit_price must be a valid decimal");
        }
        if (parsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("unit_price must be non-negative");
        }
        return parsed;
    }

    @Override
    public String getPipelineName() {
        return "item-sync";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
