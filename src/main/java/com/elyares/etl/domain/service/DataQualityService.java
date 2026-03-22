package com.elyares.etl.domain.service;

import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.valueobject.ErrorThreshold;

/**
 * Servicio de dominio responsable de calcular y evaluar la calidad de datos
 * de un lote procesado por el pipeline ETL.
 *
 * <p>Calcula la tasa de error a partir del total de registros y los rechazados,
 * la compara contra el umbral configurado y determina si la ejecución debe
 * abortarse por superar dicho umbral.</p>
 *
 * <p>Clase pura de dominio — sin dependencias de Spring ni de infraestructura.</p>
 */
public class DataQualityService {

    /**
     * Genera un {@link DataQualityReport} con la evaluación de calidad del lote.
     *
     * @param totalRead       total de registros leídos de la fuente
     * @param totalRejected   total de registros rechazados en validación
     * @param errorThreshold  umbral de error configurado para el pipeline
     * @return reporte de calidad con tasa de error y flag de brecha de umbral
     */
    public DataQualityReport evaluateQuality(long totalRead,
                                              long totalRejected,
                                              ErrorThreshold errorThreshold) {
        return DataQualityReport.of(totalRead, totalRejected, errorThreshold.percentValue());
    }

    /**
     * Determina si la ejecución debe abortarse basándose en el reporte de calidad
     * y la configuración de validación del pipeline.
     *
     * <p>Retorna {@code true} únicamente si el umbral fue superado Y la configuración
     * del pipeline indica que se debe abortar en ese caso
     * ({@link ValidationConfig#isAbortOnThresholdBreach()}).</p>
     *
     * @param report reporte de calidad generado por {@link #evaluateQuality}
     * @param config configuración de validación del pipeline
     * @return {@code true} si la ejecución debe abortarse; {@code false} en caso contrario
     */
    public boolean isAbortRequired(DataQualityReport report, ValidationConfig config) {
        return report.thresholdBreached() && config.isAbortOnThresholdBreach();
    }
}
