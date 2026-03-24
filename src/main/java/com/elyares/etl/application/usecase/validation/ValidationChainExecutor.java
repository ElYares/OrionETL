package com.elyares.etl.application.usecase.validation;

import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.infrastructure.validator.QualityValidator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Coordinador de validación que enriquece el resultado con calidad de datos.
 */
@Component
public class ValidationChainExecutor {

    private final QualityValidator qualityValidator;

    public ValidationChainExecutor(QualityValidator qualityValidator) {
        this.qualityValidator = qualityValidator;
    }

    public ValidationResult execute(List<RawRecord> records,
                                    ValidationConfig validationConfig,
                                    DataValidator validator) {
        // Primero ejecutamos la validación específica del paso; después enriquecemos con métricas
        // de calidad para que el orquestador pueda decidir si aborta por threshold.
        ValidationResult base = validator.validate(records, validationConfig);
        return qualityValidator.enrich(records, base, validationConfig);
    }
}
