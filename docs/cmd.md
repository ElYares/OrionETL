# OrionETL — Comandos Operativos

Comandos rápidos para trabajar el proyecto en desarrollo local y pruebas.

## 1) Levantar stack principal (app + db)

```bash
docker compose up -d --build
```

Ver estado:

```bash
docker compose ps
```

Logs de app:

```bash
docker compose logs -f app
```

## 2) Bajar stack principal

```bash
docker compose down
```

## 3) Ejecutar tests unitarios en contenedor Maven

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

## 4) Ejecutar integration tests (IT) 100% en Docker

Perfil de Compose: `integration-tests` (usa `docker:dind` + `it-runner`).

```bash
docker compose --profile integration-tests up -d docker-it
docker compose --profile integration-tests run --rm it-runner
```

Resumen de resultados IT:

```bash
cat target/failsafe-reports/failsafe-summary.xml
```

Formato esperado para éxito total:
- `completed=6`
- `failures=0`
- `errors=0`
- `skipped=0`

## 5) Apagar perfil de integration-tests

```bash
docker compose --profile integration-tests down
```

Nota: este `down` detiene también los servicios del proyecto en el mismo `docker-compose.yml`.

## 6) Si usas Maven local (opcional)

Suite completa (unit + IT):

```bash
mvn verify
```

Solo IT:

```bash
mvn failsafe:integration-test failsafe:verify
```

## 7) Reportes de test

Unit tests (Surefire):
- `target/surefire-reports/`

Integration tests (Failsafe):
- `target/failsafe-reports/`

Cobertura (JaCoCo):
- `target/site/jacoco/index.html`
