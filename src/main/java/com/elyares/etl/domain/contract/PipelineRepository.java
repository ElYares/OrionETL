package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.valueobject.PipelineId;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de dominio para la persistencia y recuperación de definiciones de pipelines ETL.
 *
 * <p>Define las operaciones de acceso a datos sobre la entidad agregada {@code Pipeline}.
 * Siguiendo el patrón de arquitectura hexagonal, las implementaciones concretas residen
 * en la capa de infraestructura, manteniéndose el dominio libre de dependencias tecnológicas.</p>
 */
public interface PipelineRepository {

    /**
     * Persiste o actualiza la definición de un pipeline en el almacenamiento.
     *
     * <p>Si el pipeline ya existe (mismo {@code PipelineId}), su definición es actualizada;
     * en caso contrario, se crea un nuevo registro.</p>
     *
     * @param pipeline instancia de {@code Pipeline} a guardar.
     * @return la instancia persistida, potencialmente enriquecida con datos generados
     *         por la capa de persistencia.
     */
    Pipeline save(Pipeline pipeline);

    /**
     * Busca un pipeline por su identificador único.
     *
     * @param id identificador único del pipeline a recuperar.
     * @return {@code Optional} con el pipeline encontrado, o vacío si no existe.
     */
    Optional<Pipeline> findById(PipelineId id);

    /**
     * Busca un pipeline por su nombre canónico.
     *
     * <p>El nombre de pipeline se trata como un identificador de negocio único
     * dentro del sistema ETL.</p>
     *
     * @param name nombre del pipeline a buscar.
     * @return {@code Optional} con el pipeline encontrado, o vacío si no existe ninguno con ese nombre.
     */
    Optional<Pipeline> findByName(String name);

    /**
     * Recupera todos los pipelines registrados en el sistema, independientemente de su estado.
     *
     * @return lista con todos los pipelines existentes; vacía si no hay ninguno registrado.
     */
    List<Pipeline> findAll();

    /**
     * Recupera únicamente los pipelines con estado {@code ACTIVE}.
     *
     * <p>Utilizado por el planificador y el motor de ejecución para determinar
     * qué pipelines están disponibles para ser ejecutados.</p>
     *
     * @return lista de pipelines activos; vacía si no existe ninguno activo.
     */
    List<Pipeline> findAllActive();
}
