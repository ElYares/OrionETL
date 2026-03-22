package com.elyares.etl.application.mapper;

import com.elyares.etl.application.dto.PipelineDto;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct para convertir entre el agregado de dominio {@link Pipeline}
 * y su DTO de representación externa {@link PipelineDto}.
 *
 * <p>La conversión extrae únicamente los campos seguros para exposición pública,
 * omitiendo configuraciones internas como credenciales de conexión, propiedades de
 * cifrado y detalles de implementación del conector de origen o destino.</p>
 *
 * <p>MapStruct genera en tiempo de compilación una implementación concreta de esta
 * interfaz registrada como bean Spring ({@code componentModel = "spring"}), lo que
 * permite inyectarla directamente en cualquier componente de la capa de aplicación.</p>
 *
 * <p>Las anotaciones {@link Mapping} resuelven las discrepancias entre el modelo de
 * dominio (que usa value objects como {@code PipelineId} o {@code SourceConfig}) y el
 * record plano {@link PipelineDto}:</p>
 * <ul>
 *   <li>{@code id.value} — extrae el {@code String} envuelto en el value object
 *       {@code PipelineId}</li>
 *   <li>{@code sourceConfig.type} — extrae el {@code SourceType} del value object
 *       {@code SourceConfig}</li>
 *   <li>{@code targetConfig.type} — extrae el {@code TargetType} del value object
 *       {@code TargetConfig}</li>
 * </ul>
 *
 * @see Pipeline
 * @see PipelineDto
 */
@Mapper(componentModel = "spring")
public interface PipelineMapper {

    /**
     * Convierte un agregado {@link Pipeline} de dominio a su proyección externa
     * {@link PipelineDto}.
     *
     * <p>Los campos derivados de value objects se mapean mediante expresiones de ruta
     * anidada de MapStruct: {@code id.value}, {@code sourceConfig.type} y
     * {@code targetConfig.type}. El resto de campos ({@code name}, {@code version},
     * {@code description}, {@code status}, {@code createdAt}, {@code updatedAt}) se
     * mapean automáticamente por nombre sin necesidad de anotación explícita.</p>
     *
     * @param pipeline agregado de dominio a convertir; no debe ser {@code null}
     * @return {@link PipelineDto} con los campos públicos del pipeline; nunca {@code null}
     */
    @Mapping(source = "id.value", target = "id")
    @Mapping(source = "sourceConfig.type", target = "sourceType")
    @Mapping(source = "targetConfig.type", target = "targetType")
    PipelineDto toDto(Pipeline pipeline);
}
