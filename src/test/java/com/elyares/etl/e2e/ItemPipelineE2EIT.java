package com.elyares.etl.e2e;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import com.elyares.etl.pipelines.item.ItemPipelineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPipelineE2EIT extends PostgresIntegrationTestBase {

    private static final Path ITEM_FILE = createItemCsv();

    @DynamicPropertySource
    static void itemProps(DynamicPropertyRegistry registry) {
        registry.add("ORION_ITEM_SOURCE_PATH", () -> ITEM_FILE.toString());
    }

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private ItemPipelineConfig itemPipelineConfig;

    @BeforeEach
    void cleanTablesAndRegisterPipeline() {
        jdbcTemplate.execute("TRUNCATE TABLE etl_items_staging, etl_items");
        itemPipelineConfig.registerIfMissing();
    }

    @Test
    void shouldExecuteItemPipelineEndToEnd() {
        Pipeline pipeline = itemPipelineConfig.registerIfMissing();

        PipelineExecutionDto execution = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "item-e2e")
        );

        assertThat(execution.status()).isEqualTo(ExecutionStatus.PARTIAL);
        assertThat(execution.totalRead()).isEqualTo(4);
        assertThat(execution.totalLoaded()).isEqualTo(3);
        assertThat(execution.totalRejected()).isEqualTo(1);

        Integer finalRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_items", Integer.class);
        Integer rejectedRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_rejected_records", Integer.class);
        String supplier = jdbcTemplate.queryForObject(
            "SELECT supplier_name FROM etl_items WHERE item_key = 'I00001'",
            String.class
        );
        String unit = jdbcTemplate.queryForObject(
            "SELECT unit FROM etl_items WHERE item_key = 'I00001'",
            String.class
        );

        assertThat(finalRows).isEqualTo(3);
        assertThat(rejectedRows).isEqualTo(1);
        assertThat(supplier).isEqualTo("Acme Imports");
        assertThat(unit).isEqualTo("cans");
    }

    private static Path createItemCsv() {
        try {
            Path file = Files.createTempFile("item-e2e", ".csv");
            Files.writeString(file, String.join(System.lineSeparator(),
                "item_key,item_name,desc,unit_price,man_country,supplier,unit",
                "I00001,Coke Classic 12 oz cans,a. Beverage - Soda,11.5,united states,acme imports,CANS",
                "I00002,Diet Coke 12 oz cans,a. Beverage - Soda,6.75,mexico,global supply,cans",
                "I00003,Sprite 12 oz cans,a. Beverage - Soda,-1.00,colombia,latin sourcing,cans",
                "I00004,Dr. Pepper 12 oz cans,a. Beverage - Soda,16.25,canada,north trade,CANS"
            ));
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
