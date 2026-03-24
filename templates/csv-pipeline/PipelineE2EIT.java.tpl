package com.elyares.etl.e2e;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import com.elyares.etl.pipelines.${SCAFFOLD_SLUG}.${SCAFFOLD_CLASS_PREFIX}PipelineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plantilla E2E para validar que el pipeline completo registra, transforma y carga.
 */
class ${SCAFFOLD_CLASS_PREFIX}PipelineE2EIT extends PostgresIntegrationTestBase {

    private static final Path SOURCE_FILE = createCsv();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ORION_${SCAFFOLD_UPPER_SLUG}_SOURCE_PATH", () -> SOURCE_FILE.toString());
    }

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private ${SCAFFOLD_CLASS_PREFIX}PipelineConfig ${SCAFFOLD_SLUG}PipelineConfig;

    @BeforeEach
    void cleanTablesAndRegisterPipeline() {
        jdbcTemplate.execute("TRUNCATE TABLE ${SCAFFOLD_TABLE_PREFIX}_staging, ${SCAFFOLD_TABLE_PREFIX}");
        ${SCAFFOLD_SLUG}PipelineConfig.registerIfMissing();
    }

    @Test
    void shouldExecute${SCAFFOLD_CLASS_PREFIX}Pipeline() {
        Pipeline pipeline = ${SCAFFOLD_SLUG}PipelineConfig.registerIfMissing();

        PipelineExecutionDto execution = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "${SCAFFOLD_SLUG}-e2e")
        );

        assertThat(execution.status()).isIn(ExecutionStatus.SUCCESS, ExecutionStatus.PARTIAL);
    }

    private static Path createCsv() {
        try {
            Path file = Files.createTempFile("${SCAFFOLD_SLUG}-e2e", ".csv");
            Files.writeString(file, String.join(System.lineSeparator(),
                "${SCAFFOLD_BUSINESS_KEY}",
                "TODO-001"
            ));
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
