# Bitácora — Fase 2: Domain & Application Core

**Objetivo:** Implementar toda la lógica de negocio del motor ETL. Al terminar, el sistema sabrá qué hacer y en qué orden aunque todavía no tenga extractores ni loaders reales.

**Estado actual:** ✅ COMPLETADA

**Fecha inicio:** 2026-03-22

**Regla de oro:** cero `@Autowired` y cero imports de Spring en `domain/`.

---

## Contexto del proyecto

- **Proyecto:** OrionETL — motor ETL enterprise en Java 21 + Spring Boot 3.x
- **Repositorio:** `/home/elyarestark/develop/OrionETL`
- **Package base:** `com.elyares.etl`
- **Fase anterior:** Fase 1 COMPLETADA — ver `docs/bitacora-fase1.md`
- **Docs de referencia:** `docs/action-plan.md` (Phase 2, líneas 181-283)

---

## Checklist de entregables

### Bloque 1 — DTOs y Mappers
- [x] `application/dto/ExecutionRequestDto.java`
- [x] `application/dto/PipelineDto.java`
- [x] `application/dto/PipelineExecutionDto.java`
- [x] `application/dto/ExecutionStepDto.java`
- [x] `application/dto/ExecutionStatusDto.java`
- [x] `application/dto/RejectedRecordDto.java`
- [x] `application/dto/AuditRecordDto.java`
- [x] `application/dto/ExecutionMetricDto.java`
- [x] `application/mapper/PipelineMapper.java`
- [x] `application/mapper/ExecutionMapper.java`

### Bloque 2 — Domain Rules
- [x] `domain/rules/NoDuplicateExecutionRule.java`
- [x] `domain/rules/RetryEligibilityRule.java`
- [x] `domain/rules/ErrorThresholdRule.java`
- [x] `domain/rules/AllowedExecutionWindowRule.java`
- [x] `domain/rules/CriticalErrorBlocksSuccessRule.java`

### Bloque 3 — Domain Services
- [x] `domain/service/ExecutionLifecycleService.java`
- [x] `domain/service/PipelineOrchestrationService.java`
- [x] `domain/service/DataQualityService.java`

### Bloque 4 — Use Cases
- [x] `application/usecase/pipeline/GetPipelineUseCase.java`
- [x] `application/usecase/pipeline/ListPipelinesUseCase.java`
- [x] `application/usecase/pipeline/ResolvePipelineConfigUseCase.java`
- [x] `application/usecase/execution/ExecutePipelineUseCase.java`
- [x] `application/usecase/execution/GetExecutionStatusUseCase.java`
- [x] `application/usecase/execution/ListExecutionsUseCase.java`
- [x] `application/usecase/execution/RetryExecutionUseCase.java`
- [x] `application/usecase/extraction/ExtractDataUseCase.java`
- [x] `application/usecase/validation/ValidateInputDataUseCase.java`
- [x] `application/usecase/validation/ValidateBusinessDataUseCase.java`
- [x] `application/usecase/transformation/TransformDataUseCase.java`
- [x] `application/usecase/loading/LoadProcessedDataUseCase.java`
- [x] `application/usecase/loading/RegisterAuditUseCase.java`
- [x] `application/usecase/loading/PersistRejectedRecordsUseCase.java`

### Bloque 5 — ETLOrchestrator
- [x] `application/orchestrator/ETLOrchestrator.java`

### Bloque 6 — Tests
- [x] `unit/rules/NoDuplicateExecutionRuleTest.java`
- [x] `unit/rules/ErrorThresholdRuleTest.java`
- [x] `unit/rules/RetryEligibilityRuleTest.java`
- [x] `unit/rules/AllowedExecutionWindowRuleTest.java`
- [x] `unit/rules/CriticalErrorBlocksSuccessRuleTest.java`
- [x] `unit/service/ExecutionLifecycleServiceTest.java`
- [x] `unit/service/DataQualityServiceTest.java`
- [x] `unit/service/PipelineOrchestrationServiceTest.java`
- [x] `unit/orchestrator/ETLOrchestratorTest.java`
- [x] Tests por cada use case

---

## Progreso detallado

| Bloque | Estado | Archivos |
|---|---|---|
| Bloque 1 — DTOs y Mappers | ✅ COMPLETADO | 8 DTOs + 2 Mappers |
| Bloque 2 — Domain Rules | ✅ COMPLETADO | 5 reglas + ventana de ejecución implementada |
| Bloque 3 — Domain Services | ✅ COMPLETADO | 3 servicios + `closeExecution(...)` |
| Bloque 4 — Use Cases | ✅ COMPLETADO | 14 use cases |
| Bloque 5 — ETLOrchestrator | ✅ COMPLETADO | `ETLOrchestrator` + `OrchestrationContext` |
| Bloque 6 — Tests | ✅ COMPLETADO | Reglas/servicios/orquestador + tests por todos los use cases |

---

## Criterios de aceptación

- [x] `mvn test` pasa con 80%+ cobertura en alcance Fase 2 (`domain.rules`, `domain.service`, `application.usecase`, `application.orchestrator`)
- [x] `ETLOrchestratorTest` verifica: AUDIT corre aunque falle el paso 3
- [x] `NoDuplicateExecutionRuleTest` verifica: excepción cuando hay ejecución activa
- [x] `ErrorThresholdRuleTest` verifica: aborta en threshold+0.01%, permite en threshold exacto
- [x] Ninguna clase en `domain/` tiene `@Autowired` ni imports de `org.springframework.*`

---

## Avance 2026-03-22

### Cambios funcionales aplicados

1. **Reglas y servicio de dominio**
- `AllowedExecutionWindowRule` dejó de ser placeholder y ahora valida ventanas por zona horaria.
- `ScheduleConfig` se extendió con `allowedWindows`.
- `PipelineOrchestrationService` ahora evalúa `RetryEligibilityRule` para triggers `RETRY`.
- `ExecutionLifecycleService` incorpora `closeExecution(...)`.

2. **Capa de aplicación**
- Se implementaron todos los use cases pendientes de pipeline, ejecución, extracción, validación, transformación y loading.
- Se implementó `ETLOrchestrator` con flujo de 8 pasos, abortos por umbral y `AUDIT` en `finally`.

3. **Tests**
- Se agregaron tests de reglas, servicios y orquestador.
- Se agregó test base para `ExecutePipelineUseCase`.
- Se ejecutó `mvn test` dentro de Docker (`maven:3.9.9-eclipse-temurin-21`) con resultado: 51 tests, 0 fallos.
- Se completaron tests unitarios por cada use case pendiente.
- Se reforzaron escenarios de éxito/fallo/abort/auditoría del `ETLOrchestrator`.
- Nueva ejecución de `mvn test` en Docker: **85 tests, 0 fallos**.
- Cobertura JaCoCo en alcance Fase 2: **94.00% instrucciones** y **95.01% líneas**.

4. **Perfil de despliegue en contenedor**
- Se creó `application-docker.yml`.
- `docker-compose.yml` ahora levanta `app` + `db` con `SPRING_PROFILES_ACTIVE=docker`.
- Se agregó documentación en `docs/runbooks/local-setup.md` para correr stack completo con Docker.

### Verificación en este entorno

- `mvn test` validado mediante Docker.
- Health de app validado con `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

---

## Notas para continuar

> Si llegas a este archivo como agente de continuación:
> 1. Lee este archivo para ver el estado actual
> 2. Lee `docs/action-plan.md` Phase 2 para detalles de cada entregable
> 3. Fase 1 está COMPLETADA — toda la infraestructura base existe
> 4. El package base es `com.elyares.etl`, directorio raíz `/home/elyarestark/develop/OrionETL`
> 5. Fase 2 quedó cerrada; siguiente prioridad recomendada: iniciar Fase 3 (adapters/infra)
> 6. Regla crítica: CERO imports de `org.springframework.*` en `domain/`
