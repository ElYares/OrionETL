package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.validation.ValidationResult;

import java.util.List;

/**
 * Contrato de dominio para la validación de registros en bruto durante el pipeline ETL.
 *
 * <p>Las implementaciones aplican reglas de validación configuradas sobre los {@code RawRecord}s
 * extraídos, antes de que pasen a la fase de transformación. El resultado agrega los registros
 * válidos y rechazados, así como el detalle de los errores encontrados.</p>
 *
 * <p>El método {@link #getValidatorName()} identifica unívocamente al validador dentro del
 * contexto de ejecución del pipeline, facilitando el registro de auditoría y la trazabilidad.</p>
 */
public interface DataValidator {

    /**
     * Ejecuta las reglas de validación sobre la lista de registros en bruto proporcionada.
     *
     * <p>Evalúa cada {@code RawRecord} contra las reglas definidas en {@code config}
     * (obligatoriedad de campos, rangos de valores, formatos, unicidad, etc.) y produce
     * un {@code ValidationResult} que separa registros válidos de rechazados e incluye
     * el detalle de cada violación detectada.</p>
     *
     * @param records lista de registros en bruto sobre los que aplicar las reglas de validación.
     * @param config  configuración con las reglas y parámetros de validación a aplicar.
     * @return {@code ValidationResult} con el resumen de registros válidos, rechazados y errores.
     */
    ValidationResult validate(List<RawRecord> records, ValidationConfig config);

    /**
     * Devuelve el nombre identificativo de este validador.
     *
     * <p>Utilizado para registrar qué validador procesó cada lote de registros en la
     * auditoría de la ejecución.</p>
     *
     * @return nombre canónico del validador.
     */
    String getValidatorName();
}
