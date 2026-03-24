package com.elyares.etl.infrastructure.extractor.excel;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.shared.exception.ExtractionException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor para archivos Excel basado en Apache POI.
 */
@Component
public class ExcelExtractor implements DataExtractor {

    private static final String HEADER_MAPPING_PREFIX = "headerMapping.";

    @Override
    public ExtractionResult extract(SourceConfig sourceConfig, PipelineExecution execution) {
        Path filePath = resolvePath(sourceConfig);
        Map<String, String> properties = sourceConfig.getConnectionProperties();

        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = resolveSheet(workbook, properties);
            int headerRowIndex = resolveHeaderRowIndex(sourceConfig, properties);
            int dataRowIndex = resolveDataRowIndex(sourceConfig, properties, headerRowIndex);
            List<String> headers = resolveHeaders(sheet, headerRowIndex, sourceConfig.isHasHeader(), properties);

            List<RawRecord> records = new ArrayList<>();
            for (int rowIndex = dataRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row, evaluator)) {
                    continue;
                }
                Map<String, Object> data = buildRowData(row, headers, evaluator);
                records.add(new RawRecord(rowIndex + 1L, data, filePath.toString(), Instant.now()));
            }

            return ExtractionResult.success(records, filePath.toString());
        } catch (IOException ex) {
            throw new ExtractionException("Failed to read Excel file: " + filePath, ex);
        }
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return SourceType.EXCEL == sourceType;
    }

    private Path resolvePath(SourceConfig sourceConfig) {
        if (sourceConfig.getLocation() == null || sourceConfig.getLocation().isBlank()) {
            throw new ExtractionException("Excel source location is required");
        }
        Path path = Path.of(sourceConfig.getLocation());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ExtractionException("Excel source file does not exist: " + path);
        }
        return path;
    }

    private Sheet resolveSheet(Workbook workbook, Map<String, String> properties) {
        String sheetName = properties.get("sheetName");
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet byName = workbook.getSheet(sheetName);
            if (byName == null) {
                throw new ExtractionException("Excel sheet not found: " + sheetName);
            }
            return byName;
        }
        int sheetIndex = parseInt(properties.get("sheetIndex"), 0);
        if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
            throw new ExtractionException("Excel sheet index is out of range: " + sheetIndex);
        }
        return workbook.getSheetAt(sheetIndex);
    }

    private int resolveHeaderRowIndex(SourceConfig sourceConfig, Map<String, String> properties) {
        if (!sourceConfig.isHasHeader()) {
            return -1;
        }
        return Math.max(0, parseInt(properties.get("headerRowIndex"), 0));
    }

    private int resolveDataRowIndex(SourceConfig sourceConfig, Map<String, String> properties, int headerRowIndex) {
        if (properties.containsKey("dataStartRow")) {
            return Math.max(0, parseInt(properties.get("dataStartRow"), 1) - 1);
        }
        return sourceConfig.isHasHeader() ? headerRowIndex + 1 : 0;
    }

    private List<String> resolveHeaders(Sheet sheet,
                                        int headerRowIndex,
                                        boolean hasHeader,
                                        Map<String, String> properties) {
        if (!hasHeader) {
            return List.of();
        }
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            throw new ExtractionException("Excel header row not found at index: " + headerRowIndex);
        }
        Map<String, String> headerMapping = resolveHeaderMapping(properties);
        int lastCell = Math.max(headerRow.getLastCellNum(), 0);
        List<String> headers = new ArrayList<>(lastCell);
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < lastCell; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = cell != null ? formatter.formatCellValue(cell).trim() : "";
            if (header.isBlank()) {
                header = "column_" + (i + 1);
            }
            headers.add(headerMapping.getOrDefault(header, header));
        }
        return headers;
    }

    private Map<String, String> resolveHeaderMapping(Map<String, String> properties) {
        Map<String, String> mapping = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (key.startsWith(HEADER_MAPPING_PREFIX) && value != null && !value.isBlank()) {
                mapping.put(key.substring(HEADER_MAPPING_PREFIX.length()), value);
            }
        });
        return Map.copyOf(mapping);
    }

    private Map<String, Object> buildRowData(Row row,
                                             List<String> headers,
                                             FormulaEvaluator evaluator) {
        int lastCell = Math.max(row.getLastCellNum(), headers.size());
        Map<String, Object> data = new LinkedHashMap<>(lastCell);
        for (int i = 0; i < lastCell; i++) {
            String column = i < headers.size() ? headers.get(i) : "column_" + (i + 1);
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            data.put(column, readCellValue(cell, evaluator));
        }
        return data;
    }

    private boolean isEmptyRow(Row row, FormulaEvaluator evaluator) {
        int lastCell = Math.max(row.getLastCellNum(), 0);
        for (int i = 0; i < lastCell; i++) {
            Object value = readCellValue(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), evaluator);
            if (value != null && !(value instanceof String stringValue && stringValue.isBlank())) {
                return false;
            }
        }
        return true;
    }

    private Object readCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = evaluator.evaluateFormulaCell(cell);
        }
        return switch (cellType) {
            case STRING -> {
                String value = cell.getStringCellValue();
                yield value == null || value.trim().isEmpty() ? null : value.trim();
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> readNumericCell(cell);
            case BLANK, _NONE, ERROR -> null;
            default -> null;
        };
    }

    private Object readNumericCell(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime localDateTime = cell.getLocalDateTimeCellValue();
            if (localDateTime.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                return localDateTime.toLocalDate();
            }
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }

        double numeric = cell.getNumericCellValue();
        long integral = (long) numeric;
        if (Double.compare(numeric, integral) == 0) {
            return integral;
        }
        return BigDecimal.valueOf(numeric);
    }

    private int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw);
    }
}
