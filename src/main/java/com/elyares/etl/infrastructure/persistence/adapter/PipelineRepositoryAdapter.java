package com.elyares.etl.infrastructure.persistence.adapter;

import com.elyares.etl.domain.contract.PipelineRepository;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.RollbackStrategy;
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
import com.elyares.etl.infrastructure.persistence.entity.EtlPipelineEntity;
import com.elyares.etl.infrastructure.persistence.mapper.PersistenceJsonMapper;
import com.elyares.etl.infrastructure.persistence.repository.JpaEtlPipelineRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class PipelineRepositoryAdapter implements PipelineRepository {

    private final JpaEtlPipelineRepository repository;
    private final PersistenceJsonMapper jsonMapper;

    public PipelineRepositoryAdapter(JpaEtlPipelineRepository repository,
                                     PersistenceJsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Pipeline save(Pipeline pipeline) {
        EtlPipelineEntity persisted = repository.save(toEntity(pipeline));
        return toDomain(persisted);
    }

    @Override
    public Optional<Pipeline> findById(PipelineId id) {
        return repository.findById(UUID.fromString(id.toString())).map(this::toDomain);
    }

    @Override
    public Optional<Pipeline> findByName(String name) {
        return repository.findByName(name).map(this::toDomain);
    }

    @Override
    public List<Pipeline> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Pipeline> findAllActive() {
        return repository.findByStatus(PipelineStatus.ACTIVE.name()).stream().map(this::toDomain).toList();
    }

    private EtlPipelineEntity toEntity(Pipeline pipeline) {
        return EtlPipelineEntity.builder()
            .id(UUID.fromString(pipeline.getId().toString()))
            .name(pipeline.getName())
            .version(pipeline.getVersion())
            .description(pipeline.getDescription())
            .status(pipeline.getStatus().name())
            .sourceType(pipeline.getSourceConfig().getType().name())
            .targetType(pipeline.getTargetConfig().getType().name())
            .configJson(jsonMapper.toJson(buildConfigJson(pipeline)))
            .createdAt(pipeline.getCreatedAt())
            .updatedAt(pipeline.getUpdatedAt())
            .build();
    }

    private Pipeline toDomain(EtlPipelineEntity entity) {
        Map<String, Object> config = jsonMapper.toMap(entity.getConfigJson());
        SourceConfig sourceConfig = parseSourceConfig(config.get("sourceConfig"));
        TargetConfig targetConfig = parseTargetConfig(config.get("targetConfig"));
        TransformationConfig transformationConfig = parseTransformationConfig(config.get("transformationConfig"));
        ValidationConfig validationConfig = parseValidationConfig(config.get("validationConfig"));
        ScheduleConfig scheduleConfig = parseScheduleConfig(config.get("scheduleConfig"));
        RetryPolicy retryPolicy = parseRetryPolicy(config.get("retryPolicy"));

        return new Pipeline(
            PipelineId.of(entity.getId().toString()),
            entity.getName(),
            entity.getVersion(),
            entity.getDescription(),
            PipelineStatus.valueOf(entity.getStatus()),
            sourceConfig,
            targetConfig,
            transformationConfig,
            validationConfig,
            scheduleConfig,
            retryPolicy,
            entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
            entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConfigJson(Pipeline pipeline) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        sourceConfig.put("type", pipeline.getSourceConfig().getType().name());
        sourceConfig.put("location", pipeline.getSourceConfig().getLocation());
        sourceConfig.put("encoding", pipeline.getSourceConfig().getEncoding());
        sourceConfig.put("delimiter", String.valueOf(pipeline.getSourceConfig().getDelimiter()));
        sourceConfig.put("hasHeader", pipeline.getSourceConfig().isHasHeader());
        sourceConfig.put("connectionProperties", pipeline.getSourceConfig().getConnectionProperties());
        root.put("sourceConfig", sourceConfig);

        Map<String, Object> targetConfig = new LinkedHashMap<>();
        targetConfig.put("type", pipeline.getTargetConfig().getType().name());
        targetConfig.put("schema", pipeline.getTargetConfig().getSchema());
        targetConfig.put("stagingTable", pipeline.getTargetConfig().getStagingTable());
        targetConfig.put("finalTable", pipeline.getTargetConfig().getFinalTable());
        targetConfig.put("loadStrategy", pipeline.getTargetConfig().getLoadStrategy().name());
        targetConfig.put("businessKeyColumns", pipeline.getTargetConfig().getBusinessKeyColumns());
        targetConfig.put("chunkSize", pipeline.getTargetConfig().getChunkSize());
        targetConfig.put("failFastOnChunkError", pipeline.getTargetConfig().isFailFastOnChunkError());
        targetConfig.put("rollbackStrategy", pipeline.getTargetConfig().getRollbackStrategy().name());
        targetConfig.put("closedRecordGuardEnabled", pipeline.getTargetConfig().isClosedRecordGuardEnabled());
        targetConfig.put("closedFlagColumn", pipeline.getTargetConfig().getClosedFlagColumn());
        targetConfig.put("closedFlagValue", pipeline.getTargetConfig().getClosedFlagValue());
        root.put("targetConfig", targetConfig);

        Map<String, Object> validationConfig = new LinkedHashMap<>();
        validationConfig.put("mandatoryColumns", pipeline.getValidationConfig().getMandatoryColumns());
        validationConfig.put("columnTypes", pipeline.getValidationConfig().getColumnTypes());
        validationConfig.put("uniqueKeyColumns", pipeline.getValidationConfig().getUniqueKeyColumns());
        validationConfig.put("columnPatterns", pipeline.getValidationConfig().getColumnPatterns());
        validationConfig.put("dateFormat", pipeline.getValidationConfig().getDateFormat());
        validationConfig.put("acceptIso8601Fallback", pipeline.getValidationConfig().isAcceptIso8601Fallback());
        validationConfig.put("amountFields", pipeline.getValidationConfig().getAmountFields());
        validationConfig.put("allowNegativeAmounts", pipeline.getValidationConfig().isAllowNegativeAmounts());
        validationConfig.put("rangeRules", pipeline.getValidationConfig().getRangeRules().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> Map.of(
                    "min", entry.getValue().min(),
                    "max", entry.getValue().max(),
                    "inclusive", entry.getValue().inclusive()
                ),
                (left, right) -> left,
                LinkedHashMap::new
            )));
        validationConfig.put("futureDateFields", pipeline.getValidationConfig().getFutureDateFields());
        validationConfig.put("futureDateRules", pipeline.getValidationConfig().getFutureDateRules().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> Map.of(
                    "allowedDaysInFuture", entry.getValue().allowedDaysInFuture(),
                    "conditionField", entry.getValue().conditionField(),
                    "allowedValues", entry.getValue().allowedValues()
                ),
                (left, right) -> left,
                LinkedHashMap::new
            )));
        validationConfig.put("catalogValues", pipeline.getValidationConfig().getCatalogValues());
        validationConfig.put("activeCatalogValues", pipeline.getValidationConfig().getActiveCatalogValues());
        validationConfig.put("rejectAllDuplicates", pipeline.getValidationConfig().isRejectAllDuplicates());
        validationConfig.put("errorThreshold", pipeline.getValidationConfig().getErrorThreshold().percentValue());
        validationConfig.put("abortOnThresholdBreach", pipeline.getValidationConfig().isAbortOnThresholdBreach());
        root.put("validationConfig", validationConfig);

        Map<String, Object> transformationConfig = new LinkedHashMap<>();
        // La configuración de transformación también viaja en JSONB para conservar el patrón
        // de pipeline declarativo que ya usa source/target/validation.
        transformationConfig.put("dateFields", pipeline.getTransformationConfig().getDateFields());
        transformationConfig.put("dateFormat", pipeline.getTransformationConfig().getDateFormat());
        transformationConfig.put("sourceTimezone", pipeline.getTransformationConfig().getSourceTimezone());
        transformationConfig.put("currencyField", pipeline.getTransformationConfig().getCurrencyField());
        transformationConfig.put("baseCurrency", pipeline.getTransformationConfig().getBaseCurrency());
        transformationConfig.put("defaultCurrency", pipeline.getTransformationConfig().getDefaultCurrency());
        transformationConfig.put("currencyRates", pipeline.getTransformationConfig().getCurrencyRates());
        transformationConfig.put("monetaryFields", pipeline.getTransformationConfig().getMonetaryFields());
        transformationConfig.put("nullValues", pipeline.getTransformationConfig().getNullValues());
        transformationConfig.put("codeMappings", pipeline.getTransformationConfig().getCodeMappings());
        transformationConfig.put("mappingPolicies", pipeline.getTransformationConfig().getMappingPolicies());
        transformationConfig.put("mappingDefaults", pipeline.getTransformationConfig().getMappingDefaults());
        transformationConfig.put("derivedColumns", pipeline.getTransformationConfig().getDerivedColumns());
        transformationConfig.put("roundingScale", pipeline.getTransformationConfig().getRoundingScale());
        transformationConfig.put("roundingMode", pipeline.getTransformationConfig().getRoundingMode().name());
        transformationConfig.put("roundingFields", pipeline.getTransformationConfig().getRoundingFields());
        root.put("transformationConfig", transformationConfig);

        List<Map<String, Object>> windows = pipeline.getScheduleConfig().getAllowedWindows().stream()
            .map(window -> Map.of(
                "start", window.start().toString(),
                "end", window.end().toString(),
                "days", window.days().stream().map(DayOfWeek::name).toList()
            ))
            .toList();

        Map<String, Object> scheduleConfig = new LinkedHashMap<>();
        scheduleConfig.put("cronExpression", pipeline.getScheduleConfig().getCronExpression());
        scheduleConfig.put("timezone", pipeline.getScheduleConfig().getTimezone());
        scheduleConfig.put("enabled", pipeline.getScheduleConfig().isEnabled());
        scheduleConfig.put("allowedWindows", windows);
        root.put("scheduleConfig", scheduleConfig);

        Map<String, Object> retryPolicy = new LinkedHashMap<>();
        retryPolicy.put("maxRetries", pipeline.getRetryPolicy().getMaxRetries());
        retryPolicy.put("retryDelayMs", pipeline.getRetryPolicy().getRetryDelayMs());
        retryPolicy.put("retryOnErrorTypes", pipeline.getRetryPolicy().getRetryOnErrorTypes().stream().map(Enum::name).toList());
        root.put("retryPolicy", retryPolicy);

        return root;
    }

    @SuppressWarnings("unchecked")
    private SourceConfig parseSourceConfig(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String delimiterValue = asString(m.getOrDefault("delimiter", ","));
        return new SourceConfig(
            SourceType.valueOf(asString(m.getOrDefault("type", SourceType.CSV.name()))),
            asString(m.get("location")),
            asString(m.getOrDefault("encoding", "UTF-8")),
            delimiterValue.isEmpty() ? ',' : delimiterValue.charAt(0),
            asBoolean(m.getOrDefault("hasHeader", Boolean.TRUE)),
            (Map<String, String>) m.getOrDefault("connectionProperties", Map.of())
        );
    }

    @SuppressWarnings("unchecked")
    private TargetConfig parseTargetConfig(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return new TargetConfig(
            TargetType.valueOf(asString(m.getOrDefault("type", TargetType.DATABASE.name()))),
            asString(m.getOrDefault("schema", "public")),
            asString(m.get("stagingTable")),
            asString(m.get("finalTable")),
            LoadStrategy.valueOf(asString(m.getOrDefault("loadStrategy", LoadStrategy.UPSERT.name()))),
            (List<String>) m.getOrDefault("businessKeyColumns", List.of()),
            asInt(m.getOrDefault("chunkSize", 1000)),
            asBoolean(m.getOrDefault("failFastOnChunkError", Boolean.TRUE)),
            RollbackStrategy.valueOf(asString(m.getOrDefault("rollbackStrategy", RollbackStrategy.DELETE_BY_EXECUTION.name()))),
            asBoolean(m.getOrDefault("closedRecordGuardEnabled", Boolean.TRUE)),
            asString(m.getOrDefault("closedFlagColumn", "status")),
            asString(m.getOrDefault("closedFlagValue", "CLOSED"))
        );
    }

    @SuppressWarnings("unchecked")
    private ValidationConfig parseValidationConfig(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return new ValidationConfig(
            (List<String>) m.getOrDefault("mandatoryColumns", List.of()),
            (Map<String, String>) m.getOrDefault("columnTypes", Map.of()),
            (List<String>) m.getOrDefault("uniqueKeyColumns", List.of()),
            (Map<String, String>) m.getOrDefault("columnPatterns", Map.of()),
            asString(m.get("dateFormat")),
            asBoolean(m.getOrDefault("acceptIso8601Fallback", Boolean.TRUE)),
            (List<String>) m.getOrDefault("amountFields", List.of()),
            asBoolean(m.getOrDefault("allowNegativeAmounts", Boolean.FALSE)),
            castRangeRules(m.get("rangeRules")),
            (List<String>) m.getOrDefault("futureDateFields", List.of()),
            castFutureDateRules(m.get("futureDateRules")),
            castMapOfSets(m.get("catalogValues")),
            castMapOfSets(m.get("activeCatalogValues")),
            asBoolean(m.getOrDefault("rejectAllDuplicates", Boolean.FALSE)),
            ErrorThreshold.of(asDouble(m.getOrDefault("errorThreshold", 5.0))),
            asBoolean(m.getOrDefault("abortOnThresholdBreach", Boolean.TRUE))
        );
    }

    @SuppressWarnings("unchecked")
    private TransformationConfig parseTransformationConfig(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        // Si el pipeline persistido todavía no tiene transformationConfig, se cae a defaults para
        // no romper compatibilidad con datos guardados antes de Fase 5.
        return new TransformationConfig(
            (List<String>) m.getOrDefault("dateFields", List.of()),
            asString(m.get("dateFormat")),
            asString(m.getOrDefault("sourceTimezone", "UTC")),
            asString(m.getOrDefault("currencyField", "currency")),
            asString(m.getOrDefault("baseCurrency", "USD")),
            asString(m.get("defaultCurrency")),
            castBigDecimalMap(m.get("currencyRates")),
            (List<String>) m.getOrDefault("monetaryFields", List.of()),
            (List<String>) m.getOrDefault("nullValues", List.of()),
            castNestedStringMap(m.get("codeMappings")),
            (Map<String, String>) m.getOrDefault("mappingPolicies", Map.of()),
            (Map<String, String>) m.getOrDefault("mappingDefaults", Map.of()),
            (Map<String, String>) m.getOrDefault("derivedColumns", Map.of()),
            asInt(m.getOrDefault("roundingScale", 2)),
            RoundingMode.valueOf(asString(m.getOrDefault("roundingMode", RoundingMode.HALF_UP.name()))),
            (List<String>) m.getOrDefault("roundingFields", List.of())
        );
    }

    @SuppressWarnings("unchecked")
    private ScheduleConfig parseScheduleConfig(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        List<Map<String, Object>> windows = (List<Map<String, Object>>) m.getOrDefault("allowedWindows", List.of());
        List<ScheduleConfig.AllowedWindow> allowedWindows = windows.stream().map(window -> {
            List<String> days = (List<String>) window.getOrDefault("days", List.of());
            List<DayOfWeek> dayValues = days.stream().map(DayOfWeek::valueOf).toList();
            return new ScheduleConfig.AllowedWindow(
                LocalTime.parse(asString(window.getOrDefault("start", "00:00"))),
                LocalTime.parse(asString(window.getOrDefault("end", "23:59"))),
                dayValues
            );
        }).toList();

        boolean enabled = asBoolean(m.getOrDefault("enabled", Boolean.FALSE));
        if (!enabled) {
            return ScheduleConfig.disabled();
        }
        return ScheduleConfig.of(
            asString(m.get("cronExpression")),
            asString(m.getOrDefault("timezone", "UTC")),
            allowedWindows
        );
    }

    @SuppressWarnings("unchecked")
    private RetryPolicy parseRetryPolicy(Object raw) {
        Map<String, Object> m = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        List<String> types = (List<String>) m.getOrDefault("retryOnErrorTypes", List.of());
        List<ErrorType> retryTypes = types.stream().map(ErrorType::valueOf).toList();
        return new RetryPolicy(
            asInt(m.getOrDefault("maxRetries", 0)),
            asLong(m.getOrDefault("retryDelayMs", 0L)),
            retryTypes
        );
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> castNestedStringMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Map<String, String>>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> castMapOfSets(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Set<String>>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, java.math.BigDecimal> castBigDecimalMap(Object value) {
        return value instanceof Map<?, ?> map ? ((Map<String, Object>) map).entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> new java.math.BigDecimal(String.valueOf(entry.getValue()))
            )) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValidationConfig.RangeRule> castRangeRules(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return ((Map<String, Object>) map).entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> {
                    Map<String, Object> rule = (Map<String, Object>) entry.getValue();
                    return new ValidationConfig.RangeRule(
                        new java.math.BigDecimal(String.valueOf(rule.get("min"))),
                        new java.math.BigDecimal(String.valueOf(rule.get("max"))),
                        asBoolean(rule.getOrDefault("inclusive", Boolean.TRUE))
                    );
                }
            ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValidationConfig.FutureDateRule> castFutureDateRules(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return ((Map<String, Object>) map).entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> {
                    Map<String, Object> rule = (Map<String, Object>) entry.getValue();
                    return new ValidationConfig.FutureDateRule(
                        asInt(rule.getOrDefault("allowedDaysInFuture", 0)),
                        asString(rule.get("conditionField")),
                        asStringSet(rule.get("allowedValues"))
                    );
                }
            ));
    }

    @SuppressWarnings("unchecked")
    private Set<String> asStringSet(Object value) {
        if (value instanceof Set<?> set) {
            return set.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (value instanceof Map<?, ?>) {
            return Map.<String, Object>of().keySet();
        }
        return Set.of();
    }
}
