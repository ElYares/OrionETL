package com.elyares.etl.e2e;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import com.elyares.etl.pipelines.inventory.InventoryPipelineConfig;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPipelineE2EIT extends PostgresIntegrationTestBase {

    private static final Path INVENTORY_FILE = createInventoryWorkbook();

    @DynamicPropertySource
    static void inventoryProps(DynamicPropertyRegistry registry) {
        registry.add("ORION_INVENTORY_SOURCE_PATH", () -> INVENTORY_FILE.toString());
    }

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private InventoryPipelineConfig inventoryPipelineConfig;

    @BeforeEach
    void cleanTablesAndRegisterPipeline() {
        jdbcTemplate.execute("TRUNCATE TABLE inventory_levels_staging, inventory_levels");
        inventoryPipelineConfig.registerIfMissing();
    }

    @Test
    void shouldExecuteInventoryPipelineAndConsolidateDuplicates() {
        Pipeline pipeline = inventoryPipelineConfig.registerIfMissing();

        PipelineExecutionDto execution = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "inventory-e2e")
        );

        assertThat(execution.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.totalRead()).isEqualTo(4);
        assertThat(execution.totalLoaded()).isEqualTo(2);
        assertThat(execution.totalRejected()).isEqualTo(0);

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_levels", Integer.class);
        Long qty = jdbcTemplate.queryForObject(
            "SELECT quantity_on_hand FROM inventory_levels WHERE sku = 'ABC-123' AND warehouse_id = 'W-001'",
            Long.class
        );
        String currency = jdbcTemplate.queryForObject(
            "SELECT cost_currency FROM inventory_levels WHERE sku = 'ABC-123' AND warehouse_id = 'W-001'",
            String.class
        );

        assertThat(rows).isEqualTo(2);
        assertThat(qty).isEqualTo(12L);
        assertThat(currency).isEqualTo("USD");
    }

    private static Path createInventoryWorkbook() {
        try {
            Path file = Files.createTempFile("inventory-e2e", ".xlsx");
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("Inventory");
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("sku");
                header.createCell(1).setCellValue("warehouse_id");
                header.createCell(2).setCellValue("quantity_on_hand");
                header.createCell(3).setCellValue("quantity_reserved");
                header.createCell(4).setCellValue("unit_cost");
                header.createCell(5).setCellValue("cost_currency");
                header.createCell(6).setCellValue("last_updated");

                Object[][] rows = {
                    {"abc 00123", "WH001", 5, 1, 10.50, "USD", java.sql.Date.valueOf(LocalDate.of(2026, 3, 23))},
                    {"ABC-123", "CENTRAL", 7, 2, 12.00, "USD", java.sql.Date.valueOf(LocalDate.of(2026, 3, 24))},
                    {"SKU-1", "WH002", 4, 0, 5.00, "USD", java.sql.Date.valueOf(LocalDate.of(2026, 3, 23))},
                    {"SKU-1", "WH002", 6, 0, 5.50, "USD", java.sql.Date.valueOf(LocalDate.of(2026, 3, 25))}
                };

                for (int i = 0; i < rows.length; i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < rows[i].length; j++) {
                        if (rows[i][j] instanceof Number number) {
                            row.createCell(j).setCellValue(number.doubleValue());
                        } else if (rows[i][j] instanceof java.util.Date date) {
                            row.createCell(j).setCellValue(date);
                            row.getCell(j).setCellStyle(dateStyle);
                        } else {
                            row.createCell(j).setCellValue(String.valueOf(rows[i][j]));
                        }
                    }
                }

                try (var output = Files.newOutputStream(file)) {
                    workbook.write(output);
                }
            }
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
