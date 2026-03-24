package com.elyares.etl.infrastructure.transformer.sales;

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
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
 * Transformaciones específicas del pipeline de Sales sobre la salida ya normalizada
 * por {@link CommonTransformer}.
 */
@Component
public class SalesTransformer implements DataTransformer {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.19");
    private static final Map<String, String> CHANNEL_MAPPING = Map.ofEntries(
        Map.entry("1", "ONLINE"),
        Map.entry("ONLINE", "ONLINE"),
        Map.entry("2", "IN_STORE"),
        Map.entry("STORE", "IN_STORE"),
        Map.entry("IN_STORE", "IN_STORE"),
        Map.entry("3", "PHONE"),
        Map.entry("PHONE", "PHONE"),
        Map.entry("4", "PARTNER"),
        Map.entry("PARTNER", "PARTNER"),
        Map.entry("5", "MARKETPLACE"),
        Map.entry("MARKETPLACE", "MARKETPLACE")
    );

    private final CommonTransformer commonTransformer;

    public SalesTransformer(CommonTransformer commonTransformer) {
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
                processed.add(applySalesRules(record));
            } catch (IllegalArgumentException ex) {
                RawRecord original = rawByRow.getOrDefault(
                    record.getSourceRowNumber(),
                    new RawRecord(record.getSourceRowNumber(), record.getData(), record.getSourceReference(), record.getTransformedAt())
                );
                rejected.add(new RejectedRecord(
                    original,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, original.getData(), "SALES_TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        return TransformationResult.of(processed, rejected);
    }

    private ProcessedRecord applySalesRules(ProcessedRecord record) {
        Map<String, Object> data = new LinkedHashMap<>(record.getData());

        BigDecimal amount = toBigDecimal(data.get("amount"));
        int quantity = data.get("product_quantity") == null ? 1 : Integer.parseInt(String.valueOf(data.get("product_quantity")));
        BigDecimal discountRate = data.get("discount_rate") == null ? BigDecimal.ZERO : toBigDecimal(data.get("discount_rate"));

        data.put("channel", mapChannel(data.get("channel")));
        data.put("quantity", quantity);
        data.remove("product_quantity");
        data.put("discount_rate", scale(discountRate));
        data.put("status", data.getOrDefault("status", "OPEN"));

        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(discountRate.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal subtotal = amount.multiply(BigDecimal.valueOf(quantity)).multiply(discountMultiplier);
        BigDecimal taxAmount = subtotal.multiply(DEFAULT_TAX_RATE);
        BigDecimal totalAmount = subtotal.add(taxAmount);

        data.put("subtotal", scale(subtotal));
        data.put("tax_amount", scale(taxAmount));
        data.put("total_amount", scale(totalAmount));

        return new ProcessedRecord(
            record.getSourceRowNumber(),
            data,
            record.getPipelineVersion(),
            record.getSourceReference(),
            record.getTransformedAt()
        );
    }

    private String mapChannel(Object rawValue) {
        String normalized = String.valueOf(rawValue).trim().toUpperCase();
        String mapped = CHANNEL_MAPPING.get(normalized);
        if (mapped == null) {
            throw new IllegalArgumentException("Missing channel mapping for value " + rawValue);
        }
        return mapped;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getPipelineName() {
        return "sales-daily";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
