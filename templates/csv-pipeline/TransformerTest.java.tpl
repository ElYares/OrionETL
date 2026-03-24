package com.elyares.etl.unit.transformer;

import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import com.elyares.etl.infrastructure.transformer.${SCAFFOLD_SLUG}.${SCAFFOLD_CLASS_PREFIX}Transformer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plantilla base de unit test para el transformador del pipeline.
 */
class ${SCAFFOLD_CLASS_PREFIX}TransformerTest {

    @Test
    void shouldTransformBasicRecord() {
        ${SCAFFOLD_CLASS_PREFIX}Transformer transformer = new ${SCAFFOLD_CLASS_PREFIX}Transformer(new CommonTransformer());
        Pipeline pipeline = scaffoldPipeline();
        RawRecord record = SampleDataFactory.aRawRecord(2L, Map.of(
            "${SCAFFOLD_BUSINESS_KEY}", "TODO-001"
        ));

        var result = transformer.transform(List.of(record), pipeline, null);

        assertThat(result.getRejectedRecords()).isEmpty();
        assertThat(result.getProcessedRecords()).hasSize(1);
    }

    private Pipeline scaffoldPipeline() {
        return new Pipeline(
            PipelineId.generate(),
            "${SCAFFOLD_PIPELINE_NAME}",
            "1.0.0",
            "Scaffold pipeline test",
            PipelineStatus.ACTIVE,
            new SourceConfig(com.elyares.etl.domain.enums.SourceType.CSV, "/tmp/${SCAFFOLD_SLUG}.csv", "UTF-8", ',', true, Map.of()),
            new TargetConfig(TargetType.DATABASE, "public", "${SCAFFOLD_TABLE_PREFIX}_staging", "${SCAFFOLD_TABLE_PREFIX}", LoadStrategy.UPSERT, List.of("${SCAFFOLD_BUSINESS_KEY}"), 100),
            TransformationConfig.defaultConfig(),
            new ValidationConfig(
                List.of("${SCAFFOLD_BUSINESS_KEY}"),
                Map.of(),
                List.of("${SCAFFOLD_BUSINESS_KEY}"),
                ErrorThreshold.of(2.0),
                true
            ),
            ScheduleConfig.disabled(),
            RetryPolicy.noRetry(),
            Instant.now(),
            Instant.now()
        );
    }
}
