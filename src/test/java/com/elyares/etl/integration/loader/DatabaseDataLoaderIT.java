package com.elyares.etl.integration.loader;

import com.elyares.etl.domain.enums.LoadStrategy;
import com.elyares.etl.domain.enums.TargetType;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.loader.database.DatabaseDataLoader;
import com.elyares.etl.infrastructure.loader.database.FinalLoader;
import com.elyares.etl.infrastructure.loader.database.StagingLoader;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseDataLoaderIT extends PostgresIntegrationTestBase {

    @Autowired
    private DatabaseDataLoader databaseDataLoader;

    @Autowired
    private StagingLoader stagingLoader;

    @Autowired
    private FinalLoader finalLoader;

    @BeforeEach
    void cleanSalesTables() {
        jdbcTemplate.execute("TRUNCATE TABLE sales_transactions_staging, sales_transactions");
    }

    @Test
    void shouldLoadToStagingValidateAndPromoteWithUpsert() {
        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            SampleDataFactory.anExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        TargetConfig targetConfig = SampleDataFactory.aTargetConfig();

        LoadResult result = databaseDataLoader.load(List.of(
            processedRecord("TXN-100", "CUST-001", new BigDecimal("10.50"), "OPEN", execution),
            processedRecord("TXN-101", "CUST-002", new BigDecimal("12.00"), "OPEN", execution)
        ), targetConfig, execution);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalLoaded()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_transactions", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_transactions_staging WHERE etl_execution_id = ?",
            Integer.class,
            UUID.fromString(execution.getExecutionId().toString())
        )).isEqualTo(2);
    }

    @Test
    void shouldKeepFirstChunkWhenSecondChunkFailsAndFailFastIsDisabled() {
        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            SampleDataFactory.anExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        TargetConfig targetConfig = new TargetConfig(
            TargetType.DATABASE,
            "public",
            "sales_transactions_staging",
            "sales_transactions",
            LoadStrategy.UPSERT,
            List.of("transaction_id"),
            1,
            false,
            com.elyares.etl.domain.enums.RollbackStrategy.DELETE_BY_EXECUTION,
            true,
            "status",
            "CLOSED"
        );

        var stagingResult = stagingLoader.load(List.of(
            processedRecord("TXN-200", "CUST-001", new BigDecimal("22.00"), "OPEN", execution),
            processedRecord("TXN-201", "CUST-002", new BigDecimal("-1.00"), "OPEN", execution)
        ), targetConfig, execution);

        assertThat(stagingResult.successful()).isTrue();
        assertThat(stagingResult.stagedCount()).isEqualTo(1);
        assertThat(stagingResult.rejectedCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_transactions_staging WHERE etl_execution_id = ?",
            Integer.class,
            UUID.fromString(execution.getExecutionId().toString())
        )).isEqualTo(1);
        assertThat(execution.getErrors()).hasSize(1);
    }

    @Test
    void shouldFailStagingValidationAndSkipPromotionWhenBusinessKeyDuplicatesExist() {
        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            SampleDataFactory.anExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );

        LoadResult result = databaseDataLoader.load(List.of(
            processedRecord("TXN-300", "CUST-001", new BigDecimal("15.00"), "OPEN", execution),
            processedRecord("TXN-300", "CUST-002", new BigDecimal("18.00"), "OPEN", execution)
        ), SampleDataFactory.aTargetConfig(), execution);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getErrorDetail()).contains("Business key duplicates");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_transactions", Integer.class)).isZero();
    }

    @Test
    void shouldUpsertOpenRecordsSkipClosedOnesAndRollbackByExecution() {
        PipelineExecution closedSeedExecution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            SampleDataFactory.anExecutionId(),
            TriggerType.MANUAL,
            "seed"
        );
        jdbcTemplate.update("""
            INSERT INTO sales_transactions (
                transaction_id, customer_id, amount, sale_date, status,
                etl_execution_id, etl_pipeline_id, etl_source_file, etl_load_timestamp, etl_pipeline_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "TXN-400", "CUST-001", new BigDecimal("10.00"), java.sql.Date.valueOf("2026-01-01"), "CLOSED",
            UUID.fromString(closedSeedExecution.getExecutionId().toString()),
            UUID.fromString(closedSeedExecution.getPipelineId().toString()),
            "seed.csv", java.sql.Timestamp.from(Instant.now()), "1.0.0"
        );

        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            SampleDataFactory.anExecutionId(),
            TriggerType.MANUAL,
            "tester"
        );
        TargetConfig targetConfig = SampleDataFactory.aTargetConfig();

        LoadResult result = databaseDataLoader.load(List.of(
            processedRecord("TXN-400", "CUST-999", new BigDecimal("99.00"), "OPEN", execution),
            processedRecord("TXN-401", "CUST-002", new BigDecimal("25.00"), "OPEN", execution)
        ), targetConfig, execution);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT customer_id FROM sales_transactions WHERE transaction_id = 'TXN-400'",
            String.class
        )).isEqualTo("CUST-001");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_transactions WHERE transaction_id = 'TXN-401'",
            Integer.class
        )).isEqualTo(1);

        finalLoader.rollbackByExecution(targetConfig, execution);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_transactions WHERE etl_execution_id = ?",
            Integer.class,
            UUID.fromString(execution.getExecutionId().toString())
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_transactions WHERE transaction_id = 'TXN-400'",
            Integer.class
        )).isEqualTo(1);
    }

    private ProcessedRecord processedRecord(String transactionId,
                                            String customerId,
                                            BigDecimal amount,
                                            String status,
                                            PipelineExecution execution) {
        return new ProcessedRecord(
            1L,
            Map.of(
                "transaction_id", transactionId,
                "customer_id", customerId,
                "amount", amount,
                "sale_date", java.sql.Date.valueOf("2026-01-15"),
                "status", status
            ),
            "1.0.0",
            execution.getExecutionId().toString() + ".csv",
            Instant.now()
        );
    }
}
