# Bitácora Fase 6

Fecha de actualización: `2026-03-23`

## Objetivo

Implementar la infraestructura real de carga:

- staging por chunks
- validación de staging
- promoción a final
- rollback por ejecución
- trazabilidad por registro cargado

## Qué se implementó

### 1) Modelo de carga extendido

Se extendió `TargetConfig` para soportar semántica real de Fase 6:

- `failFastOnChunkError`
- `rollbackStrategy`
- `closedRecordGuardEnabled`
- `closedFlagColumn`
- `closedFlagValue`

También se agregó:

- `src/main/java/com/elyares/etl/domain/enums/RollbackStrategy.java`
- `src/main/java/com/elyares/etl/domain/model/target/StagingValidationResult.java`

### 2) Trazabilidad en `ProcessedRecord`

`ProcessedRecord` ahora conserva `sourceReference` para poder inyectar en carga:

- `etl_source_file`

Archivo:

- `src/main/java/com/elyares/etl/domain/model/target/ProcessedRecord.java`

Y `CommonTransformer` ya la propaga desde `RawRecord`.

### 3) Persistencia de rechazados en el punto correcto

Se movió la persistencia de `RejectedRecord` al inicio de `LOAD`, antes de tocar staging, tal como lo pide la regla de loading.

Archivos:

- `src/main/java/com/elyares/etl/application/orchestrator/ETLOrchestrator.java`
- `src/main/java/com/elyares/etl/application/orchestrator/OrchestrationContext.java`

### 4) Loaders reales JDBC

Se agregaron:

- `src/main/java/com/elyares/etl/infrastructure/loader/database/DatabaseDataLoader.java`
- `src/main/java/com/elyares/etl/infrastructure/loader/database/StagingLoader.java`
- `src/main/java/com/elyares/etl/infrastructure/loader/database/StagingValidator.java`
- `src/main/java/com/elyares/etl/infrastructure/loader/database/FinalLoader.java`
- `src/main/java/com/elyares/etl/infrastructure/loader/database/DatabaseLoadSupport.java`
- `src/main/java/com/elyares/etl/infrastructure/loader/database/StagingLoadResult.java`

Cobertura funcional:

- `TRUNCATE` de staging al inicio
- carga por `chunkSize`
- una transacción por chunk
- registro de errores de chunk en el agregado `PipelineExecution`
- `failFastOnChunkError=true|false`
- validación de row count en staging
- validación de columnas `NOT NULL` en staging
- validación de unicidad de business key para `UPSERT`
- promoción `INSERT`
- promoción `UPSERT`
- promoción `REPLACE`
- guard de registros cerrados en `UPSERT`
- rollback `DELETE_BY_EXECUTION`

### 5) Migración SQL de soporte

Se agregó:

- `src/main/resources/db/migration/V3__create_sales_load_tables.sql`

Tablas:

- `sales_transactions`
- `sales_transactions_staging`

Incluyen columnas de trazabilidad:

- `etl_execution_id`
- `etl_pipeline_id`
- `etl_source_file`
- `etl_load_timestamp`
- `etl_pipeline_version`

## Pruebas agregadas

Se agregó:

- `src/test/java/com/elyares/etl/integration/loader/DatabaseDataLoaderIT.java`

Escenarios cubiertos:

1. carga completa `staging -> validate -> upsert`
2. chunk 1 commit, chunk 2 falla con `failFastOnChunkError=false`
3. duplicados en business key dentro de staging bloquean promoción
4. `UPSERT` respeta closed-record guard y `rollbackByExecution`

## Evidencia de ejecución

### Unit tests

Comando ejecutado:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado:

- `tests=100`
- `failures=0`
- `errors=0`
- `skipped=0`

### IT de loaders en contenedores

Comando ejecutado:

```bash
docker compose --profile integration-tests up -d docker-it
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=DatabaseDataLoaderIT failsafe:integration-test failsafe:verify
```

Resultado:

- `completed=4`
- `failures=0`
- `errors=0`
- `skipped=0`

Reportes:

- `target/failsafe-reports/failsafe-summary.xml`
- `target/failsafe-reports/TEST-com.elyares.etl.integration.loader.DatabaseDataLoaderIT.xml`

## Qué ya puedes hacer al terminar Fase 6

- extraer desde CSV/API
- validar y transformar en memoria
- persistir rechazados antes de la carga
- cargar registros válidos a staging
- validar staging antes de promover
- promover a final con `INSERT`, `UPSERT` o `REPLACE`
- proteger registros cerrados en `UPSERT`
- revertir registros de una ejecución con `DELETE_BY_EXECUTION`

## Qué falta todavía

Con Fase 6 ya existe el bloque real de carga, pero todavía falta:

- pipeline de negocio completo de punta a punta
- fixtures/configuración pipeline-specific de ventas
- validación E2E del flujo completo con auditoría final real

## Cierre de fase

Estado: `implementada y validada con unit tests + IT de loaders`
