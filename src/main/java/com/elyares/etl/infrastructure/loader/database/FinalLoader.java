package com.elyares.etl.infrastructure.loader.database;

import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.RollbackStrategy;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.TargetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Promueve datos desde staging hacia la tabla final usando la estrategia configurada.
 */
@Component
public class FinalLoader {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public FinalLoader(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    public LoadResult promote(TargetConfig targetConfig, PipelineExecution execution) {
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> promoteInTransaction(targetConfig, execution));
        } catch (RuntimeException ex) {
            rollbackByExecution(targetConfig, execution);
            throw ex;
        }
    }

    public void rollbackByExecution(TargetConfig targetConfig, PipelineExecution execution) {
        if (targetConfig.getRollbackStrategy() != RollbackStrategy.DELETE_BY_EXECUTION) {
            return;
        }
        jdbcTemplate.update(
            "DELETE FROM " + DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getFinalTable()) +
                " WHERE etl_execution_id = ?",
            UUID.fromString(execution.getExecutionId().toString())
        );
    }

    private LoadResult promoteInTransaction(TargetConfig targetConfig, PipelineExecution execution) {
        return switch (targetConfig.getLoadStrategy()) {
            case INSERT -> promoteInsert(targetConfig, execution);
            case UPSERT -> promoteUpsert(targetConfig, execution);
            case REPLACE -> promoteReplace(targetConfig, execution);
        };
    }

    private LoadResult promoteInsert(TargetConfig targetConfig, PipelineExecution execution) {
        List<String> columns = loadFinalColumns(targetConfig);
        List<String> businessKeys = targetConfig.getBusinessKeyColumns();
        String sql = "INSERT INTO " + DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getFinalTable()) +
            " (" + DatabaseLoadSupport.commaSeparatedQuoted(columns) + ") " +
            "SELECT " + DatabaseLoadSupport.commaSeparatedQuoted(columns) +
            " FROM " + DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable()) +
            " WHERE etl_execution_id = ?" +
            (businessKeys.isEmpty()
                ? ""
                : " ON CONFLICT (" + DatabaseLoadSupport.commaSeparatedQuoted(businessKeys) + ") DO NOTHING");

        int inserted = jdbcTemplate.update(sql, UUID.fromString(execution.getExecutionId().toString()));
        long stagedCount = countStagedRows(targetConfig, execution);
        return LoadResult.success(inserted, 0, Math.max(0, stagedCount - inserted));
    }

    private LoadResult promoteUpsert(TargetConfig targetConfig, PipelineExecution execution) {
        List<String> columns = loadFinalColumns(targetConfig);
        List<String> businessKeys = targetConfig.getBusinessKeyColumns();
        List<String> updatableColumns = new ArrayList<>(columns);
        updatableColumns.removeAll(businessKeys);
        updatableColumns.remove("created_at");

        String updateAssignments = updatableColumns.stream()
            .map(column -> DatabaseLoadSupport.quoteIdentifier(column) + " = EXCLUDED." +
                DatabaseLoadSupport.quoteIdentifier(column))
            .reduce((left, right) -> left + ", " + right)
            .orElse("etl_execution_id = EXCLUDED.etl_execution_id");

        String finalTable = DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getFinalTable());
        String stagingTable = DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable());
        String finalTableForConflictWhere = DatabaseLoadSupport.quoteIdentifier(targetConfig.getFinalTable());
        String guardClause = targetConfig.isClosedRecordGuardEnabled()
            ? " WHERE " + finalTableForConflictWhere + "." +
                DatabaseLoadSupport.quoteIdentifier(targetConfig.getClosedFlagColumn()) +
                " IS DISTINCT FROM ?"
            : "";

        // `xmax = 0` nos permite distinguir inserts de updates en PostgreSQL sin una segunda consulta.
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
            "WITH upserted AS (" +
                " INSERT INTO " + finalTable +
                " (" + DatabaseLoadSupport.commaSeparatedQuoted(columns) + ") " +
                " SELECT " + DatabaseLoadSupport.commaSeparatedQuoted(columns) +
                " FROM " + stagingTable +
                " WHERE etl_execution_id = ? " +
                " ON CONFLICT (" + DatabaseLoadSupport.commaSeparatedQuoted(businessKeys) + ") DO UPDATE SET " +
                updateAssignments +
                guardClause +
                " RETURNING xmax = 0 AS inserted" +
                ") " +
                "SELECT inserted, COUNT(*) AS total FROM upserted GROUP BY inserted",
            targetConfig.isClosedRecordGuardEnabled()
                ? new Object[]{UUID.fromString(execution.getExecutionId().toString()), targetConfig.getClosedFlagValue()}
                : new Object[]{UUID.fromString(execution.getExecutionId().toString())}
        );

        long inserted = 0;
        long updated = 0;
        for (java.util.Map<String, Object> row : rows) {
            boolean rowInserted = Boolean.TRUE.equals(row.get("inserted"));
            long total = ((Number) row.get("total")).longValue();
            if (rowInserted) {
                inserted += total;
            } else {
                updated += total;
            }
        }
        return LoadResult.success(inserted, updated, 0);
    }

    private LoadResult promoteReplace(TargetConfig targetConfig, PipelineExecution execution) {
        String finalTable = DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getFinalTable());
        String stagingTable = DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable());
        List<String> columns = loadFinalColumns(targetConfig);

        jdbcTemplate.update("DELETE FROM " + finalTable);
        int inserted = jdbcTemplate.update(
            "INSERT INTO " + finalTable +
                " (" + DatabaseLoadSupport.commaSeparatedQuoted(columns) + ") " +
                "SELECT " + DatabaseLoadSupport.commaSeparatedQuoted(columns) +
                " FROM " + stagingTable +
                " WHERE etl_execution_id = ?",
            UUID.fromString(execution.getExecutionId().toString())
        );
        return LoadResult.success(inserted, 0, 0);
    }

    private long countStagedRows(TargetConfig targetConfig, PipelineExecution execution) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable()) +
                " WHERE etl_execution_id = ?",
            Long.class,
            UUID.fromString(execution.getExecutionId().toString())
        );
        return count != null ? count : 0L;
    }

    private List<String> loadFinalColumns(TargetConfig targetConfig) {
        return jdbcTemplate.queryForList("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = ?
            ORDER BY ordinal_position
            """, String.class, targetConfig.getSchema(), targetConfig.getFinalTable())
            .stream()
            .filter(column -> !"created_at".equals(column) && !"updated_at".equals(column))
            .toList();
    }
}
