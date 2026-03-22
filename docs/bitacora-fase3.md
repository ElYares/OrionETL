# Bitácora — Fase 3: Infrastructure Persistence

**Objetivo:** Implementar la capa de persistencia (entidades JPA, repositorios Spring Data y adapters que implementan contratos de dominio).

**Estado actual:** ✅ COMPLETADA

**Fecha inicio:** 2026-03-22

---

## Checklist de entregables

### Bloque 1 — Entidades JPA
- [x] `infrastructure/persistence/entity/EtlPipelineEntity.java`
- [x] `infrastructure/persistence/entity/EtlPipelineExecutionEntity.java`
- [x] `infrastructure/persistence/entity/EtlExecutionStepEntity.java`
- [x] `infrastructure/persistence/entity/EtlExecutionErrorEntity.java`
- [x] `infrastructure/persistence/entity/EtlRejectedRecordEntity.java`
- [x] `infrastructure/persistence/entity/EtlAuditRecordEntity.java`
- [x] `infrastructure/persistence/entity/EtlExecutionMetricEntity.java`

### Bloque 2 — Repositorios Spring Data JPA
- [x] `infrastructure/persistence/repository/JpaEtlPipelineRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlPipelineExecutionRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlExecutionStepRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlExecutionErrorRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlRejectedRecordRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlAuditRecordRepository.java`
- [x] `infrastructure/persistence/repository/JpaEtlExecutionMetricRepository.java`

### Bloque 3 — Adapters de contratos de dominio
- [x] `infrastructure/persistence/adapter/PipelineRepositoryAdapter.java`
- [x] `infrastructure/persistence/adapter/ExecutionRepositoryAdapter.java`
- [x] `infrastructure/persistence/adapter/AuditRepositoryAdapter.java`
- [x] `infrastructure/persistence/adapter/RejectedRecordRepositoryAdapter.java`

### Bloque 4 — JSON mapping util
- [x] `infrastructure/persistence/mapper/PersistenceJsonMapper.java`

### Bloque 5 — Flyway V2
- [x] `db/migration/V2__add_indexes.sql`

### Bloque 6 — Validación
- [x] Compilación + tests existentes en Docker (`mvn -q test`)
- [x] Tests de integración de adapters con Testcontainers PostgreSQL (clases `*IT` agregadas)
- [x] Validación explícita de serialización/deserialización JSONB en rejected/audit/config (escenarios implementados en IT)
- [x] Ejecución efectiva de IT en Docker Compose perfil `integration-tests` (`failsafe_total=6`, `failures=0`, `errors=0`, `skipped=0`)

---

## Avance 2026-03-22

1. Se creó la estructura `infrastructure/persistence/` completa para Fase 3.
2. Se implementaron entidades JPA para todas las tablas core de metadatos ETL.
3. Se implementaron repositorios Spring Data con queries necesarias para ejecución activa e historial.
4. Se implementaron adapters que aterrizan los contratos de dominio:
- `PipelineRepository`
- `ExecutionRepository`
- `AuditRepository`
- `RejectedRecordRepository`
5. Se agregó migración `V2__add_indexes.sql` para índices y restricción única por paso de ejecución.
6. Se validó compilación y suite existente con Docker: **OK**.
7. Se agregaron pruebas de integración de persistencia:
- `integration/persistence/support/PostgresIntegrationTestBase.java`
- `integration/persistence/PipelineRepositoryAdapterIT.java`
- `integration/persistence/ExecutionRepositoryAdapterIT.java`
- `integration/persistence/AuditAndRejectedRepositoryAdapterIT.java`
8. Se implementaron validaciones de round-trip JSONB para:
- `etl_pipelines.config_json`
- `etl_audit_records.details`
- `etl_rejected_records.raw_data`
- `etl_rejected_records.validation_errors`
9. Ejecución inicial de integración con `mvn verify` en contenedor Maven: `completed=6`, `skipped=6`, `errors=0`, `failures=0`.
10. Se agregó perfil Docker `integration-tests` con `docker:dind` + `it-runner` para ejecutar `mvn verify` totalmente en contenedores y evitar dependencia del daemon viejo del host.
11. Durante ejecución real de IT en Docker se corrigieron hallazgos de persistencia:
- Mapeo JSONB en entidades (`@JdbcTypeCode(SqlTypes.JSON)`).
- `PipelineRepositoryAdapter.buildConfigJson` para soportar valores `null` (evitar `Map.of`).
- Mapeo de `ExecutionId` lógico ↔ PK en adapters de `Audit` y `RejectedRecord`.
- Transacción faltante en `ExecutionRepositoryAdapter.save`.
12. Ejecución final en Docker Compose: `completed=6`, `skipped=0`, `errors=0`, `failures=0` (**OK**).

### Ejecución IT 100% Docker (recomendada)

```bash
docker compose --profile integration-tests up -d docker-it
docker compose --profile integration-tests run --rm it-runner
docker compose --profile integration-tests down
```

### Evidencia de resultado

```bash
cat target/failsafe-reports/failsafe-summary.xml
```

---

## Siguiente prioridad

1. Fase 3 cerrada. Iniciar Fase 4 según plan (Infrastructure Extractors).
