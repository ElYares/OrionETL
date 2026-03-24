# Bitácora — Fase 4: Infrastructure Extractors

**Objetivo:** Implementar extractores prioritarios de infraestructura para habilitar lectura de fuentes reales.  
**Estado actual:** ✅ COMPLETADA  
**Fecha inicio:** 2026-03-22

---

## Checklist de entregables

### Bloque 1 — ExtractorRegistry
- [x] `infrastructure/extractor/ExtractorRegistry.java`
- [x] Resolución por `SourceType` con error explícito si no hay extractor

### Bloque 2 — CsvExtractor
- [x] `infrastructure/extractor/csv/CsvExtractor.java`
- [x] Lectura CSV con OpenCSV
- [x] Delimitador y encoding configurables desde `SourceConfig`
- [x] Soporte de comillas configurables (`quoteChar`)
- [x] Manejo de header + `headerMapping.*`
- [x] Normalización de nulos (`nullValues`)
- [x] Seguimiento de número de fila (1-based y considerando header)
- [x] `ExtractionException` con contexto de archivo

### Bloque 3 — ApiExtractor
- [x] `infrastructure/extractor/api/ApiExtractor.java`
- [x] HTTP `GET` / `POST`
- [x] `responseArrayPath`
- [x] paginación `CURSOR` y `OFFSET`
- [x] auth `BEARER`, `BASIC`, `API_KEY`, `NONE`
- [x] retry en `5xx`
- [x] timeout por request
- [x] wrapping de errores HTTP en `ExtractionException`

### Bloque 4 — Integración con application layer
- [x] `ExtractDataUseCase` refactorizado para usar `ExtractorRegistry`
- [x] Constructor de compatibilidad para pruebas existentes (`List<DataExtractor>`)

### Bloque 5 — Pruebas
- [x] Unit test `ExtractorRegistryTest`
- [x] Unit test `CsvExtractorTest` (header mapping, null normalization, quoted values)
- [x] Unit test `ApiExtractorTest` (cursor pagination, retry 503, 401, `supports`)
- [x] Integration test `CsvExtractorIT` con fixture del repo (`sales_sample.csv`)
- [x] Integration test `CsvExtractorIT` con dataset real Kaggle (`Trans_dim.csv`)
- [x] Integration test `ApiExtractorIT` con fixture JSON local (`api_customers_response.json`)

### Bloque 6 — Fixtures
- [x] `src/test/resources/fixtures/sales_sample.csv` (100 filas reales)
- [x] `src/test/resources/fixtures/sales_invalid.csv` (20 filas con anomalías)
- [x] `src/test/resources/fixtures/api_customers_response.json`

### Bloque 7 — Preview runner
- [x] `infrastructure/extractor/csv/CsvPreviewRunner.java`
- [x] preview por consola de primeras N filas
- [x] soporte para `header-mapping.*`, `null-values`, `quote-char`

### Bloque 8 — Corrección detectada durante implementación
- [x] `RawRecord.getData()` actualizado para permitir mapas con valores `null` sin romper inmutabilidad.

---

## Evidencia de ejecución

### Unit tests (Docker Maven)

Comando:

```bash
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
```

Resultado consolidado:
- `surefire_total=94`
- `failures=0`
- `errors=0`
- `skipped=0`

### IT de extractores en Docker

Comando base:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

Resultado:
- `ApiExtractorIT total=1`
- `CsvExtractorIT total=2`
- `failures=0`
- `errors=0`

### IT de CSV con dataset Kaggle montado en contenedor

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

Resultado:
- `CsvExtractorIT total=2`
- `ApiExtractorIT total=1`
- `failures=0`
- `errors=0`
- `skipped=0`

---

## Comando de preview CSV por consola

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q spring-boot:run \
    -Dspring-boot.run.arguments=\"--spring.main.web-application-type=none,--orionetl.csv-preview.enabled=true,--orionetl.csv-preview.path=/datasets/archive/fact_table.csv,--orionetl.csv-preview.limit=10,--orionetl.csv-preview.null-values=,NULL,N/A,-,--orionetl.csv-preview.header-mapping.payment_key=payment_id\"
```

Salida esperada:
- `csv_preview_total_read=...`
- líneas `row=N data={...}` con muestra de registros normalizados
