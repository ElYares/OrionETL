# Bitácora Fase 7

Fecha: `2026-03-23`

## Objetivo

Cerrar el primer pipeline de negocio real de OrionETL:

- `sales-daily`
- entrada CSV
- transformaciones específicas de Sales
- validación de negocio
- carga a staging/final
- auditoría y rechazados

## Entregables completados

- [x] `SalesTransformer` implementado
- [x] validación de negocio ejecutándose sobre datos ya transformados
- [x] soporte de leniencia para `sale_date` futura en canal `PARTNER`
- [x] `sales.yml` agregado en `src/main/resources/pipelines/`
- [x] `SalesPipelineConfig` para cargar YAML y registrar pipeline al arrancar
- [x] wiring Spring para casos de uso y orquestación end-to-end
- [x] migración de schema para completar tablas `sales_transactions` y `sales_transactions_staging`
- [x] fixture E2E de Sales con `105` filas (`100` válidas + `5` inválidas)
- [x] unit test de `SalesTransformer`
- [x] E2E `SalesPipelineE2EIT`

## Archivos principales

- `src/main/java/com/elyares/etl/infrastructure/transformer/sales/SalesTransformer.java`
- `src/main/java/com/elyares/etl/pipelines/sales/SalesPipelineConfig.java`
- `src/main/resources/pipelines/sales.yml`
- `src/main/resources/db/migration/V4__expand_sales_tables_for_phase7.sql`
- `src/test/java/com/elyares/etl/unit/transformer/sales/SalesTransformerTest.java`
- `src/test/java/com/elyares/etl/e2e/SalesPipelineE2EIT.java`
- `src/test/resources/fixtures/pipelines/sales_e2e.csv`

## Ajustes importantes hechos en el flujo

- `ETLOrchestrator` ahora valida negocio sobre registros transformados y filtra correctamente los `ProcessedRecord` antes de `LOAD`.
- `PipelineOrchestrationService` ya no bloquea ejecuciones manuales por ventana horaria.
- `PipelineOrchestrationService` calcula `PARTIAL` usando el total real de rechazados del contexto.
- `DateUtils` ahora soporta fecha sin hora para normalizar `sale_date`.
- `DatabaseLoadSupport` normaliza tipos JDBC (`Instant`, `LocalDate`, `LocalDateTime`) antes del `batchUpdate`.
- `PipelineRepositoryAdapter` ya persiste y recupera `rangeRules` y `futureDateRules`.

## Validación ejecutada

### Unit suite

Comando:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado:

- `surefire_total=103`
- `failures=0`
- `errors=0`
- `skipped=0`

### E2E Sales

Comandos:

```bash
docker compose --profile integration-tests up -d docker-it
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=SalesPipelineE2EIT \
  failsafe:integration-test failsafe:verify
```

Resultado:

- `completed=1`
- `failures=0`
- `errors=0`
- `skipped=0`

## Estado funcional después de Fase 7

Ya puedes:

- registrar automáticamente el pipeline `sales-daily`
- ejecutar el pipeline de Sales de punta a punta desde CSV
- aplicar transformación específica de negocio
- rechazar registros inválidos y persistirlos
- cargar válidos a `sales_transactions`
- dejar auditoría completa de la ejecución

Todavía falta:

- REST y monitoreo (Fase 8)
- inventory/customer pipelines (Fase 9)
- hardening final (Fase 10)
