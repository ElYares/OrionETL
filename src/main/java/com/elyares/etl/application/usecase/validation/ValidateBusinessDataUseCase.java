package com.elyares.etl.application.usecase.validation;

import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationResult;

import java.util.List;

/**
 * Caso de uso para validar reglas de negocio y calidad sobre los datos.
 */
public class ValidateBusinessDataUseCase {

    private final DataValidator businessValidator;

    public ValidateBusinessDataUseCase(DataValidator businessValidator) {
        this.businessValidator = businessValidator;
    }

    public ValidationResult execute(List<RawRecord> records, ValidationConfig validationConfig) {
        return businessValidator.validate(records, validationConfig);
    }
}
