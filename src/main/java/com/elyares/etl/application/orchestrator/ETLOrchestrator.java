package com.elyares.etl.application.orchestrator;

import com.elyares.etl.application.usecase.extraction.ExtractDataUseCase;
import com.elyares.etl.application.usecase.loading.LoadProcessedDataUseCase;
import com.elyares.etl.application.usecase.loading.PersistRejectedRecordsUseCase;
import com.elyares.etl.application.usecase.loading.RegisterAuditUseCase;
import com.elyares.etl.application.usecase.transformation.TransformDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateBusinessDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateInputDataUseCase;
import com.elyares.etl.domain.contract.ExecutionNotificationHook;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.domain.service.DataQualityService;
import com.elyares.etl.domain.service.ExecutionLifecycleService;
import com.elyares.etl.domain.service.PipelineOrchestrationService;
import com.elyares.etl.shared.constants.StepNames;
import com.elyares.etl.shared.logging.ExecutionMdcContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquestador principal del flujo ETL en Fase 2.
 */
public class ETLOrchestrator {

    private final ExtractDataUseCase extractDataUseCase;
    private final ValidateInputDataUseCase validateInputDataUseCase;
    private final TransformDataUseCase transformDataUseCase;
    private final ValidateBusinessDataUseCase validateBusinessDataUseCase;
    private final LoadProcessedDataUseCase loadProcessedDataUseCase;
    private final PersistRejectedRecordsUseCase persistRejectedRecordsUseCase;
    private final RegisterAuditUseCase registerAuditUseCase;
    private final ExecutionLifecycleService executionLifecycleService;
    private final PipelineOrchestrationService pipelineOrchestrationService;
    private final DataQualityService dataQualityService;
    private final List<ExecutionNotificationHook> executionNotificationHooks;

    public ETLOrchestrator(ExtractDataUseCase extractDataUseCase,
                           ValidateInputDataUseCase validateInputDataUseCase,
                           TransformDataUseCase transformDataUseCase,
                           ValidateBusinessDataUseCase validateBusinessDataUseCase,
                           LoadProcessedDataUseCase loadProcessedDataUseCase,
                           PersistRejectedRecordsUseCase persistRejectedRecordsUseCase,
                           RegisterAuditUseCase registerAuditUseCase,
                           ExecutionLifecycleService executionLifecycleService,
                           PipelineOrchestrationService pipelineOrchestrationService,
                           DataQualityService dataQualityService,
                           List<ExecutionNotificationHook> executionNotificationHooks) {
        this.extractDataUseCase = extractDataUseCase;
        this.validateInputDataUseCase = validateInputDataUseCase;
        this.transformDataUseCase = transformDataUseCase;
        this.validateBusinessDataUseCase = validateBusinessDataUseCase;
        this.loadProcessedDataUseCase = loadProcessedDataUseCase;
        this.persistRejectedRecordsUseCase = persistRejectedRecordsUseCase;
        this.registerAuditUseCase = registerAuditUseCase;
        this.executionLifecycleService = executionLifecycleService;
        this.pipelineOrchestrationService = pipelineOrchestrationService;
        this.dataQualityService = dataQualityService;
        this.executionNotificationHooks = List.copyOf(executionNotificationHooks);
    }

    public PipelineExecution orchestrate(Pipeline pipeline, PipelineExecution execution) {
        OrchestrationContext context = new OrchestrationContext();
        String failureReason = null;

        try (ExecutionMdcContext ignored = ExecutionMdcContext.of(
            execution.getExecutionId().toString(),
            execution.getPipelineId().toString()
        )) {
            markStepRunning(execution, StepNames.INIT, 1);
            markStepSuccess(execution, StepNames.INIT, 1, 0);

            markStepRunning(execution, StepNames.EXTRACT, 2);
            ExtractionResult extractionResult = extractDataUseCase.execute(pipeline, execution);
            if (!extractionResult.isSuccessful()) {
                failureReason = extractionResult.getErrorDetail();
                markStepFailed(execution, StepNames.EXTRACT, 2, failureReason);
                return markFailedExecution(execution, failureReason);
            }
            context.setRawRecords(extractionResult.getRecords());
            context.setTotalRead(extractionResult.getTotalRead());
            markStepSuccess(execution, StepNames.EXTRACT, 2, context.getTotalRead());

            markStepRunning(execution, StepNames.VALIDATE_SCHEMA, 3);
            ValidationResult schemaValidation = validateInputDataUseCase.execute(
                context.getRawRecords(),
                pipeline.getValidationConfig()
            );
            context.setRawRecords(schemaValidation.getValidRecords());
            context.addRejectedRecords(schemaValidation.getRejectedRecords());
            DataQualityReport schemaQuality = schemaValidation.getDataQualityReport() != null
                ? schemaValidation.getDataQualityReport()
                : dataQualityService.evaluateQuality(
                    context.getTotalRead(),
                    context.getTotalRejected(),
                    pipeline.getValidationConfig().getErrorThreshold()
                );
            if (dataQualityService.isAbortRequired(schemaQuality, pipeline.getValidationConfig())) {
                failureReason = "Schema validation threshold exceeded";
                markStepFailed(execution, StepNames.VALIDATE_SCHEMA, 3, failureReason);
                return markFailedExecution(execution, failureReason);
            }
            markStepSuccess(execution, StepNames.VALIDATE_SCHEMA, 3, context.getRawRecords().size());

            markStepRunning(execution, StepNames.TRANSFORM, 4);
            var transformationResult = transformDataUseCase.execute(
                context.getRawRecords(), pipeline, execution
            );
            context.setProcessedRecords(transformationResult.getProcessedRecords());
            context.addRejectedRecords(transformationResult.getRejectedRecords());
            context.setTotalTransformed(context.getProcessedRecords().size());
            markStepSuccess(execution, StepNames.TRANSFORM, 4, context.getTotalTransformed());

            markStepRunning(execution, StepNames.VALIDATE_BUSINESS, 5);
            List<RawRecord> validationRecords = toValidationRecords(context.getProcessedRecords());
            ValidationResult businessValidation = validateBusinessDataUseCase.execute(
                validationRecords,
                pipeline.getValidationConfig()
            );
            context.setRawRecords(businessValidation.getValidRecords());
            context.setProcessedRecords(filterProcessedRecords(context.getProcessedRecords(), businessValidation));
            context.addRejectedRecords(businessValidation.getRejectedRecords());
            DataQualityReport businessQuality = businessValidation.getDataQualityReport() != null
                ? businessValidation.getDataQualityReport()
                : dataQualityService.evaluateQuality(
                    context.getTotalRead(),
                    context.getTotalRejected(),
                    pipeline.getValidationConfig().getErrorThreshold()
                );
            if (dataQualityService.isAbortRequired(businessQuality, pipeline.getValidationConfig())) {
                failureReason = "Business validation threshold exceeded";
                markStepFailed(execution, StepNames.VALIDATE_BUSINESS, 5, failureReason);
                return markFailedExecution(execution, failureReason);
            }
            markStepSuccess(execution, StepNames.VALIDATE_BUSINESS, 5, context.getProcessedRecords().size());

            markStepRunning(execution, StepNames.LOAD, 6);
            persistRejectedBeforeLoad(context, execution);
            LoadResult loadResult = loadProcessedDataUseCase.execute(
                context.getProcessedRecords(), pipeline, execution
            );
            if (!loadResult.isSuccessful()) {
                failureReason = loadResult.getErrorDetail();
                markStepFailed(execution, StepNames.LOAD, 6, failureReason);
                return markFailedExecution(execution, failureReason);
            }
            context.setTotalLoaded(loadResult.getTotalLoaded());
            context.addRejected(loadResult.getTotalRejected());
            markStepSuccess(execution, StepNames.LOAD, 6, context.getTotalLoaded());

            markStepRunning(execution, StepNames.CLOSE, 7);
            ExecutionStatus finalStatus = pipelineOrchestrationService.determineExecutionStatus(
                execution,
                context.getTotalRejected()
            );
            execution = switch (finalStatus) {
                case SUCCESS -> executionLifecycleService.markSuccess(
                    execution.getExecutionId(),
                    context.getTotalRead(),
                    context.getTotalTransformed(),
                    context.getTotalRejected(),
                    context.getTotalLoaded()
                );
                case PARTIAL -> executionLifecycleService.markPartial(
                    execution.getExecutionId(),
                    context.getTotalRead(),
                    context.getTotalTransformed(),
                    context.getTotalRejected(),
                    context.getTotalLoaded(),
                    "Execution finished with partial quality"
                );
                default -> executionLifecycleService.markFailed(
                    execution.getExecutionId(),
                    "Execution finished in non-success terminal state"
                );
            };
            emitNotification(execution, context.getTotalRejected());
            markStepSuccess(execution, StepNames.CLOSE, 7, context.getTotalLoaded());
            return execution;

        } catch (RuntimeException ex) {
            execution = markFailedExecution(execution, ex.getMessage());
            emitNotification(execution, context.getTotalRejected());
            return execution;
        } finally {
            runAuditAlways(pipeline, execution, context, failureReason);
        }
    }

    private List<RawRecord> toValidationRecords(List<ProcessedRecord> processedRecords) {
        return processedRecords.stream()
            .map(record -> new RawRecord(
                record.getSourceRowNumber(),
                record.getData(),
                record.getSourceReference(),
                record.getTransformedAt()
            ))
            .toList();
    }

    private List<ProcessedRecord> filterProcessedRecords(List<ProcessedRecord> processedRecords,
                                                         ValidationResult businessValidation) {
        Set<Long> validRows = businessValidation.getValidRecords().stream()
            .map(RawRecord::getRowNumber)
            .collect(Collectors.toSet());
        return processedRecords.stream()
            .filter(record -> validRows.contains(record.getSourceRowNumber()))
            .toList();
    }

    private PipelineExecution markFailedExecution(PipelineExecution execution, String reason) {
        execution.addError(new ExecutionError(
            UUID.randomUUID(),
            execution.getExecutionId(),
            StepNames.CLOSE,
            ErrorType.TECHNICAL,
            ErrorSeverity.CRITICAL,
            "ETL_EXEC_FAILED",
            reason != null ? reason : "Unknown execution failure",
            null,
            null
        ));
        return executionLifecycleService.markFailed(
            execution.getExecutionId(),
            reason != null ? reason : "Unknown execution failure"
        );
    }

    private void emitNotification(PipelineExecution execution, long rejectedCount) {
        if (executionNotificationHooks.isEmpty()) {
            return;
        }
        switch (execution.getStatus()) {
            case SUCCESS -> executionNotificationHooks.forEach(hook -> hook.notifySuccess(execution));
            case PARTIAL -> executionNotificationHooks.forEach(hook -> hook.notifyPartial(execution, rejectedCount));
            case FAILED -> {
                ExecutionError error = execution.getErrors().stream()
                    .reduce((first, second) -> second)
                    .orElseGet(() -> new ExecutionError(
                        UUID.randomUUID(),
                        execution.getExecutionId(),
                        StepNames.CLOSE,
                        ErrorType.TECHNICAL,
                        ErrorSeverity.CRITICAL,
                        "ETL_EXEC_FAILED",
                        execution.getErrorSummary() != null ? execution.getErrorSummary() : "Execution failed",
                        null,
                        null
                    ));
                executionNotificationHooks.forEach(hook -> hook.notifyFailure(execution, error));
            }
            default -> {
                // no-op
            }
        }
    }

    private void runAuditAlways(Pipeline pipeline,
                                PipelineExecution execution,
                                OrchestrationContext context,
                                String failureReason) {
        try {
            markStepRunning(execution, StepNames.AUDIT, 8);
            if (!context.isRejectedRecordsPersisted()) {
                persistRejectedRecordsUseCase.execute(context.getRejectedRecords(), execution.getExecutionId());
                context.markRejectedRecordsPersisted();
            }
            registerAuditUseCase.execute(execution, pipeline, buildAuditDetails(context, failureReason));
            markStepSuccess(execution, StepNames.AUDIT, 8, context.getTotalLoaded());
        } catch (RuntimeException ex) {
            markStepFailed(execution, StepNames.AUDIT, 8, ex.getMessage());
        }
    }

    private Map<String, Object> buildAuditDetails(OrchestrationContext context, String failureReason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("totalRead", context.getTotalRead());
        details.put("totalTransformed", context.getTotalTransformed());
        details.put("totalRejected", context.getTotalRejected());
        details.put("totalLoaded", context.getTotalLoaded());
        details.put("recordedAt", Instant.now().toString());
        if (failureReason != null) {
            details.put("failureReason", failureReason);
        }
        return details;
    }

    private void persistRejectedBeforeLoad(OrchestrationContext context, PipelineExecution execution) {
        if (context.isRejectedRecordsPersisted()) {
            return;
        }
        persistRejectedRecordsUseCase.execute(context.getRejectedRecords(), execution.getExecutionId());
        context.markRejectedRecordsPersisted();
    }

    private void markStepRunning(PipelineExecution execution, String stepName, int order) {
        PipelineExecutionStep step = getOrCreateStep(execution, stepName, order);
        step.markRunning();
    }

    private void markStepSuccess(PipelineExecution execution, String stepName, int order, long processed) {
        PipelineExecutionStep step = getOrCreateStep(execution, stepName, order);
        step.markSuccess(processed);
    }

    private void markStepFailed(PipelineExecution execution, String stepName, int order, String error) {
        PipelineExecutionStep step = getOrCreateStep(execution, stepName, order);
        step.markFailed(error != null ? error : "Unknown step failure");
    }

    private PipelineExecutionStep getOrCreateStep(PipelineExecution execution, String stepName, int order) {
        return execution.getSteps().stream()
            .filter(existing -> existing.getStepName().equals(stepName))
            .findFirst()
            .orElseGet(() -> {
                PipelineExecutionStep created = new PipelineExecutionStep(
                    UUID.randomUUID(),
                    execution.getExecutionId(),
                    stepName,
                    order
                );
                execution.addStep(created);
                return created;
            });
    }
}
