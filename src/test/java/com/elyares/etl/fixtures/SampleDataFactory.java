package com.elyares.etl.fixtures;

import com.elyares.etl.domain.enums.*;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.domain.model.target.TargetConfig;
import com.elyares.etl.domain.model.validation.ValidationConfig;
import com.elyares.etl.domain.model.pipeline.*;
import com.elyares.etl.domain.valueobject.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fábrica de datos de prueba reutilizables para los tests del sistema ETL.
 *
 * <p>Proporciona métodos estáticos que construyen instancias preconfiguradas de
 * los objetos de dominio más utilizados en las pruebas unitarias e de integración.
 * El uso de esta fábrica centraliza la creación de fixtures, evitando la
 * duplicación de datos de prueba y facilitando el mantenimiento ante cambios
 * en los constructores del dominio.</p>
 *
 * <p>Esta clase no puede ser instanciada ni extendida.</p>
 */
public final class SampleDataFactory {

    private SampleDataFactory() {}

    /**
     * Genera un {@link PipelineId} con un UUID aleatorio.
     *
     * @return nuevo identificador de pipeline único
     */
    public static PipelineId aPipelineId() {
        return PipelineId.generate();
    }

    /**
     * Genera un {@link ExecutionId} con un UUID aleatorio.
     *
     * @return nuevo identificador de ejecución único
     */
    public static ExecutionId anExecutionId() {
        return ExecutionId.generate();
    }

    /**
     * Crea un {@link RawRecord} de prueba con datos de transacción predefinidos.
     *
     * <p>El registro contiene los campos {@code transaction_id}, {@code customer_id},
     * {@code amount} y {@code sale_date} con valores fijos adecuados para
     * escenarios de prueba estándar.</p>
     *
     * @return registro crudo de prueba con número de fila 1 y fuente {@code test-source.csv}
     */
    public static RawRecord aRawRecord() {
        Map<String, Object> data = new HashMap<>();
        data.put("transaction_id", "TXN-001");
        data.put("customer_id", "CUST-001");
        data.put("amount", "150.00");
        data.put("sale_date", "2026-01-15");
        return new RawRecord(1L, data, "test-source.csv", Instant.now());
    }

    /**
     * Crea un {@link RawRecord} de prueba con el número de fila y los datos indicados.
     *
     * @param rowNumber número de fila del registro dentro del archivo fuente
     * @param data      mapa de columnas y valores del registro
     * @return registro crudo con la fuente {@code test-source.csv}
     */
    public static RawRecord aRawRecord(long rowNumber, Map<String, Object> data) {
        return new RawRecord(rowNumber, data, "test-source.csv", Instant.now());
    }

    /**
     * Crea una configuración de fuente de tipo CSV apuntando a un archivo temporal.
     *
     * @return {@link SourceConfig} de tipo {@link SourceType#CSV} con codificación
     *         UTF-8, delimitador coma y cabecera habilitada
     */
    public static SourceConfig aCsvSourceConfig() {
        return new SourceConfig(SourceType.CSV, "/tmp/test.csv", "UTF-8", ',', true, Map.of());
    }

    /**
     * Crea una configuración de destino apuntando a la tabla de staging de ventas.
     *
     * @return {@link TargetConfig} de tipo {@link TargetType#DATABASE} con estrategia
     *         {@link LoadStrategy#UPSERT}, clave {@code transaction_id} y tamaño de
     *         lote de 1000 registros
     */
    public static TargetConfig aTargetConfig() {
        return new TargetConfig(TargetType.DATABASE, "public", "sales_staging",
                                "sales_transactions", LoadStrategy.UPSERT,
                                List.of("transaction_id"), 1000);
    }

    /**
     * Crea una configuración de validación con columnas requeridas, tipos de campo,
     * claves únicas y umbral de error del 5 %.
     *
     * @return {@link ValidationConfig} con {@code transaction_id} como clave única,
     *         tipos definidos para {@code amount} (DECIMAL) y {@code sale_date} (DATE),
     *         y validación de catálogo habilitada
     */
    public static ValidationConfig aValidationConfig() {
        return new ValidationConfig(
            List.of("transaction_id", "customer_id", "amount"),
            Map.of("amount", "DECIMAL", "sale_date", "DATE"),
            List.of("transaction_id"),
            ErrorThreshold.of(5.0),
            true
        );
    }

    /**
     * Crea una política de reintento con 3 intentos máximos y 5000 ms de espera.
     *
     * @return {@link RetryPolicy} configurada con 3 reintentos y 5 segundos de intervalo
     */
    public static RetryPolicy aRetryPolicy() {
        return RetryPolicy.of(3, 5000L);
    }

    /**
     * Crea un {@link Pipeline} completo y activo con configuraciones de prueba
     * predefinidas.
     *
     * <p>El pipeline representa un proceso de ventas diario con cron
     * {@code "0 2 * * *"} en UTC y agrupa todas las configuraciones creadas
     * por los demás métodos de esta fábrica.</p>
     *
     * @return instancia de {@link Pipeline} en estado {@link PipelineStatus#ACTIVE}
     *         lista para ser usada en pruebas
     */
    public static Pipeline aPipeline() {
        return new Pipeline(
            aPipelineId(),
            "sales-daily",
            "1.0.0",
            "Daily sales pipeline",
            PipelineStatus.ACTIVE,
            aCsvSourceConfig(),
            aTargetConfig(),
            aValidationConfig(),
            ScheduleConfig.of("0 2 * * *", "UTC"),
            aRetryPolicy(),
            Instant.now(),
            Instant.now()
        );
    }
}
