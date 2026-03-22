package com.elyares.etl.domain.contract;

import com.elyares.etl.domain.model.audit.AuditRecord;
import com.elyares.etl.domain.valueobject.ExecutionId;

import java.util.List;

/**
 * Puerto de dominio para la persistencia y consulta de registros de auditoría del pipeline ETL.
 *
 * <p>Los registros de auditoría capturan eventos relevantes ocurridos durante la ejecución
 * del pipeline (inicio, fin, errores, cambios de estado, etc.) y sirven como trazabilidad
 * operativa y fuente de evidencia para análisis posteriores.</p>
 *
 * <p>Siguiendo el patrón de arquitectura hexagonal, las implementaciones concretas residen
 * en la capa de infraestructura, manteniéndose el dominio libre de dependencias tecnológicas.</p>
 */
public interface AuditRepository {

    /**
     * Persiste un registro de auditoría en el almacenamiento.
     *
     * @param record instancia de {@code AuditRecord} a guardar.
     * @return la instancia persistida, potencialmente enriquecida con datos generados
     *         por la capa de persistencia (identificador generado, timestamp de inserción, etc.).
     */
    AuditRecord save(AuditRecord record);

    /**
     * Recupera todos los registros de auditoría asociados a una ejecución específica.
     *
     * <p>Los registros se devuelven en orden cronológico ascendente para facilitar
     * la reconstrucción del flujo de eventos de la ejecución.</p>
     *
     * @param executionId identificador de la ejecución cuyos registros de auditoría se desean obtener.
     * @return lista de {@code AuditRecord}s asociados a la ejecución; vacía si no existe ninguno.
     */
    List<AuditRecord> findByExecutionId(ExecutionId executionId);
}
