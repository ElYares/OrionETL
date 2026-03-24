# OrionETL — Comandos Operativos

Comandos rápidos para trabajar el proyecto en desarrollo local y pruebas.

Cada bloque explica:

- para qué sirve
- cuándo conviene usarlo
- qué deberías ver como resultado

## 1) Levantar stack principal (app + db)

Sirve para construir la imagen de la aplicación y levantar los servicios principales del proyecto:

- `db`: PostgreSQL
- `app`: OrionETL

Úsalo cuando quieras arrancar el entorno normal de trabajo del proyecto.

```bash
docker compose up -d --build
```

Ver estado:

Sirve para confirmar que los contenedores realmente quedaron arriba.

```bash
docker compose ps
```

Logs de app:

Sirve para ver arranque, errores de Spring Boot, migraciones Flyway y cualquier fallo de inicialización.

```bash
docker compose logs -f app
```

## 2) Bajar stack principal

Sirve para apagar el entorno principal (`app` + `db`).

Úsalo cuando ya terminaste de trabajar o quieras reiniciar limpio el stack.

```bash
docker compose down
```

## 3) Ejecutar tests unitarios en contenedor Maven

Sirve para correr la suite de pruebas unitarias sin depender de Maven instalado en el host.

Úsalo cuando cambies lógica de dominio, application o infraestructura y quieras validar regresiones rápidas.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado esperado:

- se generan reportes en `target/surefire-reports/`
- no debe haber `failures`
- no debe haber `errors`

## 4) Ejecutar integration tests (IT) 100% en Docker

Perfil de Compose: `integration-tests` (usa `docker:dind` + `it-runner`).

Esto sirve para ejecutar integration tests dentro de contenedores, incluyendo los que necesitan Docker/Testcontainers.

Úsalo cuando quieras validar persistencia o infraestructura real sin usar el host directamente.

```bash
docker compose --profile integration-tests up -d docker-it
docker compose --profile integration-tests run --rm it-runner
```

Resumen de resultados IT:

Sirve para revisar el total consolidado de integration tests ejecutadas.

```bash
cat target/failsafe-reports/failsafe-summary.xml
```

Formato esperado para éxito total:
- `completed=6`
- `failures=0`
- `errors=0`
- `skipped=0`

## 5) Apagar perfil de integration-tests

Sirve para bajar los contenedores del perfil `integration-tests`.

```bash
docker compose --profile integration-tests down
```

Nota: este `down` detiene también los servicios del proyecto en el mismo `docker-compose.yml`.

## 6) Si usas Maven local (opcional)

Suite completa (unit + IT):

Sirve para correr todo con Maven instalado localmente.

```bash
mvn verify
```

Solo IT:

Sirve para correr únicamente integration tests desde Maven local.

Nota: si agregaste tests nuevos y corres solo Failsafe directo, puede hacer falta `test-compile` antes.

```bash
mvn failsafe:integration-test failsafe:verify
```

## 7) Reportes de test

Estos directorios sirven para inspeccionar resultados detallados después de correr pruebas.

Unit tests (Surefire):
- `target/surefire-reports/`

Integration tests (Failsafe):
- `target/failsafe-reports/`

Cobertura (JaCoCo):
- `target/site/jacoco/index.html`

## 8) IT de CSV con datasets Kaggle (desde contenedor)

Requiere que exista la ruta local:
- `/home/elyarestark/develop/datasets/archive`

Sirve para validar los extractores contra datos reales externos montados dentro del contenedor.

Úsalo cuando quieras comprobar que Phase 4 funciona con tus CSV reales de Kaggle y no solo con fixtures del repo.

Comando:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -e ORION_DATASETS_ARCHIVE=/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

Reporte específico:

Sirve para revisar específicamente el resultado de la prueba de integración de CSV.

```bash
cat target/failsafe-reports/TEST-com.elyares.etl.integration.extractor.CsvExtractorIT.xml
```

## 9) IT de extractores API + CSV

Sirve para validar únicamente el bloque de extractores de Fase 4:

- `CsvExtractorIT`
- `ApiExtractorIT`

Úsalo cuando estés trabajando solo en extracción y no quieras ejecutar toda la suite de integración.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

Resultado esperado:

- `CsvExtractorIT` en verde
- `ApiExtractorIT` en verde
- sin `failures`
- sin `errors`

## 10) Preview de CSV por consola

Sirve para ver en consola cómo está saliendo la extracción real de un CSV.

Este comando no ejecuta el ETL completo. Solo arranca Spring Boot en modo no web, corre el `CsvPreviewRunner`, imprime una muestra y termina.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q spring-boot:run \
    -Dspring-boot.run.arguments="--spring.main.web-application-type=none,--orionetl.csv-preview.enabled=true,--orionetl.csv-preview.path=/datasets/archive/fact_table.csv,--orionetl.csv-preview.limit=10,--orionetl.csv-preview.null-values=,NULL,N/A,-,--orionetl.csv-preview.header-mapping.payment_key=payment_id"
```

Imprime:
- `csv_preview_total_read=...`
- `row=N data={...}`

Qué hace cada argumento importante:

- `--spring.main.web-application-type=none`
  Evita levantar el servidor web completo. Solo corre el contexto necesario para el runner.
- `--orionetl.csv-preview.enabled=true`
  Activa el `CsvPreviewRunner`.
- `--orionetl.csv-preview.path=/datasets/archive/fact_table.csv`
  Indica qué archivo CSV leer.
- `--orionetl.csv-preview.limit=10`
  Define cuántas filas imprimir.
- `--orionetl.csv-preview.null-values=,NULL,N/A,-`
  Define qué valores deben tratarse como `null`.
- `--orionetl.csv-preview.header-mapping.payment_key=payment_id`
  Renombra el header `payment_key` a `payment_id` en la salida.

Cuándo usarlo:

- cuando quieras inspeccionar datos extraídos
- cuando estés depurando headers
- cuando quieras validar null normalization
- cuando quieras confirmar que el archivo correcto se está leyendo

## 11) Tests unitarios enfocados en Fase 5

Sirve para validar únicamente el bloque nuevo de transformación y validación:

- `CommonTransformerTest`
- `SchemaValidatorTest`
- `BusinessValidatorTest`
- `QualityValidatorTest`

Úsalo cuando estés trabajando solo en Fase 5 y no quieras correr toda la suite.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=CommonTransformerTest,SchemaValidatorTest,BusinessValidatorTest,QualityValidatorTest test
```

Resultado esperado:

- los 4 tests en verde
- sin `failures`
- sin `errors`

## 12) Suite unitaria completa después de Fase 5

Sirve para validar toda la lógica unitaria acumulada hasta Fase 7, incluyendo:

- extractores
- transformadores base
- `SalesTransformer`
- validadores
- orquestación

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado esperado:

- reportes en `target/surefire-reports/`
- `surefire_total=103`
- `failures=0`
- `errors=0`

## 13) Unit test de `SalesTransformer`

Sirve para validar únicamente la lógica específica del pipeline de Sales:

- defaulting de `quantity` y `discount_rate`
- channel mapping
- cálculo de `subtotal`
- cálculo de `tax_amount`
- cálculo de `total_amount`
- rechazo por canal no mapeado

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=SalesTransformerTest test
```

Resultado esperado:

- `SalesTransformerTest` en verde
- sin `failures`
- sin `errors`

## 14) E2E del pipeline de Sales

Sirve para validar el primer pipeline real de punta a punta:

- registro del pipeline desde `sales.yml`
- extracción CSV
- validación de esquema
- transformación
- validación de negocio
- carga a `staging`
- promoción a `sales_transactions`

## 32) Tests del `DatabaseExtractor`

Sirve para validar el extractor JDBC agregado al cierre de Fase 10:

- `DatabaseExtractorTest`
- `DatabaseExtractorIT`

Úsalo cuando trabajes en extracción relacional y no quieras correr toda la suite.

### Unit

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=DatabaseExtractorTest test
```

Qué valida:

- error claro cuando falta `query`
- `supports(SourceType.DATABASE)`

### Integration

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=DatabaseExtractorIT failsafe:integration-test failsafe:verify
```

Qué valida:

- conexión real a PostgreSQL con JDBC
- named parameters
- lectura de filas a `RawRecord`
- `fetchSize` aplicado sin romper la consulta

### Configuración mínima esperada

```text
type=DATABASE
location=jdbc:postgresql://host:5432/db
connectionProperties.username=reader
connectionProperties.password=secret
connectionProperties.query=SELECT * FROM table WHERE status = :status
connectionProperties.queryParam.status=OPEN
connectionProperties.fetchSize=500
```

## 15) Probar endpoints REST de Fase 8

Sirve para validar el contrato HTTP nuevo sin correr toda la suite unitaria.

- `PipelineControllerTest`
- `ExecutionControllerTest`
- `PipelineExecutionFacadeTest`
- `ExecutionMonitoringFacadeTest`

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=PipelineControllerTest,ExecutionControllerTest,PipelineExecutionFacadeTest,ExecutionMonitoringFacadeTest test
```

Resultado esperado:

- los 4 tests en verde
- sin `failures`
- sin `errors`

## 16) Levantar stack para usar la API REST

Sirve para trabajar ya con la aplicacion real y no solo con tests.

```bash
docker compose up -d
```

Luego valida:

```bash
curl http://localhost:8080/actuator/health
```

Debes ver:

- `status = UP`
- componente `etlEngine`
- componente `db`

## 17) Listar pipelines activos por REST

Sirve para confirmar que la app ya registra pipelines y que el controlador responde.

```bash
curl http://localhost:8080/api/v1/pipelines
```

Debes ver:

- `success=true`
- `data` con al menos `sales-daily`

## 18) Ejecutar `sales-daily` por REST

Sirve para disparar el pipeline y obtener un `executionId`.

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/sales-daily/execute \
  -H "Content-Type: application/json" \
  -d '{
    "triggeredBy": "api:elya",
    "parameters": {
      "batch_date": "2026-03-23"
    }
  }'
```

Debes ver:

- HTTP `202 Accepted`
- `success=true`
- `data.executionId`
- `data.status=RUNNING`

## 19) Consultar estado completo de una ejecucion

Sirve para hacer polling y ver pasos, conteos y estado final.

```bash
curl http://localhost:8080/api/v1/executions/<executionId>
```

Te devuelve:

- estado global
- `totalRead`
- `totalTransformed`
- `totalRejected`
- `totalLoaded`
- arreglo `steps`

## 20) Consultar metricas de una ejecucion

Sirve para ver tiempos y conteos calculados para esa corrida.

```bash
curl http://localhost:8080/api/v1/executions/<executionId>/metrics
```

Metricas disponibles hoy:

- `records.read`
- `records.transformed`
- `records.rejected`
- `records.loaded`
- `error.rate.percent`
- `duration.ms`
- `extract.duration.ms`
- `transform.duration.ms`
- `load.duration.ms`

## 21) Consultar rechazados paginados

Sirve para inspeccionar registros que no pasaron validacion o transformacion.

```bash
curl "http://localhost:8080/api/v1/executions/<executionId>/rejected?page=0&size=20"
```

Te devuelve:

- `rowNumber`
- `rawData`
- `rejectionReason`
- `rejectedAt`

## 22) Suite unitaria completa despues de Fase 8

Sirve para validar que Fase 8 no rompio fases previas.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado esperado:

- suite unitaria completa en verde
- reportes en `target/surefire-reports/`
- persistencia de rechazados
- auditoría final

Usa el perfil `integration-tests` porque el test corre con Testcontainers dentro de Docker.

Levantar soporte Docker-in-Docker:

```bash
docker compose --profile integration-tests up -d docker-it
```

Ejecutar el E2E:

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=SalesPipelineE2EIT \
  failsafe:integration-test failsafe:verify
```

Resultado esperado:

- `SalesPipelineE2EIT` en verde
- `completed=1`
- `failures=0`
- `errors=0`

Qué valida:

- `status = PARTIAL`
- `total_read = 105`
- `total_loaded = 100`
- `total_rejected = 5`
- `sales_transactions = 100`
- `etl_rejected_records = 5`
- `etl_audit_records = 1`

## 15) Bajar el perfil de integración después de Fase 7

```bash
docker compose --profile integration-tests down
```

Úsalo al terminar pruebas E2E para apagar `docker-it` y los contenedores auxiliares del perfil.

Sirve para validar que el refactor de Fase 5 no rompió el resto del proyecto.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado esperado actual:

- `tests=100`
- `failures=0`
- `errors=0`
- reportes en `target/surefire-reports/`

## 13) IT de loaders de Fase 6 dentro del perfil Docker del proyecto

Sirve para validar el bloque real de carga:

- `StagingLoader`
- `StagingValidator`
- `FinalLoader`
- `DatabaseDataLoader`

Úsalo cuando quieras probar Fase 6 sin depender del host y sin pelearte con Testcontainers dentro de un `docker run` suelto.

Primero levanta el servicio Docker-in-Docker:

```bash
docker compose --profile integration-tests up -d docker-it
```

Luego corre solo la IT de loaders:

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=DatabaseDataLoaderIT failsafe:integration-test failsafe:verify
```

Resultado esperado:

- `completed=4`
- `failures=0`
- `errors=0`
- `skipped=0`

Qué valida:

- carga por chunks a staging
- validación de staging
- promoción `UPSERT`
- guard de registros cerrados
- rollback por `etl_execution_id`

## 14) Reporte específico de la IT de loaders

Sirve para revisar el detalle exacto de Fase 6 después de correr la prueba.

```bash
cat target/failsafe-reports/TEST-com.elyares.etl.integration.loader.DatabaseDataLoaderIT.xml
cat target/failsafe-reports/failsafe-summary.xml
```

Qué deberías ver:

- `tests="4"`
- `failures="0"`
- `errors="0"`

## 15) Bajar el perfil Docker de IT al terminar

Sirve para apagar `docker-it` y los contenedores efímeros del perfil de integración.

```bash
docker compose --profile integration-tests down
```

Nota:

- para las IT que usan Testcontainers en este proyecto, la ruta recomendada es el perfil `integration-tests`
- un `docker run ... maven ...` directo puede no tener una negociación válida de API Docker para Testcontainers

## 23) Unit tests enfocados en Fase 9

Sirve para validar solo el bloque nuevo de Excel + Inventory + Customer sin correr toda la suite.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=ExcelExtractorTest,InventoryTransformerTest,CustomerTransformerTest test
```

Resultado esperado:

- `ExcelExtractorTest` en verde
- `InventoryTransformerTest` en verde
- `CustomerTransformerTest` en verde
- sin `failures`
- sin `errors`

## 24) E2E de Inventory y Customer en Docker Compose

Sirve para validar los dos pipelines agregados en Fase 9:

- `InventoryPipelineE2EIT`
- `CustomerPipelineE2EIT`

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=InventoryPipelineE2EIT,CustomerPipelineE2EIT \
  failsafe:integration-test failsafe:verify
```

Resultado esperado:

- `completed=2`
- `failures=0`
- `errors=0`
- `skipped=0`

Qué valida:

- lectura real de Excel para `inventory-sync`
- consolidación por `(sku, warehouse_id)`
- lectura paginada de API para `customer-sync`
- normalización de phone y nombres
- guard de registros `CLOSED`

## 25) E2E de los tres pipelines reales

Sirve para validar el estado funcional completo de V1 con los tres pipelines de negocio:

- `SalesPipelineE2EIT`
- `InventoryPipelineE2EIT`
- `CustomerPipelineE2EIT`

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=SalesPipelineE2EIT,InventoryPipelineE2EIT,CustomerPipelineE2EIT \
  failsafe:integration-test failsafe:verify
```

Resultado esperado actual:

- `completed=3`
- `failures=0`
- `errors=0`
- `skipped=0`

## 26) Reportes de Fase 9

Sirve para revisar el resultado puntual de los E2E y la suite unitaria después de cerrar Fase 9.

```bash
cat target/failsafe-reports/failsafe-summary.xml
cat target/failsafe-reports/TEST-com.elyares.etl.e2e.InventoryPipelineE2EIT.xml
cat target/failsafe-reports/TEST-com.elyares.etl.e2e.CustomerPipelineE2EIT.xml
```

Unit tests:

```bash
ls target/surefire-reports
```

## 27) Unit tests enfocados en retry y hardening de Fase 10

Sirve para validar rápido el bloque nuevo de reintentos automáticos, runner y notificaciones.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=RetryEligibilityRuleTest,RetryExecutionUseCaseTest,ExecutePipelineUseCaseTest,PipelineExecutionRunnerTest,PipelineExecutionFacadeTest,ETLOrchestratorTest test
```

Resultado esperado:

- todo en verde
- sin `failures`
- sin `errors`

## 28) IT de retry automático

Sirve para validar que una ejecución fallida sí genera una segunda ejecución con `triggerType=RETRY`.

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false \
  -Dit.test=AutomaticRetryIT \
  failsafe:integration-test failsafe:verify
```

Qué valida:

- primer intento `FAILED`
- segundo intento `RETRY`
- `parentExecutionId` enlazado
- `retryCount` incrementado

## 29) Suite completa de V1 en Compose

Sirve para validar todo el proyecto con unit + integration + e2e usando el runner de Compose.

```bash
docker compose --profile integration-tests run --rm it-runner \
  mvn -q clean verify
```

Resultado esperado actual:

- `surefire_total=120`
- `failures=0`
- `errors=0`
- `failsafe_total=17`
- `failures=0`
- `errors=0`

## 30) Build de imagen final

Sirve para validar que el proyecto sí genera la imagen `orionetl:v1`.

```bash
docker build -t orionetl:v1 .
```

Resultado esperado:

- imagen creada sin errores

## 31) Health real del stack principal

Sirve para comprobar que `docker compose up -d --build` dejó la app realmente disponible.

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/pipelines
```

Resultado esperado:

- `{"status":"UP"}`
- la API lista `sales-daily`, `inventory-sync` y `customer-sync`
