package com.elyares.etl.infrastructure.loader.database;

import com.elyares.etl.domain.contract.DataLoader;
import com.elyares.etl.domain.enums.ErrorSeverity;
import com.elyares.etl.domain.enums.ErrorType;
import com.elyares.etl.domain.model.execution.ExecutionError;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.StagingValidationResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.shared.constants.StepNames;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementación de carga a base de datos con el flujo staging -> validate -> final.
 */
@Component
public class DatabaseDataLoader implements DataLoader {

    private final StagingLoader stagingLoader;
    private final StagingValidator stagingValidator;
    private final FinalLoader finalLoader;

    public DatabaseDataLoader(StagingLoader stagingLoader,
                              StagingValidator stagingValidator,
                              FinalLoader finalLoader) {
        this.stagingLoader = stagingLoader;
        this.stagingValidator = stagingValidator;
        this.finalLoader = finalLoader;
    }

    @Override
    public LoadResult load(List<ProcessedRecord> records, TargetConfig targetConfig, PipelineExecution execution) {
        StagingLoadResult stagingResult = stagingLoader.load(records, targetConfig, execution);
        if (!stagingResult.successful()) {
            return LoadResult.failure(stagingResult.errorDetail());
        }

        StagingValidationResult validationResult = stagingValidator.validate(
            targetConfig,
            execution,
            stagingResult.stagedCount()
        );
        if (!validationResult.isValid()) {
            // No promovemos nunca a final si staging no representa un lote consistente.
            registerStagingValidationFailure(execution, validationResult);
            return LoadResult.failure(String.join("; ", validationResult.getDetails()));
        }

        try {
            LoadResult finalResult = finalLoader.promote(targetConfig, execution);
            return LoadResult.success(
                finalResult.getTotalInserted(),
                finalResult.getTotalUpdated(),
                stagingResult.rejectedCount() + finalResult.getTotalRejected()
            );
        } catch (RuntimeException ex) {
            execution.addError(new ExecutionError(
                UUID.randomUUID(),
                execution.getExecutionId(),
                StepNames.LOAD,
                ErrorType.TECHNICAL,
                ErrorSeverity.CRITICAL,
                "FINAL_LOAD_FAILURE",
                ex.getMessage() != null ? ex.getMessage() : "Final load failed",
                DatabaseLoadSupport.stackTrace(ex),
                targetConfig.getFinalTable()
            ));
            return LoadResult.failure(ex.getMessage());
        }
    }

    private void registerStagingValidationFailure(PipelineExecution execution, StagingValidationResult validationResult) {
        execution.addError(new ExecutionError(
            UUID.randomUUID(),
            execution.getExecutionId(),
            StepNames.LOAD,
            ErrorType.DATA_QUALITY,
            ErrorSeverity.CRITICAL,
            "STAGING_VALIDATION_FAILED",
            String.join("; ", validationResult.getDetails()),
            null,
            "expected=" + validationResult.getExpectedRows() + ", actual=" + validationResult.getActualRows()
        ));
    }
}
