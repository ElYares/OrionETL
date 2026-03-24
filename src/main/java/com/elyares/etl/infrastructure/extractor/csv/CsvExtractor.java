package com.elyares.etl.infrastructure.extractor.csv;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.shared.exception.ExtractionException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extractor de archivos CSV basado en OpenCSV.
 */
@Component
public class CsvExtractor implements DataExtractor {

    private static final String HEADER_MAPPING_PREFIX = "headerMapping.";
    private static final Set<String> DEFAULT_NULL_TOKENS = Set.of("", "NULL", "null", "N/A", "-");

    @Override
    public ExtractionResult extract(SourceConfig sourceConfig, PipelineExecution execution) {
        Path filePath = resolvePath(sourceConfig);
        Charset charset = Charset.forName(sourceConfig.getEncoding());
        char delimiter = sourceConfig.getDelimiter();
        char quoteChar = resolveQuoteChar(sourceConfig.getConnectionProperties());
        Set<String> nullTokens = resolveNullTokens(sourceConfig.getConnectionProperties());
        Map<String, String> headerMapping = resolveHeaderMapping(sourceConfig.getConnectionProperties());

        List<RawRecord> records = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(Files.newBufferedReader(filePath, charset))
            .withCSVParser(new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar(quoteChar)
                .build())
            .build()) {

            String[] headerRow = sourceConfig.isHasHeader() ? reader.readNext() : null;
            List<String> headers = normalizeHeaders(headerRow, headerMapping);
            String[] row;
            long rowNumber = sourceConfig.isHasHeader() ? 2L : 1L;

            while ((row = reader.readNext()) != null) {
                if (isEffectivelyEmptyRow(row, nullTokens)) {
                    rowNumber++;
                    continue;
                }
                Map<String, Object> data = buildRowData(headers, row, nullTokens);
                records.add(new RawRecord(rowNumber, data, filePath.toString(), Instant.now()));
                rowNumber++;
            }
        } catch (IOException | CsvValidationException e) {
            throw new ExtractionException("Failed to read CSV file: " + filePath, e);
        }

        return ExtractionResult.success(records, filePath.toString());
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return SourceType.CSV == sourceType;
    }

    private Path resolvePath(SourceConfig sourceConfig) {
        if (sourceConfig.getLocation() == null || sourceConfig.getLocation().isBlank()) {
            throw new ExtractionException("CSV source location is required");
        }
        Path path = Path.of(sourceConfig.getLocation());
        if (!Files.exists(path)) {
            throw new ExtractionException("CSV source file does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new ExtractionException("CSV source path is not a file: " + path);
        }
        return path;
    }

    private char resolveQuoteChar(Map<String, String> properties) {
        String value = properties.get("quoteChar");
        if (value == null || value.isBlank()) {
            return '"';
        }
        return value.charAt(0);
    }

    private Set<String> resolveNullTokens(Map<String, String> properties) {
        String value = properties.get("nullValues");
        if (value == null || value.isBlank()) {
            return DEFAULT_NULL_TOKENS;
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            tokens.add(token.trim());
        }
        return tokens;
    }

    private Map<String, String> resolveHeaderMapping(Map<String, String> properties) {
        Map<String, String> headerMapping = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(HEADER_MAPPING_PREFIX)) {
                String sourceHeader = entry.getKey().substring(HEADER_MAPPING_PREFIX.length());
                String targetHeader = entry.getValue();
                if (!sourceHeader.isBlank() && targetHeader != null && !targetHeader.isBlank()) {
                    headerMapping.put(sourceHeader, targetHeader);
                }
            }
        }
        return headerMapping;
    }

    private List<String> normalizeHeaders(String[] headerRow, Map<String, String> headerMapping) {
        if (headerRow == null || headerRow.length == 0) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(headerRow.length);
        for (int i = 0; i < headerRow.length; i++) {
            String current = normalizeCell(headerRow[i], Set.of());
            if (current == null || current.isBlank()) {
                current = "column_" + (i + 1);
            }
            String mapped = headerMapping.getOrDefault(current, current);
            headers.add(mapped);
        }
        return headers;
    }

    private Map<String, Object> buildRowData(List<String> headers, String[] row, Set<String> nullTokens) {
        int size = Math.max(headers.size(), row.length);
        Map<String, Object> data = new LinkedHashMap<>(size);

        for (int i = 0; i < size; i++) {
            String key = i < headers.size() ? headers.get(i) : "column_" + (i + 1);
            String value = i < row.length ? row[i] : null;
            data.put(key, normalizeCell(value, nullTokens));
        }
        return data;
    }

    private boolean isEffectivelyEmptyRow(String[] row, Set<String> nullTokens) {
        if (row.length == 0) {
            return true;
        }
        for (String value : row) {
            String normalized = normalizeCell(value, nullTokens);
            if (normalized != null && !normalized.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeCell(String rawValue, Set<String> nullTokens) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (nullTokens.contains(rawValue) || nullTokens.contains(trimmed)) {
            return null;
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
