package com.elyares.etl.domain.rules;

import com.elyares.etl.domain.model.validation.DataQualityReport;
import com.elyares.etl.domain.model.validation.ValidationConfig;

/**
 * Regla de dominio que determina si la tasa de error de un lote supera el umbral
 * configurado y por tanto debe abortarse la ejecución.
 *
 * <p>Se aplica en dos momentos del flujo ETL:
 * <ul>
 *   <li>Tras la validación de esquema (paso 3).</li>
 *   <li>Tras la validación de negocio (paso 5).</li>
 * </ul>
 * Si {@link ValidationConfig#isAbortOnThresholdBreach()} es {@code false}, la regla
 * reporta la brecha pero no aborta.</p>
 */
public class ErrorThresholdRule {

    /**
     * Evalúa si el reporte de calidad indica que se debe abortar la ejecución.
     *
     * @param report  reporte de calidad del lote procesado
     * @param config  configuración de validación con el umbral y la política de aborto
     * @return {@code true} si el umbral fue superado Y la configuración indica abortar
     */
    public boolean shouldAbort(DataQualityReport report, ValidationConfig config) {
        return report.thresholdBreached() && config.isAbortOnThresholdBreach();
    }

    /**
     * Comprueba únicamente si la tasa de error superó el umbral, sin considerar
     * la política de aborto.
     *
     * @param report reporte de calidad del lote
     * @return {@code true} si la tasa de error supera el umbral configurado
     */
    public boolean isThresholdBreached(DataQualityReport report) {
        return report.thresholdBreached();
    }
}
