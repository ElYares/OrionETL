package com.elyares.etl.unit.service;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.TriggerType;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.rules.AllowedExecutionWindowRule;
import com.elyares.etl.domain.rules.CriticalErrorBlocksSuccessRule;
import com.elyares.etl.domain.rules.NoDuplicateExecutionRule;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineOrchestrationServiceTest {

    @Test
    void shouldDeterminePartialWhenRejectedRecordsExist() {
        ExecutionRepository repository = new NoActiveExecutionRepository();
        PipelineOrchestrationService service = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        execution.partialSuccess(
            com.elyares.etl.domain.valueobject.RecordCount.of(10),
            com.elyares.etl.domain.valueobject.RecordCount.of(8),
            com.elyares.etl.domain.valueobject.RecordCount.of(2),
            com.elyares.etl.domain.valueobject.RecordCount.of(8),
            "Rejected records"
        );

        ExecutionStatus status = service.determineExecutionStatus(execution);

        assertThat(status).isEqualTo(ExecutionStatus.PARTIAL);
    }

    @Test
    void shouldValidateRetryPreconditions() {
        ExecutionRepository repository = new NoActiveExecutionRepository();
        PipelineOrchestrationService service = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ExecutionRequestDto request = new ExecutionRequestDto(
            SampleDataFactory.aPipeline().getId().toString(),
            TriggerType.RETRY,
            "tester",
            Map.of("retryCount", "1", "lastErrorType", "TECHNICAL")
        );

        service.validatePreconditions(SampleDataFactory.aPipeline(), request);
    }

    @Test
    void shouldDetermineFailedWhenCriticalErrorsExist() {
        ExecutionRepository repository = new NoActiveExecutionRepository();
        PipelineOrchestrationService service = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        execution.addError(new ExecutionError(
            java.util.UUID.randomUUID(),
            execution.getExecutionId(),
            "LOAD",
            ErrorType.TECHNICAL,
            ErrorSeverity.CRITICAL,
            "ERR-CRIT",
            "Critical",
            null,
            null
        ));

        ExecutionStatus status = service.determineExecutionStatus(execution);

        assertThat(status).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void shouldKeepTerminalFailedStatus() {
        ExecutionRepository repository = new NoActiveExecutionRepository();
        PipelineOrchestrationService service = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        PipelineExecution execution = new PipelineExecution(
            null,
            SampleDataFactory.aPipelineId(),
            com.elyares.etl.domain.valueobject.ExecutionId.generate(),
            TriggerType.MANUAL,
            "tester"
        );
        execution.fail("already failed");

        ExecutionStatus status = service.determineExecutionStatus(execution);

        assertThat(status).isEqualTo(ExecutionStatus.FAILED);
    }

    private static final class NoActiveExecutionRepository implements ExecutionRepository {
        @Override
        public PipelineExecution save(PipelineExecution execution) {
            return execution;
        }

        @Override
        public Optional<PipelineExecution> findByExecutionId(com.elyares.etl.domain.valueobject.ExecutionId executionId) {
            return Optional.empty();
        }

        @Override
        public Optional<PipelineExecution> findActiveByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecution> findByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId, int limit) {
            return List.of();
        }
    }
}
