package com.elyares.etl.infrastructure.loader.database;

import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.StagingValidationResult;
import com.elyares.etl.domain.model.target.TargetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Valida la consistencia del lote persistido en staging antes de promoverlo.
 */
@Component
public class StagingValidator {

    private final JdbcTemplate jdbcTemplate;

    public StagingValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StagingValidationResult validate(TargetConfig targetConfig,
                                            PipelineExecution execution,
                                            long expectedRows) {
        String stagingTable = DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable());
        UUID executionRef = UUID.fromString(execution.getExecutionId().toString());
        List<String> details = new ArrayList<>();

        Long actualRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + stagingTable + " WHERE etl_execution_id = ?",
            Long.class,
            executionRef
        );
        long actual = actualRows != null ? actualRows : 0L;
        if (actual != expectedRows) {
            details.add("Row count mismatch: expected=" + expectedRows + ", actual=" + actual);
        }

        for (String column : findNonNullableColumns(targetConfig)) {
            Long nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + stagingTable + " WHERE etl_execution_id = ? AND " +
                    DatabaseLoadSupport.quoteIdentifier(column) + " IS NULL",
                Long.class,
                executionRef
            );
            if (nullCount != null && nullCount > 0) {
                details.add("Mandatory staging column has null values: " + column + " (" + nullCount + " rows)");
            }
        }

        if (targetConfig.getLoadStrategy() == LoadStrategy.UPSERT && !targetConfig.getBusinessKeyColumns().isEmpty()) {
            // Un UPSERT con duplicados dentro del mismo staging genera resultados no deterministas;
            // se bloquea antes de tocar la tabla final.
            String groupBy = DatabaseLoadSupport.commaSeparatedQuoted(targetConfig.getBusinessKeyColumns());
            Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                    "SELECT " + groupBy +
                    " FROM " + stagingTable +
                    " WHERE etl_execution_id = ?" +
                    " GROUP BY " + groupBy +
                    " HAVING COUNT(*) > 1" +
                    ") duplicated_keys",
                Integer.class,
                executionRef
            );
            if (duplicates != null && duplicates > 0) {
                details.add("Business key duplicates found in staging: " + duplicates);
            }
        }

        if (details.isEmpty()) {
            return StagingValidationResult.success(expectedRows, actual);
        }
        return StagingValidationResult.failure(expectedRows, actual, details);
    }

    private List<String> findNonNullableColumns(TargetConfig targetConfig) {
        return jdbcTemplate.queryForList("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = ?
              AND is_nullable = 'NO'
            ORDER BY ordinal_position
            """, String.class, targetConfig.getSchema(), targetConfig.getStagingTable())
            .stream()
            .filter(column -> !"etl_execution_id".equals(column))
            .toList();
    }
}
