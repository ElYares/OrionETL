package com.elyares.etl.integration.persistence;

import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.pipeline.RetryPolicy;
import com.elyares.etl.domain.model.pipeline.ScheduleConfig;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.transformation.TransformationConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;
import com.elyares.etl.domain.valueobject.PipelineId;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineRepositoryAdapterIT extends PostgresIntegrationTestBase {

    @Autowired
    private PipelineRepository pipelineRepository;

    @Test
    void shouldPersistAndLoadPipelineWithJsonbConfigRoundTrip() {
        Pipeline pipeline = buildPipeline("sales-nightly-it", PipelineStatus.ACTIVE);

        Pipeline saved = pipelineRepository.save(pipeline);
        Optional<Pipeline> loadedOptional = pipelineRepository.findById(saved.getId());

        assertThat(loadedOptional).isPresent();
        Pipeline loaded = loadedOptional.orElseThrow();

        assertThat(loaded.getName()).isEqualTo("sales-nightly-it");
        assertThat(loaded.getStatus()).isEqualTo(PipelineStatus.ACTIVE);
        assertThat(loaded.getSourceConfig().getType()).isEqualTo(SourceType.CSV);
        assertThat(loaded.getSourceConfig().getConnectionProperties())
            .containsEntry("compression", "gzip");
        assertThat(loaded.getTargetConfig().getLoadStrategy()).isEqualTo(LoadStrategy.UPSERT);
        assertThat(loaded.getValidationConfig().getErrorThreshold().percentValue()).isEqualTo(3.5);
        assertThat(loaded.getScheduleConfig().isEnabled()).isTrue();
        assertThat(loaded.getScheduleConfig().getAllowedWindows()).hasSize(1);
        assertThat(loaded.getRetryPolicy().getRetryOnErrorTypes())
            .containsExactly(ErrorType.TECHNICAL, ErrorType.EXTERNAL_INTEGRATION);

        String configType = jdbcTemplate.queryForObject(
            "select pg_typeof(config_json)::text from etl_pipelines where id = ?",
            String.class,
            UUID.fromString(saved.getId().toString())
        );
        String sourceLocation = jdbcTemplate.queryForObject(
            "select config_json->'sourceConfig'->>'location' from etl_pipelines where id = ?",
            String.class,
            UUID.fromString(saved.getId().toString())
        );

        assertThat(configType).isEqualTo("jsonb");
        assertThat(sourceLocation).isEqualTo("/data/input/sales.csv");
    }

    @Test
    void shouldFindByNameAndOnlyReturnActivePipelines() {
        Pipeline active = buildPipeline("active-pipeline-it", PipelineStatus.ACTIVE);
        Pipeline inactive = buildPipeline("inactive-pipeline-it", PipelineStatus.INACTIVE);

        pipelineRepository.save(active);
        pipelineRepository.save(inactive);

        Optional<Pipeline> byName = pipelineRepository.findByName("active-pipeline-it");
        List<Pipeline> all = pipelineRepository.findAll();
        List<Pipeline> activeOnly = pipelineRepository.findAllActive();

        assertThat(byName).isPresent();
        assertThat(byName.orElseThrow().getStatus()).isEqualTo(PipelineStatus.ACTIVE);
        assertThat(all).hasSize(2);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.getFirst().getName()).isEqualTo("active-pipeline-it");
    }

    private Pipeline buildPipeline(String name, PipelineStatus status) {
        PipelineId id = PipelineId.generate();
        Instant now = Instant.now();

        SourceConfig sourceConfig = new SourceConfig(
            SourceType.CSV,
            "/data/input/sales.csv",
            "UTF-8",
            ';',
            true,
            Map.of("compression", "gzip", "region", "mx-central")
        );

        TargetConfig targetConfig = new TargetConfig(
            TargetType.DATABASE,
            "public",
            "sales_staging",
            "sales_final",
            LoadStrategy.UPSERT,
            List.of("transaction_id"),
            500
        );

        ValidationConfig validationConfig = new ValidationConfig(
            List.of("transaction_id", "amount"),
            Map.of("amount", "DECIMAL", "event_date", "DATE"),
            List.of("transaction_id"),
            ErrorThreshold.of(3.5),
            true
        );

        ScheduleConfig scheduleConfig = ScheduleConfig.of(
            "0 0/15 * * * *",
            "America/Mexico_City",
            List.of(new ScheduleConfig.AllowedWindow(
                LocalTime.of(1, 0),
                LocalTime.of(5, 0),
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            ))
        );

        RetryPolicy retryPolicy = new RetryPolicy(
            2,
            1500L,
            List.of(ErrorType.TECHNICAL, ErrorType.EXTERNAL_INTEGRATION)
        );

        return new Pipeline(
            id,
            name,
            "1.2.0",
            "Pipeline de integración",
            status,
            sourceConfig,
            targetConfig,
            TransformationConfig.defaultConfig(),
            validationConfig,
            scheduleConfig,
            retryPolicy,
            now,
            now
        );
    }
}
