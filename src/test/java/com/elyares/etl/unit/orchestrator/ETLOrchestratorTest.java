package com.elyares.etl.unit.orchestrator;

import com.elyares.etl.application.orchestrator.ETLOrchestrator;
import com.elyares.etl.application.usecase.extraction.ExtractDataUseCase;
import com.elyares.etl.application.usecase.loading.LoadProcessedDataUseCase;
import com.elyares.etl.application.usecase.loading.PersistRejectedRecordsUseCase;
import com.elyares.etl.application.usecase.loading.RegisterAuditUseCase;
import com.elyares.etl.application.usecase.transformation.TransformDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateBusinessDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateInputDataUseCase;
import com.elyares.etl.domain.contract.ExecutionRepository;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.enums.StepStatus;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.domain.service.DataQualityService;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.domain.rules.AllowedExecutionWindowRule;
import com.elyares.etl.domain.rules.CriticalErrorBlocksSuccessRule;
import com.elyares.etl.domain.rules.NoDuplicateExecutionRule;
import com.elyares.etl.domain.rules.RetryEligibilityRule;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ETLOrchestratorTest {

    @Test
    void shouldRunAuditEvenWhenSchemaStepFails() {
        ExtractDataUseCase extractDataUseCase = mock(ExtractDataUseCase.class);
        ValidateInputDataUseCase validateInputDataUseCase = mock(ValidateInputDataUseCase.class);
        TransformDataUseCase transformDataUseCase = mock(TransformDataUseCase.class);
        ValidateBusinessDataUseCase validateBusinessDataUseCase = mock(ValidateBusinessDataUseCase.class);
        LoadProcessedDataUseCase loadProcessedDataUseCase = mock(LoadProcessedDataUseCase.class);
        PersistRejectedRecordsUseCase persistRejectedRecordsUseCase = mock(PersistRejectedRecordsUseCase.class);
        RegisterAuditUseCase registerAuditUseCase = mock(RegisterAuditUseCase.class);

        ExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService executionLifecycleService = new ExecutionLifecycleService(repository);
        PipelineOrchestrationService orchestrationService = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ETLOrchestrator orchestrator = new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            orchestrationService,
            new DataQualityService(),
            List.of()
        );

        var pipeline = SampleDataFactory.aPipeline();
        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "tester"
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());

        when(extractDataUseCase.execute(any(), any()))
            .thenReturn(ExtractionResult.success(List.of(), "test-source"));
        when(validateInputDataUseCase.execute(anyList(), any()))
            .thenThrow(new RuntimeException("schema failure"));

        orchestrator.orchestrate(pipeline, execution);

        verify(registerAuditUseCase, times(1))
            .execute(eq(execution), eq(pipeline), ArgumentMatchers.anyMap());
    }

    @Test
    void shouldCompleteExecutionSuccessfully() {
        ExtractDataUseCase extractDataUseCase = mock(ExtractDataUseCase.class);
        ValidateInputDataUseCase validateInputDataUseCase = mock(ValidateInputDataUseCase.class);
        TransformDataUseCase transformDataUseCase = mock(TransformDataUseCase.class);
        ValidateBusinessDataUseCase validateBusinessDataUseCase = mock(ValidateBusinessDataUseCase.class);
        LoadProcessedDataUseCase loadProcessedDataUseCase = mock(LoadProcessedDataUseCase.class);
        PersistRejectedRecordsUseCase persistRejectedRecordsUseCase = mock(PersistRejectedRecordsUseCase.class);
        RegisterAuditUseCase registerAuditUseCase = mock(RegisterAuditUseCase.class);

        ExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService executionLifecycleService = new ExecutionLifecycleService(repository);
        PipelineOrchestrationService orchestrationService = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ETLOrchestrator orchestrator = new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            orchestrationService,
            new DataQualityService(),
            List.of()
        );

        var pipeline = SampleDataFactory.aPipeline();
        List<com.elyares.etl.domain.model.source.RawRecord> rawRecords = List.of(
            SampleDataFactory.aRawRecord(1, Map.of("id", "1")),
            SampleDataFactory.aRawRecord(2, Map.of("id", "2"))
        );
        List<ProcessedRecord> processedRecords = List.of(
            new ProcessedRecord(1, Map.of("id", "1"), "1.0.0", Instant.now()),
            new ProcessedRecord(2, Map.of("id", "2"), "1.0.0", Instant.now())
        );

        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "tester"
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());

        when(extractDataUseCase.execute(any(), any()))
            .thenReturn(ExtractionResult.success(rawRecords, "test-source"));
        when(validateInputDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(transformDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(TransformationResult.of(processedRecords, List.of()));
        when(validateBusinessDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(loadProcessedDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(LoadResult.success(2, 0, 0));

        PipelineExecution result = orchestrator.orchestrate(pipeline, execution);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getTotalRead().value()).isEqualTo(2);
        assertThat(result.getTotalLoaded().value()).isEqualTo(2);
        assertThat(result.getSteps()).hasSize(8);
        assertThat(result.getSteps().stream().filter(s -> s.getStepName().equals("CLOSE")).findFirst().orElseThrow().getStatus())
            .isEqualTo(StepStatus.SUCCESS);
        assertThat(result.getSteps().stream().filter(s -> s.getStepName().equals("AUDIT")).findFirst().orElseThrow().getStatus())
            .isEqualTo(StepStatus.SUCCESS);
    }

    @Test
    void shouldFailExecutionWhenLoadFails() {
        ExtractDataUseCase extractDataUseCase = mock(ExtractDataUseCase.class);
        ValidateInputDataUseCase validateInputDataUseCase = mock(ValidateInputDataUseCase.class);
        TransformDataUseCase transformDataUseCase = mock(TransformDataUseCase.class);
        ValidateBusinessDataUseCase validateBusinessDataUseCase = mock(ValidateBusinessDataUseCase.class);
        LoadProcessedDataUseCase loadProcessedDataUseCase = mock(LoadProcessedDataUseCase.class);
        PersistRejectedRecordsUseCase persistRejectedRecordsUseCase = mock(PersistRejectedRecordsUseCase.class);
        RegisterAuditUseCase registerAuditUseCase = mock(RegisterAuditUseCase.class);

        ExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService executionLifecycleService = new ExecutionLifecycleService(repository);
        PipelineOrchestrationService orchestrationService = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ETLOrchestrator orchestrator = new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            orchestrationService,
            new DataQualityService(),
            List.of()
        );

        var pipeline = SampleDataFactory.aPipeline();
        List<com.elyares.etl.domain.model.source.RawRecord> rawRecords = List.of(
            SampleDataFactory.aRawRecord(1, Map.of("id", "1")),
            SampleDataFactory.aRawRecord(2, Map.of("id", "2"))
        );
        List<ProcessedRecord> processedRecords = List.of(
            new ProcessedRecord(1, Map.of("id", "1"), "1.0.0", Instant.now()),
            new ProcessedRecord(2, Map.of("id", "2"), "1.0.0", Instant.now())
        );

        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "tester"
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());

        when(extractDataUseCase.execute(any(), any()))
            .thenReturn(ExtractionResult.success(rawRecords, "test-source"));
        when(validateInputDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(transformDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(TransformationResult.of(processedRecords, List.of()));
        when(validateBusinessDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(loadProcessedDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(LoadResult.failure("db unavailable"));

        PipelineExecution result = orchestrator.orchestrate(pipeline, execution);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getErrorSummary()).contains("db unavailable");
        assertThat(result.getSteps().stream().filter(s -> s.getStepName().equals("LOAD")).findFirst().orElseThrow().getStatus())
            .isEqualTo(StepStatus.FAILED);
    }

    @Test
    void shouldFailWhenSchemaThresholdExceeded() {
        ExtractDataUseCase extractDataUseCase = mock(ExtractDataUseCase.class);
        ValidateInputDataUseCase validateInputDataUseCase = mock(ValidateInputDataUseCase.class);
        TransformDataUseCase transformDataUseCase = mock(TransformDataUseCase.class);
        ValidateBusinessDataUseCase validateBusinessDataUseCase = mock(ValidateBusinessDataUseCase.class);
        LoadProcessedDataUseCase loadProcessedDataUseCase = mock(LoadProcessedDataUseCase.class);
        PersistRejectedRecordsUseCase persistRejectedRecordsUseCase = mock(PersistRejectedRecordsUseCase.class);
        RegisterAuditUseCase registerAuditUseCase = mock(RegisterAuditUseCase.class);

        ExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService executionLifecycleService = new ExecutionLifecycleService(repository);
        PipelineOrchestrationService orchestrationService = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ETLOrchestrator orchestrator = new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            orchestrationService,
            new DataQualityService(),
            List.of()
        );

        var pipeline = SampleDataFactory.aPipeline();
        List<com.elyares.etl.domain.model.source.RawRecord> rawRecords = IntStream.range(0, 10)
            .mapToObj(i -> SampleDataFactory.aRawRecord(i + 1L, Map.of("id", String.valueOf(i + 1))))
            .toList();
        List<ValidationError> criticalErrors = List.of(
            ValidationError.critical("id", "1", "RULE", "critical-1"),
            ValidationError.critical("id", "2", "RULE", "critical-2")
        );

        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "tester"
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());

        when(extractDataUseCase.execute(any(), any()))
            .thenReturn(ExtractionResult.success(rawRecords, "test-source"));
        when(validateInputDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.batch(
                List.of(),
                rawRecords.stream()
                    .map(record -> new RejectedRecord(record, "VALIDATE_SCHEMA", "schema failed", criticalErrors))
                    .toList(),
                criticalErrors,
                null
            ));

        PipelineExecution result = orchestrator.orchestrate(pipeline, execution);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getErrorSummary()).contains("Schema validation threshold exceeded");
        assertThat(result.getSteps().stream().filter(s -> s.getStepName().equals("VALIDATE_SCHEMA")).findFirst().orElseThrow().getStatus())
            .isEqualTo(StepStatus.FAILED);
    }

    @Test
    void shouldMarkAuditStepFailedWhenAuditRegistrationThrows() {
        ExtractDataUseCase extractDataUseCase = mock(ExtractDataUseCase.class);
        ValidateInputDataUseCase validateInputDataUseCase = mock(ValidateInputDataUseCase.class);
        TransformDataUseCase transformDataUseCase = mock(TransformDataUseCase.class);
        ValidateBusinessDataUseCase validateBusinessDataUseCase = mock(ValidateBusinessDataUseCase.class);
        LoadProcessedDataUseCase loadProcessedDataUseCase = mock(LoadProcessedDataUseCase.class);
        PersistRejectedRecordsUseCase persistRejectedRecordsUseCase = mock(PersistRejectedRecordsUseCase.class);
        RegisterAuditUseCase registerAuditUseCase = mock(RegisterAuditUseCase.class);

        ExecutionRepository repository = new InMemoryExecutionRepository();
        ExecutionLifecycleService executionLifecycleService = new ExecutionLifecycleService(repository);
        PipelineOrchestrationService orchestrationService = new PipelineOrchestrationService(
            new NoDuplicateExecutionRule(repository),
            new AllowedExecutionWindowRule(),
            new CriticalErrorBlocksSuccessRule(),
            new RetryEligibilityRule()
        );

        ETLOrchestrator orchestrator = new ETLOrchestrator(
            extractDataUseCase,
            validateInputDataUseCase,
            transformDataUseCase,
            validateBusinessDataUseCase,
            loadProcessedDataUseCase,
            persistRejectedRecordsUseCase,
            registerAuditUseCase,
            executionLifecycleService,
            orchestrationService,
            new DataQualityService(),
            List.of()
        );

        var pipeline = SampleDataFactory.aPipeline();
        List<com.elyares.etl.domain.model.source.RawRecord> rawRecords = List.of(
            SampleDataFactory.aRawRecord(1, Map.of("id", "1")),
            SampleDataFactory.aRawRecord(2, Map.of("id", "2"))
        );
        List<ProcessedRecord> processedRecords = List.of(
            new ProcessedRecord(1, Map.of("id", "1"), "1.0.0", Instant.now()),
            new ProcessedRecord(2, Map.of("id", "2"), "1.0.0", Instant.now())
        );

        PipelineExecution execution = executionLifecycleService.createExecution(
            pipeline.getId(),
            com.elyares.etl.domain.enums.TriggerType.MANUAL,
            "tester"
        );
        execution = executionLifecycleService.markRunning(execution.getExecutionId());

        when(extractDataUseCase.execute(any(), any()))
            .thenReturn(ExtractionResult.success(rawRecords, "test-source"));
        when(validateInputDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(transformDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(TransformationResult.of(processedRecords, List.of()));
        when(validateBusinessDataUseCase.execute(anyList(), any()))
            .thenReturn(ValidationResult.ok(rawRecords));
        when(loadProcessedDataUseCase.execute(anyList(), any(), any()))
            .thenReturn(LoadResult.success(2, 0, 0));
        when(registerAuditUseCase.execute(any(), any(), anyMap()))
            .thenThrow(new RuntimeException("audit down"));

        PipelineExecution result = orchestrator.orchestrate(pipeline, execution);

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(result.getSteps().stream().filter(s -> s.getStepName().equals("AUDIT")).findFirst().orElseThrow().getStatus())
            .isEqualTo(StepStatus.FAILED);
    }

    private static final class InMemoryExecutionRepository implements ExecutionRepository {
        private PipelineExecution execution;

        @Override
        public PipelineExecution save(PipelineExecution execution) {
            this.execution = execution;
            return execution;
        }

        @Override
        public Optional<PipelineExecution> findByExecutionId(com.elyares.etl.domain.valueobject.ExecutionId executionId) {
            return Optional.ofNullable(execution);
        }

        @Override
        public Optional<PipelineExecution> findActiveByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecution> findByPipelineId(com.elyares.etl.domain.valueobject.PipelineId pipelineId, int limit) {
            return execution == null ? List.of() : List.of(execution);
        }
    }
}
