package com.elyares.etl.infrastructure.loader.database;

import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.target.TargetConfig;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DatabaseLoadSupport {

    static final String TRACE_EXECUTION_ID = "etl_execution_id";
    static final String TRACE_PIPELINE_ID = "etl_pipeline_id";
    static final String TRACE_SOURCE_FILE = "etl_source_file";
    static final String TRACE_LOAD_TIMESTAMP = "etl_load_timestamp";
    static final String TRACE_PIPELINE_VERSION = "etl_pipeline_version";

    private DatabaseLoadSupport() {}

    static String qualifiedTable(TargetConfig targetConfig, String tableName) {
        return quoteIdentifier(targetConfig.getSchema()) + "." + quoteIdentifier(tableName);
    }

    static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    static String commaSeparatedQuoted(List<String> columns) {
        return columns.stream()
            .map(DatabaseLoadSupport::quoteIdentifier)
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    static List<String> resolveDataColumns(List<ProcessedRecord> records) {
        Set<String> ordered = new LinkedHashSet<>();
        for (ProcessedRecord record : records) {
            ordered.addAll(record.getData().keySet());
        }
        return List.copyOf(ordered);
    }

    static List<String> withTraceColumns(List<String> dataColumns) {
        List<String> columns = new ArrayList<>(dataColumns);
        columns.add(TRACE_EXECUTION_ID);
        columns.add(TRACE_PIPELINE_ID);
        columns.add(TRACE_SOURCE_FILE);
        columns.add(TRACE_LOAD_TIMESTAMP);
        columns.add(TRACE_PIPELINE_VERSION);
        return List.copyOf(columns);
    }

    static Object[] buildRowValues(List<String> dataColumns,
                                   ProcessedRecord record,
                                   PipelineExecution execution,
                                   Instant loadTimestamp) {
        List<Object> values = new ArrayList<>(dataColumns.size() + 5);
        for (String column : dataColumns) {
            values.add(normalizeJdbcValue(record.getData().get(column)));
        }
        values.add(UUID.fromString(execution.getExecutionId().toString()));
        values.add(UUID.fromString(execution.getPipelineId().toString()));
        values.add(record.getSourceReference());
        values.add(Timestamp.from(loadTimestamp));
        values.add(record.getPipelineVersion());
        return values.toArray();
    }

    static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static Object normalizeJdbcValue(Object value) {
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        if (value instanceof LocalDate localDate) {
            return Date.valueOf(localDate);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
