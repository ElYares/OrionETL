<p align="center">
  <img src="./assets/orionetl-logo.png" alt="OrionETL Logo" width="260" />
</p>

<h1 align="center">OrionETL</h1>

<p align="center">
  Motor ETL empresarial construido con Java 21 y Spring Boot 3.
</p>

<p align="center">
  Extrae, transforma, valida y carga datos con trazabilidad completa, auditoría y ejecución por pipelines.
</p>

<p align="center">
  <strong>CSV</strong> · <strong>API</strong> · <strong>EXCEL</strong> · <strong>DATABASE</strong>
</p>

## Overview

OrionETL está diseñado para ejecutar pipelines configurados por YAML y llevar cada ejecución desde la extracción hasta la carga final con observabilidad y control operativo.

## Qué hace

OrionETL ejecuta pipelines configurados por YAML y registra:

- extracción desde `CSV`, `API` y `Excel`
- extracción desde `DATABASE` por JDBC
- validación estructural y de negocio
- transformación común y específica por pipeline
- carga a `staging` y promoción a tabla final
- auditoría, métricas y rechazados persistidos
- ejecución por REST y monitoreo por Actuator
- reintentos automáticos ante fallos técnicos reintentables

Pipelines V1 implementados:

- `sales-daily`
- `inventory-sync`
- `customer-sync`
- `item-sync`

## Quick Links

- [Quick Start](#quick-start)
- [API principal](#api-principal)
- [Documentación completa](#documentación-completa)
- [Flujo para crear nuevos pipelines CSV](./docs/flujo.md)

## Quick Start

### 1. Clonar y entrar

```bash
git clone <tu-repo>
cd OrionETL
```

### 2. Levantar stack con Docker Compose

Si quieres sobreescribir credenciales o puertos, crea un archivo `.env` en la raíz con variables como:

```bash
POSTGRES_DB=orionetl
POSTGRES_USER=orionetl
POSTGRES_PASSWORD=orionetl
POSTGRES_PORT=5432
```

Luego:

```bash
docker compose up -d --build
docker compose ps
```

### 3. Verificar health

```bash
curl http://localhost:8080/actuator/health
```

Respuesta esperada:

```json
{"status":"UP"}
```

### 4. Consultar pipelines

```bash
curl http://localhost:8080/api/v1/pipelines
```

### 5. Ejecutar un pipeline

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/sales-daily/execute \
  -H "Content-Type: application/json" \
  -d '{
    "triggeredBy": "api:manual",
    "parameters": {
      "batch_date": "2026-03-23"
    }
  }'
```

## Ejecutar local sin Docker

Requiere PostgreSQL disponible y variables `POSTGRES_*` configuradas.

```bash
mvn spring-boot:run
```

## Build y tests

Unit tests:

```bash
mvn test
```

Suite completa:

```bash
mvn verify
```

Build Docker:

```bash
docker build -t orionetl:v1 .
```

## API principal

- `GET /api/v1/pipelines`
- `GET /api/v1/pipelines/{pipelineRef}`
- `GET /api/v1/pipelines/{pipelineRef}/executions`
- `POST /api/v1/pipelines/{pipelineRef}/execute`
- `GET /api/v1/executions/{executionId}`
- `GET /api/v1/executions/{executionId}/metrics`
- `GET /api/v1/executions/{executionId}/rejected?page=0&size=50`
- `GET /actuator/health`

## Documentación completa

La documentación operativa, arquitectura y bitácoras vive en:

- [docs/README.md](./docs/README.md)

Puntos de entrada útiles:

- [Entender OrionETL](./docs/runbooks/understanding-orionetl.md)
- [Running a Pipeline](./docs/runbooks/running-a-pipeline.md)
- [Current Architecture Context](./docs/architecture/current-architecture-context.md)
- [Command Reference](./docs/cmd.md)

## Contribución

- Mantén `domain/` libre de dependencias Spring.
- No metas lógica de negocio en controllers ni repositories.
- Agrega unit tests para `domain/` y `application/`.
- Usa IT/E2E en Docker cuando toques infraestructura o pipelines reales.
- Documenta cada fase importante en `docs/bitacora-faseX.md`.
