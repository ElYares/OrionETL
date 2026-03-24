# Bitácora Fase 9

Fecha de cierre: `2026-03-24`

## Objetivo

Cerrar la Fase 9 del plan:

- segundo pipeline real: `inventory-sync`
- tercer pipeline real: `customer-sync`
- soporte de extracción Excel con `ExcelExtractor`

## Entregables implementados

### Extracción

- `ExcelExtractor`
  - Lee `.xlsx`/`.xls` con Apache POI
  - Resuelve hoja por `sheetName` o `sheetIndex`
  - Soporta header row y `dataStartRow`
  - Convierte celdas de fecha a `LocalDate` o `Instant`
  - Mantiene `rowNumber` real del Excel

Archivo:

- `src/main/java/com/elyares/etl/infrastructure/extractor/excel/ExcelExtractor.java`

### Transformación

- `InventoryTransformer`
  - normaliza SKU
  - normaliza `warehouse_id`
  - consolida duplicados por `(sku, warehouse_id)`
  - suma cantidades
  - conserva costo/fecha más reciente

- `CustomerTransformer`
  - normaliza nombres
  - normaliza email
  - normaliza phone a formato tipo E.164
  - normaliza país y status
  - remapea `customer_id -> crm_customer_id`

Archivos:

- `src/main/java/com/elyares/etl/infrastructure/transformer/inventory/InventoryTransformer.java`
- `src/main/java/com/elyares/etl/infrastructure/transformer/customer/CustomerTransformer.java`

### Pipelines

- `inventory-sync`
- `customer-sync`

Wiring:

- `src/main/java/com/elyares/etl/pipelines/inventory/InventoryPipelineConfig.java`
- `src/main/java/com/elyares/etl/pipelines/customer/CustomerPipelineConfig.java`

YAML:

- `src/main/resources/pipelines/inventory.yml`
- `src/main/resources/pipelines/customer.yml`

### Base de datos

Migraciones agregadas:

- `src/main/resources/db/migration/V10__create_inventory_tables.sql`
- `src/main/resources/db/migration/V11__create_customer_tables.sql`

Tablas agregadas:

- `inventory_levels`
- `inventory_levels_staging`
- `customers`
- `customers_staging`

## Ajustes realizados durante el cierre

- Se corrigió el mapeo de cliente para persistir `crm_customer_id` a partir de `customer_id`.
- Se corrigió el fixture E2E de inventario para escribir fechas Excel con estilo real de fecha, alineado con el comportamiento esperado del extractor.

## Pruebas agregadas

### Unit

- `ExcelExtractorTest`
- `InventoryTransformerTest`
- `CustomerTransformerTest`

### End-to-end

- `SalesPipelineE2EIT`
- `InventoryPipelineE2EIT`
- `CustomerPipelineE2EIT`

## Validación ejecutada

### Suite unitaria completa en Docker

Comando:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado:

- `tests=115`
- `failures=0`
- `errors=0`
- `skipped=0`

### E2E de los tres pipelines en Docker Compose

Comando:

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=SalesPipelineE2EIT,InventoryPipelineE2EIT,CustomerPipelineE2EIT \
  failsafe:integration-test failsafe:verify
```

Resultado:

- `completed=3`
- `failures=0`
- `errors=0`
- `skipped=0`

## Estado funcional al cerrar Fase 9

Ya puedes:

- ejecutar `sales-daily` end-to-end
- ejecutar `inventory-sync` end-to-end
- ejecutar `customer-sync` end-to-end
- extraer desde CSV, API y Excel
- transformar y validar tres pipelines reales
- cargar a staging/final con auditoría y rechazados
- disparar y monitorear ejecuciones por REST

## Pendiente siguiente

La siguiente fase natural es la Fase 10:

- scheduler y reintentos operativos
- alerting / observabilidad adicional
- cierre operativo del motor para ejecución continua
