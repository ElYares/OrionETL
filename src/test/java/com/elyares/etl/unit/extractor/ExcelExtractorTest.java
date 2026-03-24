package com.elyares.etl.unit.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.excel.ExcelExtractor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExtractorTest {

    @Test
    void shouldReadExcelSheetByNameAndPreserveDateCellsAsLocalDate() throws IOException {
        Path workbookPath = createWorkbook();
        ExcelExtractor extractor = new ExcelExtractor();

        var result = extractor.extract(
            new SourceConfig(
                SourceType.EXCEL,
                workbookPath.toString(),
                "UTF-8",
                ',',
                true,
                Map.of("sheetName", "Inventory", "dataStartRow", "2")
            ),
            null
        );

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getField("sku")).isEqualTo("SKU-1");
        assertThat(result.getRecords().get(0).getField("last_updated")).isEqualTo(LocalDate.of(2026, 3, 23));
    }

    @Test
    void shouldSupportExcelSourceType() {
        assertThat(new ExcelExtractor().supports(SourceType.EXCEL)).isTrue();
    }

    private Path createWorkbook() throws IOException {
        Path file = Files.createTempFile("inventory-extractor-test", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Inventory");
            CellStyle dateStyle = workbook.createCellStyle();
            short format = workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd");
            dateStyle.setDataFormat(format);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("sku");
            header.createCell(1).setCellValue("warehouse_id");
            header.createCell(2).setCellValue("last_updated");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("SKU-1");
            row1.createCell(1).setCellValue("WH001");
            row1.createCell(2).setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 3, 23)));
            row1.getCell(2).setCellStyle(dateStyle);

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("SKU-2");
            row2.createCell(1).setCellValue("WH002");
            row2.createCell(2).setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 3, 24)));
            row2.getCell(2).setCellStyle(dateStyle);

            try (var outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }
}
