package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.target.LoadResult;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.target.TargetConfig;

import java.util.List;

/**
 * Contrato de dominio para la carga de registros procesados en el destino final del pipeline ETL.
 *
 * <p>Las implementaciones son responsables de persistir la lista de {@code ProcessedRecord}s
 * en el sistema de destino (base de datos, archivo, data warehouse, etc.) aplicando la
 * estrategia de carga definida en {@code TargetConfig} (INSERT, UPSERT, REPLACE).</p>
 *
 * <p>El resultado de la operación queda encapsulado en un {@code LoadResult} que incluye
 * contadores de registros insertados, actualizados y rechazados, así como metadatos
 * de la operación.</p>
 */
public interface DataLoader {

    /**
     * Persiste la lista de registros procesados en el destino configurado.
     *
     * <p>Aplica la estrategia de carga especificada en {@code targetConfig} para determinar
     * si los registros deben insertarse, actualizarse o reemplazar el contenido existente.
     * Encapsula el resultado completo de la operación en el objeto retornado.</p>
     *
     * @param records      lista de registros procesados y validados listos para su carga.
     * @param targetConfig configuración del destino de datos (conexión, tabla, estrategia, etc.).
     * @param execution    contexto de la ejecución del pipeline en curso.
     * @return {@code LoadResult} con los contadores de operación y metadatos del resultado de carga.
     */
    LoadResult load(List<ProcessedRecord> records, TargetConfig targetConfig, PipelineExecution execution);
}
