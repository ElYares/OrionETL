package com.elyares.etl.unit.rules;

import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.rules.CriticalErrorBlocksSuccessRule;
import com.elyares.etl.domain.valueobject.ExecutionId;
import com.elyares.etl.domain.valueobject.PipelineId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalErrorBlocksSuccessRuleTest {

    @Test
    void shouldBlockSuccessWhenCriticalErrorsExist() {
        PipelineExecution execution = new PipelineExecution(
            UUID.randomUUID(),
            PipelineId.generate(),
            ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        execution.addError(new ExecutionError(
            UUID.randomUUID(),
            execution.getExecutionId(),
            "VALIDATE_BUSINESS",
            ErrorType.DATA_QUALITY,
            ErrorSeverity.CRITICAL,
            "ERR-1",
            "Critical error",
            null,
            null
        ));

        CriticalErrorBlocksSuccessRule rule = new CriticalErrorBlocksSuccessRule();

        assertThat(rule.allowsSuccess(execution)).isFalse();
    }

    @Test
    void shouldAllowSuccessWhenNoCriticalErrors() {
        PipelineExecution execution = new PipelineExecution(
            UUID.randomUUID(),
            PipelineId.generate(),
            ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );

        CriticalErrorBlocksSuccessRule rule = new CriticalErrorBlocksSuccessRule();

        assertThat(rule.allowsSuccess(execution)).isTrue();
    }
}
