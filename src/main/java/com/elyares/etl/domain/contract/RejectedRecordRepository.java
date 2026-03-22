package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;

import java.util.List;

/**
 * Puerto de dominio para la persistencia y consulta de registros rechazados durante
 * la fase de validación del pipeline ETL.
 *
 * <p>Un {@code RejectedRecord} representa un registro en bruto que no superó las reglas
 * de validación y fue excluido del flujo de transformación y carga. Su persistencia
 * permite la revisión posterior, la corrección manual y el rereprocesamiento selectivo.</p>
 *
 * <p>Siguiendo el patrón de arquitectura hexagonal, las implementaciones concretas residen
 * en la capa de infraestructura, manteniéndose el dominio libre de dependencias tecnológicas.</p>
 */
public interface RejectedRecordRepository {

    /**
     * Persiste en lote todos los registros rechazados de una ejecución dada.
     *
     * <p>Asocia cada {@code RejectedRecord} al {@code ExecutionId} indicado para garantizar
     * la trazabilidad de la ejecución que originó el rechazo.</p>
     *
     * @param rejectedRecords lista de registros rechazados a persistir.
     * @param executionId     identificador de la ejecución a la que pertenecen los registros.
     */
    void saveAll(List<RejectedRecord> rejectedRecords, ExecutionId executionId);

    /**
     * Recupera todos los registros rechazados asociados a una ejecución específica.
     *
     * @param executionId identificador de la ejecución cuyos registros rechazados se desean obtener.
     * @return lista de {@code RejectedRecord}s de la ejecución; vacía si no hubo rechazos.
     */
    List<RejectedRecord> findByExecutionId(ExecutionId executionId);

    /**
     * Devuelve el número total de registros rechazados en una ejecución específica.
     *
     * <p>Permite evaluar si se ha superado el umbral de error configurado en el pipeline
     * ({@code ErrorThreshold}) sin necesidad de cargar todos los registros en memoria.</p>
     *
     * @param executionId identificador de la ejecución cuyo conteo de rechazados se desea obtener.
     * @return número de registros rechazados en la ejecución indicada.
     */
    long countByExecutionId(ExecutionId executionId);
}
