package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.enums.SourceType;

/**
 * Contrato de dominio para la extracción de datos en la fase inicial del pipeline ETL.
 *
 * <p>Cada implementación es responsable de leer registros en bruto desde un tipo de origen
 * específico (CSV, API, base de datos, etc.) y encapsularlos en un {@code ExtractionResult}.
 * La selección de la implementación concreta se realiza mediante el método {@link #supports(SourceType)}.</p>
 *
 * <p>Las implementaciones deben ser stateless y reutilizables entre ejecuciones.</p>
 */
public interface DataExtractor {

    /**
     * Ejecuta la extracción de datos desde el origen configurado.
     *
     * <p>Lee los registros en bruto según los parámetros definidos en {@code sourceConfig}
     * y los encapsula en un {@code ExtractionResult} que incluye los registros leídos,
     * contadores y metadatos de la operación.</p>
     *
     * @param sourceConfig configuración del origen de datos (ruta, credenciales, formato, etc.).
     * @param execution    contexto de la ejecución del pipeline en curso.
     * @return {@code ExtractionResult} con los registros extraídos y los metadatos resultantes.
     */
    ExtractionResult extract(SourceConfig sourceConfig, PipelineExecution execution);

    /**
     * Indica si esta implementación es capaz de procesar el tipo de origen especificado.
     *
     * <p>Utilizado por el mecanismo de selección de extractores para determinar qué
     * implementación delegar en función de la configuración del pipeline.</p>
     *
     * @param sourceType tipo de origen de datos a evaluar.
     * @return {@code true} si esta implementación soporta el {@code sourceType} indicado;
     *         {@code false} en caso contrario.
     */
    boolean supports(SourceType sourceType);
}
