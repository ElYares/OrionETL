package com.elyares.etl.infrastructure.config;

import com.elyares.etl.application.usecase.transformation.TransformDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateBusinessDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidateInputDataUseCase;
import com.elyares.etl.application.usecase.validation.ValidationChainExecutor;
import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.contract.DataValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wiring explícito de los casos de uso de Fase 5.
 */
@Configuration
public class Phase5UseCaseConfig {

    @Bean
    TransformDataUseCase transformDataUseCase(List<DataTransformer> transformers) {
        return new TransformDataUseCase(transformers);
    }

    @Bean
    ValidateInputDataUseCase validateInputDataUseCase(ValidationChainExecutor validationChainExecutor,
                                                      @Qualifier("schemaValidator") DataValidator schemaValidator) {
        return new ValidateInputDataUseCase(validationChainExecutor, schemaValidator);
    }

    @Bean
    ValidateBusinessDataUseCase validateBusinessDataUseCase(ValidationChainExecutor validationChainExecutor,
                                                            @Qualifier("businessValidator") DataValidator businessValidator) {
        return new ValidateBusinessDataUseCase(validationChainExecutor, businessValidator);
    }
}
