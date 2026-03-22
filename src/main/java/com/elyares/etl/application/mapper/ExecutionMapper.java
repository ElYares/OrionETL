package com.elyares.etl.application.mapper;

import com.elyares.etl.application.dto.AuditRecordDto;
import com.elyares.etl.application.dto.ExecutionMetricDto;
import com.elyares.etl.application.dto.ExecutionStatusDto;
import com.elyares.etl.application.dto.ExecutionStepDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.model.execution.ExecutionMetric;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.execution.PipelineExecutionStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct para convertir entidades de ejecución del dominio a sus DTOs
 * correspondientes.
 *
 * <p>Centraliza las conversiones de los siguientes agregados y entidades del dominio:</p>
 * <ul>
 *   <li>{@link PipelineExecution} → {@link PipelineExecutionDto} (vista completa)</li>
 *   <li>{@link PipelineExecution} → {@link ExecutionStatusDto} (vista resumida para polling)</li>
 *   <li>{@link PipelineExecutionStep} → {@link ExecutionStepDto}</li>
 *   <li>{@link AuditRecord} → {@link AuditRecordDto}</li>
 *   <li>{@link ExecutionMetric} → {@link ExecutionMetricDto}</li>
 * </ul>
 *
 * <p>MapStruct genera en tiempo de compilación una implementación concreta registrada
 * como bean Spring ({@code componentModel = "spring"}). Las anotaciones {@link Mapping}
 * resuelven las discrepancias entre los value objects del dominio (como {@code ExecutionId},
 * {@code PipelineId} o {@code RecordCount}) y los tipos primitivos o {@code String}
 * esperados por los records DTO.</p>
 *
 * <p>El método {@link #toStepDto(PipelineExecutionStep)} no requiere anotaciones de mapeo
 * explícitas porque los nombres de campo del dominio coinciden directamente con los del DTO.</p>
 *
 * @see PipelineExecution
 * @see PipelineExecutionStep
 * @see AuditRecord
 * @see ExecutionMetric
 */
@Mapper(componentModel = "spring")
public interface ExecutionMapper {

    /**
     * Convierte una {@link PipelineExecution} a su DTO completo incluyendo la lista de
     * pasos individuales.
     *
     * <p>Requiere el parámetro adicional {@code pipelineName} porque el nombre del pipeline
     * no forma parte del agregado {@code PipelineExecution} y debe ser suministrado por el
     * caso de uso llamante (tras una consulta al repositorio de pipelines).</p>
     *
     * <p>Los conteos de registros ({@code totalRead}, {@code totalTransformed},
     * {@code totalRejected}, {@code totalLoaded}) se extraen del valor interno de sus
     * respectivos value objects {@code RecordCount} mediante la expresión de ruta
     * {@code *.value}. La lista {@code steps} se convierte elemento a elemento invocando
     * internamente {@link #toStepDto(PipelineExecutionStep)}.</p>
     *
     * @param execution    ejecución de dominio a convertir; no debe ser {@code null}
     * @param pipelineName nombre del pipeline asociado a la ejecución; no debe ser
     *                     {@code null}
     * @return {@link PipelineExecutionDto} con estado completo, conteos acumulados y
     *         lista de pasos; nunca {@code null}
     */
    @Mapping(source = "execution.executionId.value", target = "executionId")
    @Mapping(source = "execution.pipelineId.value", target = "pipelineId")
    @Mapping(source = "execution.totalRead.value", target = "totalRead")
    @Mapping(source = "execution.totalTransformed.value", target = "totalTransformed")
    @Mapping(source = "execution.totalRejected.value", target = "totalRejected")
    @Mapping(source = "execution.totalLoaded.value", target = "totalLoaded")
    @Mapping(source = "execution.steps", target = "steps")
    @Mapping(source = "pipelineName", target = "pipelineName")
    PipelineExecutionDto toDto(PipelineExecution execution, String pipelineName);

    /**
     * Convierte una {@link PipelineExecution} a un DTO de estado resumido, optimizado
     * para consultas de polling de alta frecuencia.
     *
     * <p>Solo incluye los campos mínimos necesarios para determinar si la ejecución ha
     * terminado y con qué resultado: identificador, estado, conteos agregados y momento
     * de finalización. No incluye la lista de pasos para minimizar el coste de
     * serialización.</p>
     *
     * @param execution ejecución de dominio a convertir; no debe ser {@code null}
     * @return {@link ExecutionStatusDto} con los campos mínimos para polling;
     *         nunca {@code null}
     */
    @Mapping(source = "executionId.value", target = "executionId")
    @Mapping(source = "totalRead.value", target = "totalRead")
    @Mapping(source = "totalLoaded.value", target = "totalLoaded")
    @Mapping(source = "totalRejected.value", target = "totalRejected")
    ExecutionStatusDto toStatusDto(PipelineExecution execution);

    /**
     * Convierte un {@link PipelineExecutionStep} a su DTO de representación externa.
     *
     * <p>No requiere anotaciones {@link Mapping} adicionales porque los nombres de campo
     * del dominio ({@code stepName}, {@code stepOrder}, {@code status}, {@code startedAt},
     * {@code finishedAt}, {@code recordsProcessed}, {@code errorDetail}) coinciden
     * directamente con los del record {@link ExecutionStepDto}.</p>
     *
     * @param step paso de ejecución de dominio a convertir; no debe ser {@code null}
     * @return {@link ExecutionStepDto} con el estado del paso; nunca {@code null}
     */
    ExecutionStepDto toStepDto(PipelineExecutionStep step);

    /**
     * Convierte un {@link AuditRecord} a su DTO de representación externa.
     *
     * <p>Los identificadores {@code executionId} y {@code pipelineId} se extraen del valor
     * interno de sus respectivos value objects mediante la expresión de ruta {@code *.value}.</p>
     *
     * @param record registro de auditoría de dominio a convertir; no debe ser {@code null}
     * @return {@link AuditRecordDto} con los datos públicos del evento auditado;
     *         nunca {@code null}
     */
    @Mapping(source = "executionId.value", target = "executionId")
    @Mapping(source = "pipelineId.value", target = "pipelineId")
    AuditRecordDto toAuditDto(AuditRecord record);

    /**
     * Convierte un {@link ExecutionMetric} a su DTO de representación externa.
     *
     * <p>El identificador {@code executionId} se extrae del valor interno del value object
     * {@code ExecutionId} mediante la expresión de ruta {@code executionId.value}. El
     * resto de campos ({@code metricName}, {@code metricValue}, {@code recordedAt}) se
     * mapean automáticamente por nombre.</p>
     *
     * @param metric métrica de dominio a convertir; no debe ser {@code null}
     * @return {@link ExecutionMetricDto} con los datos de la métrica; nunca {@code null}
     */
    @Mapping(source = "executionId.value", target = "executionId")
    ExecutionMetricDto toMetricDto(ExecutionMetric metric);
}
