package com.elyares.etl.e2e;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import com.elyares.etl.pipelines.sales.SalesPipelineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesPipelineE2EIT extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private SalesPipelineConfig salesPipelineConfig;

    @BeforeEach
    void cleanSalesTablesAndRegisterPipeline() {
        jdbcTemplate.execute("TRUNCATE TABLE sales_transactions_staging, sales_transactions");
        salesPipelineConfig.registerIfMissing();
    }

    @Test
    void shouldExecuteSalesPipelineEndToEndWithPartialOutcome() {
        Pipeline pipeline = salesPipelineConfig.registerIfMissing();

        PipelineExecutionDto execution = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "sales-e2e")
        );

        assertThat(execution.status())
            .withFailMessage("status=%s errorSummary=%s steps=%s", execution.status(), execution.errorSummary(), execution.steps())
            .isEqualTo(ExecutionStatus.PARTIAL);
        assertThat(execution.totalRead()).isEqualTo(105);
        assertThat(execution.totalLoaded()).isEqualTo(100);
        assertThat(execution.totalRejected()).isEqualTo(5);

        Integer finalRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_transactions", Integer.class);
        Integer rejectedRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_rejected_records", Integer.class);
        Integer auditRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM etl_audit_records", Integer.class);

        assertThat(finalRows).isEqualTo(100);
        assertThat(rejectedRows).isEqualTo(5);
        assertThat(auditRows).isEqualTo(1);

        List<String> rejectionRules = jdbcTemplate.queryForList(
            "SELECT DISTINCT jsonb_array_elements(validation_errors)->>'rule' FROM etl_rejected_records ORDER BY 1",
            String.class
        );
        assertThat(rejectionRules).containsExactlyInAnyOrder(
            "ACTIVE_REFERENCE",
            "CATALOG_LOOKUP",
            "FUTURE_DATE",
            "RANGE_CHECK"
        );

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM etl_pipeline_executions WHERE execution_ref = ?::uuid",
            String.class,
            execution.executionId()
        );
        Integer totalRead = jdbcTemplate.queryForObject(
            "SELECT total_read FROM etl_pipeline_executions WHERE execution_ref = ?::uuid",
            Integer.class,
            execution.executionId()
        );
        Integer totalLoaded = jdbcTemplate.queryForObject(
            "SELECT total_loaded FROM etl_pipeline_executions WHERE execution_ref = ?::uuid",
            Integer.class,
            execution.executionId()
        );
        Integer totalRejected = jdbcTemplate.queryForObject(
            "SELECT total_rejected FROM etl_pipeline_executions WHERE execution_ref = ?::uuid",
            Integer.class,
            execution.executionId()
        );

        assertThat(status).isEqualTo("PARTIAL");
        assertThat(totalRead).isEqualTo(105);
        assertThat(totalLoaded).isEqualTo(100);
        assertThat(totalRejected).isEqualTo(5);

        String auditTotalRead = jdbcTemplate.queryForObject(
            "SELECT details->>'totalRead' FROM etl_audit_records WHERE execution_id = (SELECT id FROM etl_pipeline_executions WHERE execution_ref = ?::uuid)",
            String.class,
            execution.executionId()
        );
        String auditTotalLoaded = jdbcTemplate.queryForObject(
            "SELECT details->>'totalLoaded' FROM etl_audit_records WHERE execution_id = (SELECT id FROM etl_pipeline_executions WHERE execution_ref = ?::uuid)",
            String.class,
            execution.executionId()
        );
        String auditTotalRejected = jdbcTemplate.queryForObject(
            "SELECT details->>'totalRejected' FROM etl_audit_records WHERE execution_id = (SELECT id FROM etl_pipeline_executions WHERE execution_ref = ?::uuid)",
            String.class,
            execution.executionId()
        );

        assertThat(auditTotalRead).isEqualTo("105");
        assertThat(auditTotalLoaded).isEqualTo("100");
        assertThat(auditTotalRejected).isEqualTo("5");
    }
}
