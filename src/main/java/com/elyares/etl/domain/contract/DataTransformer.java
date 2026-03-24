package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;

import java.util.List;

/**
 * Contrato de dominio para la transformación de registros en bruto durante el pipeline ETL.
 *
 * <p>Cada implementación encapsula la lógica de transformación específica de un pipeline:
 * mapeo de campos, conversión de tipos, enriquecimiento de datos, aplicación de reglas
 * de negocio, etc. La transformación convierte {@code RawRecord}s en registros
 * procesados y, si es necesario, registra rechazos por transformación.</p>
 *
 * <p>El método {@link #getPipelineName()} vincula cada transformador con el pipeline
 * para el que fue diseñado, permitiendo su resolución por nombre en el contexto de ejecución.</p>
 */
public interface DataTransformer {

    /**
     * Transforma una lista de registros en bruto en registros procesados y normalizados.
     *
     * <p>Aplica las reglas de transformación propias del pipeline sobre cada
     * {@code RawRecord} y produce la lista de {@code ProcessedRecord}s resultante.
     * Los registros que no superen las transformaciones deben ser gestionados internamente
     * (rechazados, omitidos o notificados según la política del transformador).</p>
     *
     * @param records   lista de registros en bruto extraídos de la fuente de datos.
     * @param execution contexto de la ejecución del pipeline en curso.
     * @param pipeline  definición completa del pipeline con su configuración de transformación.
     * @return resultado agregado de transformación con registros procesados y rechazados.
     */
    TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution);

    /**
     * Devuelve el nombre del pipeline al que pertenece este transformador.
     *
     * <p>Se utiliza para resolver el transformador correcto en función del nombre
     * del pipeline configurado en la ejecución.</p>
     *
     * @return nombre canónico del pipeline asociado a esta implementación.
     */
    String getPipelineName();

    /**
     * Orden de ejecución dentro de una cadena de transformadores.
     *
     * @return prioridad del transformador; menor valor se ejecuta primero
     */
    default int getOrder() {
        return 0;
    }
}
