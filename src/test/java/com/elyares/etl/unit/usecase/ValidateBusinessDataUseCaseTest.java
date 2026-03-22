package com.elyares.etl.unit.usecase;

import com.elyares.etl.application.usecase.validation.ValidateBusinessDataUseCase;
import com.elyares.etl.domain.contract.DataValidator;
import com.elyares.etl.domain.model.validation.ValidationResult;
import com.elyares.etl.fixtures.SampleDataFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValidateBusinessDataUseCaseTest {

    @Test
    void shouldDelegateBusinessValidation() {
        var records = List.of(SampleDataFactory.aRawRecord());
        var config = SampleDataFactory.aValidationConfig();
        ValidationResult expected = ValidationResult.ok();

        DataValidator validator = mock(DataValidator.class);
        when(validator.validate(records, config)).thenReturn(expected);

        ValidateBusinessDataUseCase useCase = new ValidateBusinessDataUseCase(validator);

        assertThat(useCase.execute(records, config)).isEqualTo(expected);
    }
}
