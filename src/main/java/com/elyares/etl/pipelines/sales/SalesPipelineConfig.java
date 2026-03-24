package com.elyares.etl.pipelines.sales;

import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.RollbackStrategy;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga declarativa del pipeline de Sales desde YAML y lo registra en metadatos al arrancar.
 */
@Configuration
public class SalesPipelineConfig {

    private final PipelineRepository pipelineRepository;
    private final Environment environment;

    public SalesPipelineConfig(PipelineRepository pipelineRepository, Environment environment) {
        this.pipelineRepository = pipelineRepository;
        this.environment = environment;
    }

    @Bean("salesPipelineDefinition")
    Pipeline salesPipelineDefinition() {
        return loadSalesPipeline();
    }

    @Bean
    org.springframework.boot.ApplicationRunner salesPipelineRegistrar(
        @Qualifier("salesPipelineDefinition") Pipeline salesPipelineDefinition
    ) {
        return args -> registerIfMissing(salesPipelineDefinition);
    }

    public Pipeline registerIfMissing() {
        return registerIfMissing(loadSalesPipeline());
    }

    public Pipeline registerIfMissing(Pipeline pipeline) {
        return pipelineRepository.findById(pipeline.getId())
            .orElseGet(() -> pipelineRepository.save(pipeline));
    }

    public Pipeline loadSalesPipeline() {
        Map<String, Object> yaml = loadYaml();
        Map<String, Object> pipelineMap = asMap(yaml.get("pipeline"));

        PipelineId id = PipelineId.of(resolveString(pipelineMap.get("id")));
        String name = resolveString(pipelineMap.get("name"));
        String version = resolveString(pipelineMap.get("version"));
        String description = resolveString(pipelineMap.get("description"));
        PipelineStatus status = PipelineStatus.valueOf(resolveString(pipelineMap.getOrDefault("status", PipelineStatus.ACTIVE.name())));
        Instant now = Instant.now();

        return new Pipeline(
            id,
            name,
            version,
            description,
            status,
            parseSourceConfig(asMap(pipelineMap.get("source-config"))),
            parseTargetConfig(asMap(pipelineMap.get("target-config"))),
            parseTransformationConfig(asMap(pipelineMap.get("transformation-config"))),
            parseValidationConfig(asMap(pipelineMap.get("validation-config"))),
            parseScheduleConfig(asMap(pipelineMap.get("schedule-config"))),
            parseRetryPolicy(asMap(pipelineMap.get("retry-policy"))),
            now,
            now
        );
    }

    private Map<String, Object> loadYaml() {
        Resource resource = new ClassPathResource("pipelines/sales.yml");
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> yaml = factory.getObject();
        Assert.notNull(yaml, "sales.yml could not be loaded");
        return yaml;
    }

    private SourceConfig parseSourceConfig(Map<String, Object> sourceMap) {
        return new SourceConfig(
            SourceType.valueOf(resolveString(sourceMap.getOrDefault("type", SourceType.CSV.name()))),
            resolveString(sourceMap.get("location")),
            resolveString(sourceMap.getOrDefault("encoding", "UTF-8")),
            resolveString(sourceMap.getOrDefault("delimiter", ",")).charAt(0),
            asBoolean(sourceMap.getOrDefault("has-header", Boolean.TRUE)),
            asStringMap(sourceMap.get("connection-properties"))
        );
    }

    private TargetConfig parseTargetConfig(Map<String, Object> targetMap) {
        return new TargetConfig(
            TargetType.valueOf(resolveString(targetMap.getOrDefault("type", TargetType.DATABASE.name()))),
            resolveString(targetMap.getOrDefault("schema", "public")),
            resolveString(targetMap.get("staging-table")),
            resolveString(targetMap.get("final-table")),
            LoadStrategy.valueOf(resolveString(targetMap.getOrDefault("load-strategy", LoadStrategy.UPSERT.name()))),
            asStringList(targetMap.get("business-key")),
            asInt(targetMap.getOrDefault("chunk-size", 500)),
            asBoolean(targetMap.getOrDefault("fail-fast-on-chunk-error", Boolean.TRUE)),
            RollbackStrategy.valueOf(resolveString(targetMap.getOrDefault("rollback-strategy", RollbackStrategy.DELETE_BY_EXECUTION.name()))),
            asBoolean(targetMap.getOrDefault("closed-record-guard-enabled", Boolean.TRUE)),
            resolveString(targetMap.getOrDefault("closed-flag-column", "status")),
            resolveString(targetMap.getOrDefault("closed-flag-value", "CLOSED"))
        );
    }

    private TransformationConfig parseTransformationConfig(Map<String, Object> transformationMap) {
        return new TransformationConfig(
            asStringList(transformationMap.get("date-fields")),
            resolveString(transformationMap.get("date-format")),
            resolveString(transformationMap.getOrDefault("source-timezone", "UTC")),
            resolveString(transformationMap.getOrDefault("currency-field", "currency")),
            resolveString(transformationMap.getOrDefault("base-currency", "USD")),
            resolveString(transformationMap.get("default-currency")),
            asBigDecimalMap(transformationMap.get("currency-rates")),
            asStringList(transformationMap.get("monetary-fields")),
            asStringList(transformationMap.get("null-values")),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            asInt(transformationMap.getOrDefault("rounding-scale", 2)),
            RoundingMode.valueOf(resolveString(transformationMap.getOrDefault("rounding-mode", RoundingMode.HALF_UP.name()))),
            asStringList(transformationMap.get("rounding-fields"))
        );
    }

    private ValidationConfig parseValidationConfig(Map<String, Object> validationMap) {
        return new ValidationConfig(
            asStringList(validationMap.get("mandatory-columns")),
            asStringMap(validationMap.get("column-types")),
            asStringList(validationMap.get("unique-key-columns")),
            asStringMap(validationMap.get("column-patterns")),
            resolveString(validationMap.get("date-format")),
            asBoolean(validationMap.getOrDefault("accept-iso8601-fallback", Boolean.TRUE)),
            asStringList(validationMap.get("amount-fields")),
            asBoolean(validationMap.getOrDefault("allow-negative-amounts", Boolean.FALSE)),
            asRangeRules(validationMap.get("range-rules")),
            asStringList(validationMap.get("future-date-fields")),
            asFutureDateRules(validationMap.get("future-date-rules")),
            asSetMap(validationMap.get("catalog-values")),
            asSetMap(validationMap.get("active-catalog-values")),
            asBoolean(validationMap.getOrDefault("reject-all-duplicates", Boolean.FALSE)),
            ErrorThreshold.of(asDouble(validationMap.getOrDefault("error-threshold-percent", 5.0))),
            asBoolean(validationMap.getOrDefault("abort-on-threshold-breach", Boolean.TRUE))
        );
    }

    private ScheduleConfig parseScheduleConfig(Map<String, Object> scheduleMap) {
        if (scheduleMap.isEmpty()) {
            return ScheduleConfig.disabled();
        }
        return ScheduleConfig.of(
            resolveString(scheduleMap.get("cron")),
            resolveString(scheduleMap.getOrDefault("timezone", "UTC")),
            asAllowedWindows(scheduleMap.get("allowed-windows"))
        );
    }

    private RetryPolicy parseRetryPolicy(Map<String, Object> retryMap) {
        return new RetryPolicy(
            asInt(retryMap.getOrDefault("max-retries", 0)),
            asLong(retryMap.getOrDefault("retry-delay-ms", 0L)),
            asStringList(retryMap.get("retry-on-errors")).stream().map(ErrorType::valueOf).toList()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), resolveString(mapValue)));
        return Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> asSetMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), Set.copyOf(asStringList(mapValue))));
        return Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> asBigDecimalMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), new BigDecimal(String.valueOf(mapValue))));
        return Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValidationConfig.RangeRule> asRangeRules(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, ValidationConfig.RangeRule> normalized = new LinkedHashMap<>();
        map.forEach((key, ruleValue) -> {
            Map<String, Object> ruleMap = (Map<String, Object>) ruleValue;
            normalized.put(String.valueOf(key), new ValidationConfig.RangeRule(
                new BigDecimal(String.valueOf(ruleMap.get("min"))),
                new BigDecimal(String.valueOf(ruleMap.get("max"))),
                asBoolean(ruleMap.getOrDefault("inclusive", Boolean.TRUE))
            ));
        });
        return Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValidationConfig.FutureDateRule> asFutureDateRules(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, ValidationConfig.FutureDateRule> normalized = new LinkedHashMap<>();
        map.forEach((key, ruleValue) -> {
            Map<String, Object> ruleMap = (Map<String, Object>) ruleValue;
            normalized.put(String.valueOf(key), new ValidationConfig.FutureDateRule(
                asInt(ruleMap.getOrDefault("allowed-days-in-future", 0)),
                resolveString(ruleMap.get("condition-field")),
                Set.copyOf(asStringList(ruleMap.get("allowed-values")))
            ));
        });
        return Map.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private List<ScheduleConfig.AllowedWindow> asAllowedWindows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> {
            Map<String, Object> windowMap = (Map<String, Object>) item;
            return new ScheduleConfig.AllowedWindow(
                LocalTime.parse(resolveString(windowMap.get("start"))),
                LocalTime.parse(resolveString(windowMap.get("end"))),
                asStringList(windowMap.get("days")).stream().map(DayOfWeek::valueOf).toList()
            );
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::resolveString).toList();
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(this::resolveString).toList();
        }
        return List.of(resolveString(value));
    }

    private String resolveString(Object value) {
        return value == null ? null : environment.resolvePlaceholders(String.valueOf(value));
    }

    private boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }
}
