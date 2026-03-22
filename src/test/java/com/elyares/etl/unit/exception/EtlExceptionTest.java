package com.elyares.etl.unit.exception;

import com.elyares.etl.shared.exception.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias para la jerarquía de excepciones del sistema ETL.
 *
 * <p>Verifica que cada excepción transporte correctamente su código de error,
 * que los constructores de conveniencia formateen los mensajes según lo esperado
 * y que las listas de errores de validación se almacenen de forma inmutable.</p>
 */
class EtlExceptionTest {

    /**
     * Verifica que {@link EtlException} almacena correctamente el código de error
     * y el mensaje al ser construida con ambos parámetros.
     */
    @Test
    void etlExceptionShouldCarryErrorCode() {
        EtlException ex = new EtlException("TEST_001", "test message");
        assertThat(ex.getErrorCode()).isEqualTo("TEST_001");
        assertThat(ex.getMessage()).isEqualTo("test message");
    }

    /**
     * Verifica que {@link PipelineNotFoundException} incluye el identificador
     * del pipeline en el mensaje y usa el código {@code ETL_PIPELINE_NOT_FOUND}.
     */
    @Test
    void pipelineNotFoundShouldFormatMessage() {
        PipelineNotFoundException ex = new PipelineNotFoundException("pipeline-abc");
        assertThat(ex.getMessage()).contains("pipeline-abc");
        assertThat(ex.getErrorCode()).isEqualTo("ETL_PIPELINE_NOT_FOUND");
    }

    /**
     * Verifica que {@link ValidationException} almacena la lista completa de
     * errores de validación proporcionada en el constructor.
     */
    @Test
    void validationExceptionShouldCarryErrors() {
        List<String> errors = List.of("field1 is required", "field2 invalid");
        ValidationException ex = new ValidationException("Validation failed", errors);
        assertThat(ex.getValidationErrors()).hasSize(2);
    }

    /**
     * Verifica que {@link ExecutionConflictException} incluye el identificador
     * del pipeline en el mensaje y usa el código {@code ETL_EXEC_CONFLICT}.
     */
    @Test
    void executionConflictShouldFormatMessage() {
        ExecutionConflictException ex = new ExecutionConflictException("sales-pipeline");
        assertThat(ex.getMessage()).contains("sales-pipeline");
        assertThat(ex.getErrorCode()).isEqualTo("ETL_EXEC_CONFLICT");
    }
}
