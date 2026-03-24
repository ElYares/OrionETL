# Bitácora Fase 10

Fecha de cierre: `2026-03-24`

## Objetivo

Cerrar la Fase 10 del plan:

- hardening final de V1
- retries automáticos
- hooks de notificación operativa
- validación Docker
- `README.md` raíz y roadmap V2
- `DatabaseExtractor` bonus

## Entregables implementados

### Reintentos automáticos

Se agregó un runner central para encadenar intentos:

- `PipelineExecutionRunner`

Archivos:

- `src/main/java/com/elyares/etl/application/usecase/execution/PipelineExecutionRunner.java`
- `src/main/java/com/elyares/etl/application/usecase/execution/ExecutePipelineUseCase.java`
- `src/main/java/com/elyares/etl/application/usecase/execution/RetryExecutionUseCase.java`
- `src/main/java/com/elyares/etl/application/facade/PipelineExecutionFacade.java`

Qué hace:

- ejecuta la corrida inicial
- si queda `FAILED`, evalúa elegibilidad de retry
- espera `retryDelayMs`
- crea una nueva ejecución `RETRY`
- encadena `parentExecutionId`
- incrementa `retryCount` correctamente por intento
- devuelve la última ejecución terminal

### Notifications operativas

Se agregó puerto de salida para cierre operativo:

- `ExecutionNotificationHook`

Implementaciones:

- `LogNotificationService`
- `WebhookNotificationService` como stub V2

Archivos:

- `src/main/java/com/elyares/etl/domain/contract/ExecutionNotificationHook.java`
- `src/main/java/com/elyares/etl/infrastructure/notification/LogNotificationService.java`
- `src/main/java/com/elyares/etl/infrastructure/notification/WebhookNotificationService.java`
- `src/main/java/com/elyares/etl/application/orchestrator/ETLOrchestrator.java`

Qué hace:

- `FAILED` → log `ERROR` estructurado
- `PARTIAL` → log `WARN`
- `SUCCESS` → log `INFO`

### Wiring Spring

Se actualizó el wiring para usar el runner compartido y propagar hooks de notificación.

Archivo:

- `src/main/java/com/elyares/etl/infrastructure/config/CoreUseCaseConfig.java`

### DatabaseExtractor bonus

Se cerró también el extractor relacional bonus de la Fase 10.

Archivos:

- `src/main/java/com/elyares/etl/infrastructure/extractor/database/DatabaseExtractor.java`
- `src/test/java/com/elyares/etl/unit/extractor/DatabaseExtractorTest.java`
- `src/test/java/com/elyares/etl/integration/extractor/DatabaseExtractorIT.java`

Qué hace:

- abre conexión JDBC desde `SourceConfig`
- ejecuta query parametrizable con named parameters
- aplica `fetchSize`
- soporta credenciales directas o vía variables de entorno
- convierte cada fila en `RawRecord`

### Documentación operativa

Se agregó:

- `README.md` raíz
- roadmap separado de V2

Archivos:

- `README.md`
- `docs/architecture/v2-roadmap.md`

## Pruebas agregadas o extendidas

### Unit

- `PipelineExecutionRunnerTest`
- `RetryExecutionUseCaseTest`
- `RetryEligibilityRuleTest`
- ajustes a `ExecutePipelineUseCaseTest`
- ajustes a `PipelineExecutionFacadeTest`
- ajustes a `ETLOrchestratorTest`

### Integration

- `AutomaticRetryIT`
- `DatabaseExtractorIT`

## Validación ejecutada

### Unit tests enfocados en Fase 10

Resultado:

- en verde

### Integration test de retry automático

Resultado:

- se crean 2 ejecuciones
- la segunda queda con `triggerType=RETRY`
- `parentExecutionId` queda enlazado
- `retryCount=1`

### Integration test de `DatabaseExtractor`

Resultado:

- lectura real contra PostgreSQL en Testcontainers
- named parameters funcionando
- `fetchSize` aplicado sin error
- extracción en verde

### Suite completa `clean verify` en Docker Compose

Comando:

```bash
docker compose --profile integration-tests run --rm it-runner mvn -q clean verify
```

Resultado:

- `surefire_total=120`
- `failures=0`
- `errors=0`
- `skipped=0`
- `failsafe_total=17`
- `failures=0`
- `errors=0`
- `skipped=1`

### Imagen Docker

Comando:

```bash
docker build -t orionetl:v1 .
```

Resultado:

- build exitoso

### Stack principal + health

Comandos:

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/pipelines
```

Resultado:

- `{"status":"UP"}`
- API responde con los 3 pipelines activos

## Estado funcional al cerrar Fase 10

V1 queda cerrado con:

- 3 pipelines reales funcionando
- extracción `CSV` / `API` / `Excel`
- staging/final load con auditoría y rechazados
- REST API y health endpoint
- retries automáticos ante errores técnicos reintentables
- notifications operativas por log
- extractor relacional `DATABASE`
- imagen Docker construible y stack local levantable por Compose

## Pendiente siguiente

El siguiente trabajo ya es V2:

- scheduler
- migración a Spring Batch
- dashboard operativo
- webhook real
- dead-letter queue
