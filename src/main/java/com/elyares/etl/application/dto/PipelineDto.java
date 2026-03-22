package com.elyares.etl.application.dto;

import com.elyares.etl.domain.enums.PipelineStatus;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.enums.TargetType;
import java.time.Instant;

/**
 * DTO de representación de un pipeline ETL para exposición externa (API, CLI).
 *
 * <p>Actúa como proyección segura del agregado de dominio {@code Pipeline}: contiene
 * únicamente los campos que son relevantes y seguros para el consumidor externo. No expone
 * configuraciones internas sensibles como credenciales de conexión, propiedades de cifrado
 * ni detalles de implementación del conector.</p>
 *
 * <p>Instancias de este record son producidas por {@code PipelineMapper#toDto(Pipeline)} y
 * son inmutables por diseño al ser un {@code record} de Java.</p>
 *
 * @param id          identificador UUID del pipeline, representado como cadena;
 *                    corresponde al valor interno de {@code PipelineId}
 * @param name        nombre único del pipeline dentro del sistema
 * @param version     versión semántica del pipeline (por ejemplo {@code "1.0.0"})
 * @param description descripción legible del propósito y alcance del pipeline
 * @param status      estado actual del ciclo de vida del pipeline
 *                    ({@code ACTIVE}, {@code INACTIVE}, {@code DEPRECATED}, etc.)
 * @param sourceType  tipo tecnológico de la fuente de datos (por ejemplo
 *                    {@code CSV}, {@code JDBC}, {@code REST_API})
 * @param targetType  tipo tecnológico del destino de datos (por ejemplo
 *                    {@code POSTGRESQL}, {@code ELASTICSEARCH}, {@code S3})
 * @param createdAt   marca de tiempo UTC en que el pipeline fue registrado en el sistema;
 *                    nunca {@code null}
 * @param updatedAt   marca de tiempo UTC de la última modificación del pipeline;
 *                    nunca {@code null}
 *
 * @see PipelineStatus
 * @see SourceType
 * @see TargetType
 */
public record PipelineDto(
    String id,
    String name,
    String version,
    String description,
    PipelineStatus status,
    SourceType sourceType,
    TargetType targetType,
    Instant createdAt,
    Instant updatedAt
) {}
