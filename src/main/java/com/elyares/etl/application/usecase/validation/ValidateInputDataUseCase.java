package com.elyares.etl.application.usecase.validation;

import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationResult;

import java.util.List;

/**
 * Caso de uso para validar estructura/esquema de datos de entrada.
 */
public class ValidateInputDataUseCase {

    private final DataValidator schemaValidator;

    public ValidateInputDataUseCase(DataValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    public ValidationResult execute(List<RawRecord> records, ValidationConfig validationConfig) {
        return schemaValidator.validate(records, validationConfig);
    }
}
