package com.elyares.etl.infrastructure.transformer.inventory;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transformador del pipeline de inventario.
 */
@Component
public class InventoryTransformer implements DataTransformer {

    private static final Map<String, String> WAREHOUSE_MAPPING = Map.ofEntries(
        Map.entry("WH001", "W-001"),
        Map.entry("CENTRAL", "W-001"),
        Map.entry("1", "W-001"),
        Map.entry("WH002", "W-002"),
        Map.entry("NORTH", "W-002"),
        Map.entry("2", "W-002"),
        Map.entry("WH003", "W-003"),
        Map.entry("SOUTH", "W-003"),
        Map.entry("3", "W-003")
    );

    private final CommonTransformer commonTransformer;

    public InventoryTransformer(CommonTransformer commonTransformer) {
        this.commonTransformer = commonTransformer;
    }

    @Override
    public TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution) {
        CommonTransformer.CommonTransformationOutput commonOutput = commonTransformer.transform(records, pipeline);
        Map<Long, RawRecord> rawByRow = records.stream()
            .collect(Collectors.toMap(RawRecord::getRowNumber, Function.identity(), (left, right) -> left));

        List<RejectedRecord> rejected = new ArrayList<>(commonOutput.rejectedRecords());
        Map<String, ConsolidatedInventory> consolidated = new LinkedHashMap<>();

        for (ProcessedRecord record : commonOutput.processedRecords()) {
            try {
                ProcessedRecord normalized = applyInventoryRules(record);
                String key = normalized.getData().get("sku") + "|" + normalized.getData().get("warehouse_id");
                consolidated.compute(key, (ignored, current) -> current == null
                    ? ConsolidatedInventory.from(normalized)
                    : current.merge(normalized));
            } catch (IllegalArgumentException ex) {
                RawRecord original = rawByRow.getOrDefault(
                    record.getSourceRowNumber(),
                    new RawRecord(record.getSourceRowNumber(), record.getData(), record.getSourceReference(), record.getTransformedAt())
                );
                rejected.add(new RejectedRecord(
                    original,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, original.getData(), "INVENTORY_TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        List<ProcessedRecord> processed = consolidated.values().stream()
            .map(ConsolidatedInventory::toProcessedRecord)
            .toList();

        return TransformationResult.of(processed, rejected);
    }

    private ProcessedRecord applyInventoryRules(ProcessedRecord record) {
        Map<String, Object> data = new LinkedHashMap<>(record.getData());
        String normalizedSku = normalizeSku(String.valueOf(data.get("sku")));
        String normalizedWarehouse = normalizeWarehouseId(String.valueOf(data.get("warehouse_id")));

        long onHand = toLong(data.get("quantity_on_hand"));
        long reserved = data.get("quantity_reserved") == null ? 0L : toLong(data.get("quantity_reserved"));
        if (onHand < 0 || reserved < 0) {
            throw new IllegalArgumentException("Inventory quantities must be non-negative");
        }
        if (reserved > onHand) {
            throw new IllegalArgumentException("Reserved quantity cannot exceed on-hand quantity");
        }

        data.put("sku", normalizedSku);
        data.put("warehouse_id", normalizedWarehouse);
        data.put("quantity_on_hand", onHand);
        data.put("quantity_reserved", reserved);

        return new ProcessedRecord(
            record.getSourceRowNumber(),
            data,
            record.getPipelineVersion(),
            record.getSourceReference(),
            record.getTransformedAt()
        );
    }

    private String normalizeSku(String rawSku) {
        String trimmed = StringUtils.trimToNull(rawSku);
        if (trimmed == null) {
            throw new IllegalArgumentException("SKU is required");
        }
        String normalized = trimmed.toUpperCase()
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");

        String[] segments = normalized.split("-");
        List<String> cleaned = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment.matches("\\d+")) {
                cleaned.add(String.valueOf(Integer.parseInt(segment)));
            } else {
                cleaned.add(segment);
            }
        }
        return String.join("-", cleaned);
    }

    private String normalizeWarehouseId(String rawWarehouseId) {
        String trimmed = StringUtils.trimToNull(rawWarehouseId);
        if (trimmed == null) {
            throw new IllegalArgumentException("warehouse_id is required");
        }
        String candidate = trimmed.toUpperCase();
        String mapped = WAREHOUSE_MAPPING.get(candidate);
        if (mapped == null) {
            throw new IllegalArgumentException("Unknown warehouse mapping for " + rawWarehouseId);
        }
        return mapped;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public String getPipelineName() {
        return "inventory-sync";
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private record ConsolidatedInventory(long sourceRowNumber,
                                         Map<String, Object> data,
                                         String pipelineVersion,
                                         String sourceReference,
                                         Instant transformedAt,
                                         LinkedHashSet<Long> sourceRows) {

        static ConsolidatedInventory from(ProcessedRecord record) {
            LinkedHashSet<Long> sourceRows = new LinkedHashSet<>();
            sourceRows.add(record.getSourceRowNumber());
            return new ConsolidatedInventory(
                record.getSourceRowNumber(),
                new LinkedHashMap<>(record.getData()),
                record.getPipelineVersion(),
                record.getSourceReference(),
                record.getTransformedAt(),
                sourceRows
            );
        }

        ConsolidatedInventory merge(ProcessedRecord record) {
            Map<String, Object> merged = new LinkedHashMap<>(data);
            merged.put("quantity_on_hand", toLong(merged.get("quantity_on_hand")) + toLong(record.getData().get("quantity_on_hand")));
            merged.put("quantity_reserved", toLong(merged.get("quantity_reserved")) + toLong(record.getData().get("quantity_reserved")));

            Instant currentLastUpdated = toInstant(merged.get("last_updated"));
            Instant incomingLastUpdated = toInstant(record.getData().get("last_updated"));
            if (incomingLastUpdated != null && (currentLastUpdated == null || incomingLastUpdated.isAfter(currentLastUpdated))) {
                merged.put("last_updated", record.getData().get("last_updated"));
                if (record.getData().containsKey("unit_cost")) {
                    merged.put("unit_cost", record.getData().get("unit_cost"));
                }
                if (record.getData().containsKey("unit_cost_original")) {
                    merged.put("unit_cost_original", record.getData().get("unit_cost_original"));
                }
                if (record.getData().containsKey("currency_original")) {
                    merged.put("currency_original", record.getData().get("currency_original"));
                }
                if (record.getData().containsKey("currency")) {
                    merged.put("currency", record.getData().get("currency"));
                }
            }

            LinkedHashSet<Long> mergedRows = new LinkedHashSet<>(sourceRows);
            mergedRows.add(record.getSourceRowNumber());
            return new ConsolidatedInventory(
                sourceRowNumber,
                merged,
                pipelineVersion,
                sourceReference,
                transformedAt,
                mergedRows
            );
        }

        ProcessedRecord toProcessedRecord() {
            return new ProcessedRecord(sourceRowNumber, data, pipelineVersion, sourceReference, transformedAt);
        }

        private static long toLong(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        }

        private static Instant toInstant(Object value) {
            if (value instanceof Instant instant) {
                return instant;
            }
            return null;
        }
    }
}
