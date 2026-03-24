# Bitacora Fase 8

Fecha: 2026-03-23

## Objetivo

Exponer el motor ETL por REST, habilitar polling de ejecuciones y agregar monitoreo operativo basico con Spring Actuator.

## Resultado

Fase 8 quedo implementada.

## Entregables completados

### 1. REST API de pipelines

Se agregaron endpoints en:

- `src/main/java/com/elyares/etl/interfaces/rest/controller/PipelineController.java`

Endpoints:

- `GET /api/v1/pipelines`
- `GET /api/v1/pipelines/{pipelineRef}`
- `GET /api/v1/pipelines/{pipelineRef}/executions?limit=20`
- `POST /api/v1/pipelines/{pipelineRef}/execute`

Notas:

- `pipelineRef` acepta UUID o nombre de negocio, por ejemplo `sales-daily`.
- la ejecucion via REST ya no bloquea el request HTTP
- `POST /execute` devuelve `202 Accepted`

### 2. REST API de ejecuciones

Se agregaron endpoints en:

- `src/main/java/com/elyares/etl/interfaces/rest/controller/ExecutionController.java`

Endpoints:

- `GET /api/v1/executions/{executionId}`
- `GET /api/v1/executions/{executionId}/metrics`
- `GET /api/v1/executions/{executionId}/rejected?page=0&size=50`

### 3. Fachadas de aplicacion para REST

Se agregaron:

- `src/main/java/com/elyares/etl/application/facade/PipelineExecutionFacade.java`
- `src/main/java/com/elyares/etl/application/facade/ExecutionMonitoringFacade.java`

Responsabilidades:

- disparar ejecuciones asincronas para devolver `202`
- resolver `pipelineRef` por UUID o nombre
- armar vistas de monitoreo sin meter logica en controllers
- calcular metricas de ejecucion para el endpoint `/metrics`
- paginar rechazados para el endpoint `/rejected`

### 4. Ejecucion asincrona para HTTP

Se agrego:

- `src/main/java/com/elyares/etl/infrastructure/config/AsyncExecutionConfig.java`

Esto mueve la orquestacion ETL fuera del hilo HTTP y permite:

- responder rapido con `executionId`
- hacer polling despues con `GET /api/v1/executions/{executionId}`

### 5. Manejador global de errores

Se agrego:

- `src/main/java/com/elyares/etl/interfaces/rest/handler/GlobalExceptionHandler.java`

Mapeos implementados:

- `PipelineNotFoundException` -> `404`
- `ExecutionNotFoundException` -> `404`
- `ExecutionConflictException` -> `409`
- Bean Validation / request invalido -> `400`
- `EtlException` -> `500`
- excepcion no controlada -> `500`

### 6. Health indicator del motor ETL

Se agrego:

- `src/main/java/com/elyares/etl/infrastructure/monitoring/ETLEngineHealthIndicator.java`

Expose en `/actuator/health`:

- numero de ejecuciones activas
- numero de pipelines registrados
- ultima ejecucion exitosa o parcial por pipeline

Tambien se amplio:

- `src/main/resources/application.yml`

Ahora Actuator expone:

- `health`
- `info`
- `metrics`
- `loggers`

### 7. Rechazados con numero de fila real

Se agrego migracion:

- `src/main/resources/db/migration/V5__add_source_row_number_to_rejected_records.sql`

Y se ajusto persistencia:

- `src/main/java/com/elyares/etl/infrastructure/persistence/entity/EtlRejectedRecordEntity.java`
- `src/main/java/com/elyares/etl/infrastructure/persistence/adapter/RejectedRecordRepositoryAdapter.java`
- `src/main/java/com/elyares/etl/domain/model/validation/RejectedRecord.java`

Con esto el endpoint de rechazados ya puede devolver `rowNumber` util.

## Archivos principales de Fase 8

- `src/main/java/com/elyares/etl/application/dto/ExecutionAcceptedDto.java`
- `src/main/java/com/elyares/etl/application/facade/PipelineExecutionFacade.java`
- `src/main/java/com/elyares/etl/application/facade/ExecutionMonitoringFacade.java`
- `src/main/java/com/elyares/etl/interfaces/rest/request/ExecutePipelineRequest.java`
- `src/main/java/com/elyares/etl/interfaces/rest/controller/PipelineController.java`
- `src/main/java/com/elyares/etl/interfaces/rest/controller/ExecutionController.java`
- `src/main/java/com/elyares/etl/interfaces/rest/handler/GlobalExceptionHandler.java`
- `src/main/java/com/elyares/etl/infrastructure/monitoring/ETLEngineHealthIndicator.java`
- `src/main/java/com/elyares/etl/infrastructure/config/AsyncExecutionConfig.java`
- `src/main/resources/db/migration/V5__add_source_row_number_to_rejected_records.sql`

## Pruebas agregadas

- `src/test/java/com/elyares/etl/interfaces/rest/controller/PipelineControllerTest.java`
- `src/test/java/com/elyares/etl/interfaces/rest/controller/ExecutionControllerTest.java`
- `src/test/java/com/elyares/etl/unit/facade/PipelineExecutionFacadeTest.java`
- `src/test/java/com/elyares/etl/unit/facade/ExecutionMonitoringFacadeTest.java`

## Validacion ejecutada

### Pruebas nuevas de Fase 8

Comando:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q -Dtest=PipelineControllerTest,ExecutionControllerTest,PipelineExecutionFacadeTest,ExecutionMonitoringFacadeTest test
```

Resultado:

- en verde
- sin `failures`
- sin `errors`

### Suite unitaria completa

Comando:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado:

- suite unitaria completa en verde

## Estado funcional al cerrar Fase 8

Ya puedes:

- listar pipelines por REST
- consultar detalle de un pipeline por REST
- disparar `sales-daily` por REST
- recibir `executionId` con `202 Accepted`
- consultar estado completo de una ejecucion
- consultar metricas calculadas de una ejecucion
- consultar rechazados paginados
- consultar health del motor con Actuator

## Pendiente fuera de Fase 8

- Fase 9: `Inventory` y `Customer`
- extractores adicionales como `ExcelExtractor`
- mas pipelines reales
- dashboard visual de monitoreo
