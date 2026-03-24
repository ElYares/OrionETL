package com.elyares.etl.infrastructure.extractor.database;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.shared.exception.ExtractionException;
import org.springframework.jdbc.core.ArgumentTypePreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extractor JDBC para fuentes relacionales con soporte de named parameters y fetch size.
 */
@Component
public class DatabaseExtractor implements DataExtractor {

    private static final String QUERY_PARAM_PREFIX = "queryParam.";
    private static final String QUERY_PARAM_TYPE_PREFIX = "queryParamType.";
    private static final String QUERY_PARAM_TYPES_PREFIX = "queryParamTypes.";

    @Override
    public ExtractionResult extract(SourceConfig sourceConfig, PipelineExecution execution) {
        String jdbcUrl = resolveJdbcUrl(sourceConfig);
        Map<String, String> properties = sourceConfig.getConnectionProperties();
        String query = requireProperty(properties, "query");
        int fetchSize = parseInt(properties.get("fetchSize"), 500);
        int queryTimeoutSeconds = parseInt(properties.get("queryTimeoutSeconds"), 30);
        SqlParameterSource parameterSource = buildParameterSource(properties);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(buildDataSource(jdbcUrl, properties));
        jdbcTemplate.setQueryTimeout(queryTimeoutSeconds);

        ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(query);
        String sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
        Object[] values = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, null);
        int[] sqlTypes = NamedParameterUtils.buildSqlTypeArray(parsedSql, parameterSource);

        List<RawRecord> records = new ArrayList<>();

        try {
            jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sqlToUse);
                statement.setFetchSize(fetchSize);
                new ArgumentTypePreparedStatementSetter(values, sqlTypes).setValues(statement);
                return statement;
            }, resultSet -> {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                long rowNumber = 1L;

                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>(columnCount);
                    for (int index = 1; index <= columnCount; index++) {
                        String label = metaData.getColumnLabel(index);
                        if (label == null || label.isBlank()) {
                            label = metaData.getColumnName(index);
                        }
                        row.put(label, resultSet.getObject(index));
                    }
                    records.add(new RawRecord(rowNumber++, row, jdbcUrl, Instant.now()));
                }
                return null;
            });
        } catch (Exception exception) {
            throw new ExtractionException("Failed to execute database extraction query against: " + jdbcUrl, exception);
        }

        return ExtractionResult.success(records, jdbcUrl);
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return SourceType.DATABASE == sourceType;
    }

    private DataSource buildDataSource(String jdbcUrl, Map<String, String> properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(resolveSecret(properties, "username", "usernameEnv"));
        dataSource.setPassword(resolveSecret(properties, "password", "passwordEnv"));

        String driverClassName = properties.getOrDefault("driverClassName", inferDriverClassName(jdbcUrl));
        if (driverClassName != null && !driverClassName.isBlank()) {
            dataSource.setDriverClassName(driverClassName);
        }
        return dataSource;
    }

    private SqlParameterSource buildParameterSource(Map<String, String> properties) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!entry.getKey().startsWith(QUERY_PARAM_PREFIX)) {
                continue;
            }
            String parameterName = entry.getKey().substring(QUERY_PARAM_PREFIX.length());
            if (parameterName.isBlank()) {
                continue;
            }
            String type = properties.getOrDefault(
                QUERY_PARAM_TYPE_PREFIX + parameterName,
                properties.get(QUERY_PARAM_TYPES_PREFIX + parameterName)
            );
            parameterSource.addValue(parameterName, parseParameterValue(entry.getValue(), type));
        }

        return parameterSource;
    }

    private Object parseParameterValue(String rawValue, String declaredType) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }

        if (declaredType != null && !declaredType.isBlank()) {
            return parseTypedValue(trimmed, declaredType);
        }

        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        if (trimmed.matches("-?\\d+")) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private Object parseTypedValue(String value, String declaredType) {
        return switch (declaredType.trim().toUpperCase()) {
            case "STRING" -> value;
            case "INT", "INTEGER" -> Integer.parseInt(value);
            case "LONG", "BIGINT" -> Long.parseLong(value);
            case "DECIMAL", "BIGDECIMAL" -> new BigDecimal(value);
            case "DOUBLE" -> Double.parseDouble(value);
            case "BOOLEAN", "BOOL" -> Boolean.parseBoolean(value);
            case "UUID" -> UUID.fromString(value);
            case "DATE", "LOCALDATE" -> LocalDate.parse(value);
            case "TIMESTAMP", "LOCALDATETIME" -> LocalDateTime.parse(value);
            case "INSTANT" -> Instant.parse(value);
            default -> throw new ExtractionException("Unsupported database query parameter type: " + declaredType);
        };
    }

    private String resolveJdbcUrl(SourceConfig sourceConfig) {
        String jdbcUrl = sourceConfig.getLocation();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = sourceConfig.getConnectionProperties().get("jdbcUrl");
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new ExtractionException("Database source location or jdbcUrl property is required");
        }
        return jdbcUrl;
    }

    private String requireProperty(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new ExtractionException("Missing required database property: " + key);
        }
        return value;
    }

    private String resolveSecret(Map<String, String> properties, String literalKey, String envKey) {
        String literalValue = properties.get(literalKey);
        if (literalValue != null && !literalValue.isBlank()) {
            return literalValue;
        }
        String envName = properties.get(envKey);
        if (envName == null || envName.isBlank()) {
            return null;
        }
        return System.getenv(envName);
    }

    private String inferDriverClassName(String jdbcUrl) {
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return "";
    }

    private int parseInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new ExtractionException("Invalid integer property value: " + rawValue, exception);
        }
    }
}
