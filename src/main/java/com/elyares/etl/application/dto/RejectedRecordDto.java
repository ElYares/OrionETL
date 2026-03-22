package com.elyares.etl.application.dto;

import java.time.Instant;
import java.util.Map;

/**
 * DTO que representa un registro rechazado durante el proceso ETL.
 *
 * <p>Cada instancia encapsula la información necesaria para identificar, localizar y
 * auditar un registro que no superó las validaciones del pipeline. Los rechazos pueden
 * ocurrir en distintos pasos ({@code VALIDATE_SCHEMA}, {@code TRANSFORM},
 * {@code VALIDATE_BUSINESS}, {@code LOAD}) y el campo {@code stepName} identifica
 * exactamente en cuál se produjo el rechazo.</p>
 *
 * <p>El mapa {@code rawData} contiene los datos originales tal como fueron leídos de la
 * fuente, antes de cualquier transformación. Esto permite a los operadores corregir la
 * fuente de datos o implementar reprocesados selectivos.</p>
 *
 * <p>Instancias de este record son producidas al consultar el repositorio de registros
 * rechazados (por ejemplo, {@code GET /executions/{executionId}/rejected-records}).</p>
 *
 * @param executionId     identificador UUID de la ejecución en la que se produjo el rechazo;
 *                        corresponde al valor interno de {@code ExecutionId}
 * @param stepName        nombre canónico del paso ETL donde fue rechazado el registro
 *                        (por ejemplo {@code "VALIDATE_SCHEMA"} o {@code "TRANSFORM"})
 * @param rowNumber       número de fila (base 1) en la fuente de datos original; permite
 *                        localizar el registro en el fichero o tabla de origen
 * @param rawData         datos crudos del registro rechazado tal como fueron leídos de
 *                        la fuente, representados como mapa clave-valor; nunca {@code null}
 * @param rejectionReason descripción legible del motivo por el que el registro fue
 *                        rechazado (por ejemplo {@code "campo 'edad' no es un entero válido"})
 * @param rejectedAt      marca de tiempo UTC en que se registró el rechazo dentro del
 *                        sistema; nunca {@code null}
 */
public record RejectedRecordDto(
    String executionId,
    String stepName,
    long rowNumber,
    Map<String, Object> rawData,
    String rejectionReason,
    Instant rejectedAt
) {}
