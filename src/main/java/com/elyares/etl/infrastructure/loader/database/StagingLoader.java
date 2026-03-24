package com.elyares.etl.infrastructure.loader.database;

import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.shared.constants.StepNames;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Carga registros procesados a staging en chunks independientes.
 */
@Component
public class StagingLoader {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public StagingLoader(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    public StagingLoadResult load(List<ProcessedRecord> records, TargetConfig targetConfig, PipelineExecution execution) {
        if (!targetConfig.hasStagingTable()) {
            return StagingLoadResult.failure(0, 0, "Staging table is required for database loads");
        }

        clearStaging(targetConfig);
        if (records == null || records.isEmpty()) {
            return StagingLoadResult.success(0, 0);
        }

        List<String> dataColumns = DatabaseLoadSupport.resolveDataColumns(records);
        List<String> allColumns = DatabaseLoadSupport.withTraceColumns(dataColumns);
        String sql = buildInsertSql(targetConfig, allColumns);

        long stagedCount = 0;
        long rejectedCount = 0;
        int chunkSize = targetConfig.getChunkSize();

        for (int start = 0; start < records.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, records.size());
            List<ProcessedRecord> chunk = records.subList(start, end);
            Instant loadTimestamp = Instant.now();
            try {
                // Cada chunk va en su propia transacción para aislar fallos y permitir continuidad
                // cuando el pipeline no está en modo fail-fast.
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(
                    sql,
                    chunk.stream()
                        .map(record -> DatabaseLoadSupport.buildRowValues(dataColumns, record, execution, loadTimestamp))
                        .toList()
                ));
                stagedCount += chunk.size();
            } catch (RuntimeException ex) {
                rejectedCount += chunk.size();
                registerChunkFailure(execution, start, end, chunk, ex, targetConfig.isFailFastOnChunkError());
                if (targetConfig.isFailFastOnChunkError()) {
                    return StagingLoadResult.failure(stagedCount, rejectedCount, ex.getMessage());
                }
            }
        }

        return StagingLoadResult.success(stagedCount, rejectedCount);
    }

    private void clearStaging(TargetConfig targetConfig) {
        jdbcTemplate.execute("TRUNCATE TABLE " +
            DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable()));
    }

    private String buildInsertSql(TargetConfig targetConfig, List<String> allColumns) {
        return "INSERT INTO " + DatabaseLoadSupport.qualifiedTable(targetConfig, targetConfig.getStagingTable()) +
            " (" + DatabaseLoadSupport.commaSeparatedQuoted(allColumns) + ") VALUES (" +
            DatabaseLoadSupport.placeholders(allColumns.size()) + ")";
    }

    private void registerChunkFailure(PipelineExecution execution,
                                      int start,
                                      int end,
                                      List<ProcessedRecord> chunk,
                                      RuntimeException ex,
                                      boolean failFast) {
        long startRow = chunk.isEmpty() ? start + 1L : chunk.getFirst().getSourceRowNumber();
        long endRow = chunk.isEmpty() ? end : chunk.getLast().getSourceRowNumber();
        execution.addError(new ExecutionError(
            UUID.randomUUID(),
            execution.getExecutionId(),
            StepNames.LOAD,
            ErrorType.TECHNICAL,
            failFast ? ErrorSeverity.CRITICAL : ErrorSeverity.ERROR,
            "CHUNK_LOAD_FAILURE",
            "Chunk load failed for rows " + startRow + "-" + endRow + ": " + ex.getMessage(),
            DatabaseLoadSupport.stackTrace(ex),
            "chunk:" + ((start / Math.max(1, chunk.size())) + 1) + ", rows:" + startRow + "-" + endRow
        ));
    }
}
