package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.validation.ValidateInputDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidationChainExecutor;
import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.fixtures.SampleDataFactory;
import com.elyares.etl.infrastructure.validator.QualityValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValidateInputDataUseCaseTest {

    @Test
    void shouldDelegateSchemaValidation() {
        var records = List.of(SampleDataFactory.aRawRecord());
        var config = SampleDataFactory.aValidationConfig();
        ValidationResult expected = ValidationResult.ok();

        DataValidator validator = mock(DataValidator.class);
        when(validator.validate(records, config)).thenReturn(expected);

        ValidateInputDataUseCase useCase = new ValidateInputDataUseCase(
            new ValidationChainExecutor(new QualityValidator()),
            validator
        );

        assertThat(useCase.execute(records, config).getErrors()).isEqualTo(expected.getErrors());
    }
}
