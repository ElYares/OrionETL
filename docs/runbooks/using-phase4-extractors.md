# Runbook: Using Phase 4 Extractors

This runbook explains how to work with the extractors completed in Phase 4, how to verify them locally, and how to inspect the output they produce.

---

## Scope

Phase 4 completed these components:

- `ExtractorRegistry`
- `CsvExtractor`
- `ApiExtractor`
- `CsvPreviewRunner`

At this stage you can validate data extraction from CSV files and HTTP APIs, but this is still only the extract layer. Transformation, validation, and loading are handled in later phases.

---

## What You Can Do Today

- Read real CSV files into `RawRecord` objects.
- Read API responses into `RawRecord` objects.
- Use header mapping in CSV extraction.
- Normalize null-like values in CSV extraction.
- Use API auth modes: `BEARER`, `BASIC`, `API_KEY`, `NONE`.
- Use API pagination modes: `CURSOR`, `OFFSET`.
- Retry transient API `5xx` responses.
- Preview extracted CSV records directly in the console.

---

## What You Still Cannot Do Here

- Run a full business ETL flow from source to final load just with this runbook.
- Execute REST pipeline runs end-to-end, because later infrastructure is still pending.
- Inspect extracted API records through a dedicated REST endpoint. For now, verification is done through tests and console preview.

---

## Prerequisites

- Docker available on the machine.
- Project root: `/home/elyarestark/develop/OrionETL`
- Optional Kaggle CSV dataset path:
  `/home/elyarestark/develop/datasets/archive`

Move to the project root before running commands:

```bash
cd /home/elyarestark/develop/OrionETL
```

---

## 1) Validate All Current Unit Tests

Use this when you want to confirm the codebase still builds and the current extractor implementation is healthy.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Expected result:

- Surefire reports generated under `target/surefire-reports/`
- No failures
- No errors

---

## 2) Validate Extractor Integration Tests

Use this when you want to verify CSV and API extraction behavior without running the entire project stack.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

Where to inspect the result:

- `target/failsafe-reports/TEST-com.elyares.etl.integration.extractor.CsvExtractorIT.xml`
- `target/failsafe-reports/TEST-com.elyares.etl.integration.extractor.ApiExtractorIT.xml`

Healthy result:

- `failures=0`
- `errors=0`

---

## 3) Validate CSV Extraction Against Kaggle Dataset

Use this when you want to prove that the extractor reads a real external dataset mounted into a container.

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -e ORION_DATASETS_ARCHIVE=/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

What this validates:

- `CsvExtractorIT` reads `Trans_dim.csv`
- `CsvExtractorIT` reads the local fixture `sales_sample.csv`
- `ApiExtractorIT` still passes in the same run

---

## 4) Preview CSV Output in Console

Use this when you want to actually see extracted rows instead of only checking test reports.

### Example with Kaggle dataset

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q spring-boot:run \
    -Dspring-boot.run.arguments="--spring.main.web-application-type=none,--orionetl.csv-preview.enabled=true,--orionetl.csv-preview.path=/datasets/archive/fact_table.csv,--orionetl.csv-preview.limit=10,--orionetl.csv-preview.null-values=,NULL,N/A,-,--orionetl.csv-preview.header-mapping.payment_key=payment_id"
```

Expected console output:

- One line like `csv_preview_total_read=...`
- Then lines like:

```text
row=2 data={"payment_id":"P026","coustomer_key":"C004510",...}
row=3 data={"payment_id":"P022","coustomer_key":"C008967",...}
```

### What the preview does

- Reads the configured CSV file.
- Applies null normalization using `orionetl.csv-preview.null-values`.
- Applies header mapping using `orionetl.csv-preview.header-mapping.*`.
- Prints the first `N` rows according to `orionetl.csv-preview.limit`.

### Useful preview properties

- `orionetl.csv-preview.path`
- `orionetl.csv-preview.limit`
- `orionetl.csv-preview.encoding`
- `orionetl.csv-preview.delimiter`
- `orionetl.csv-preview.has-header`
- `orionetl.csv-preview.null-values`
- `orionetl.csv-preview.quote-char`
- `orionetl.csv-preview.header-mapping.<sourceHeader>=<targetHeader>`

---

## 5) Fixtures Available in the Repo

Use these when you want deterministic local verification without relying on an external dataset path.

- `src/test/resources/fixtures/sales_sample.csv`
- `src/test/resources/fixtures/sales_invalid.csv`
- `src/test/resources/fixtures/api_customers_response.json`

What each one is for:

- `sales_sample.csv`: valid CSV sample for extraction.
- `sales_invalid.csv`: CSV sample with anomalies useful for later phases.
- `api_customers_response.json`: API fixture for extractor tests.

---

## 6) Where the Implementations Live

- `src/main/java/com/elyares/etl/infrastructure/extractor/ExtractorRegistry.java`
- `src/main/java/com/elyares/etl/infrastructure/extractor/csv/CsvExtractor.java`
- `src/main/java/com/elyares/etl/infrastructure/extractor/api/ApiExtractor.java`
- `src/main/java/com/elyares/etl/infrastructure/extractor/csv/CsvPreviewRunner.java`

---

## 7) Where the Tests Live

- `src/test/java/com/elyares/etl/unit/extractor/ExtractorRegistryTest.java`
- `src/test/java/com/elyares/etl/unit/extractor/CsvExtractorTest.java`
- `src/test/java/com/elyares/etl/unit/extractor/ApiExtractorTest.java`
- `src/test/java/com/elyares/etl/integration/extractor/CsvExtractorIT.java`
- `src/test/java/com/elyares/etl/integration/extractor/ApiExtractorIT.java`

---

## 8) Typical Workflow

1. Run unit tests.
2. Run extractor integration tests.
3. Run the CSV preview command if you want to inspect real records.
4. Use Kaggle-mounted runs only when you need external dataset verification.
5. Use repo fixtures when you want stable, repeatable checks.

---

## 9) Troubleshooting

### CSV preview prints nothing useful

Check:

- the file path in `--orionetl.csv-preview.path`
- whether the delimiter matches the file
- whether `has-header` is correct

### Kaggle CSV test is skipped

Check:

- that `/home/elyarestark/develop/datasets/archive` exists on the host
- that the directory is mounted as `/datasets/archive`
- that `ORION_DATASETS_ARCHIVE=/datasets/archive` is passed to the container

### API extractor test fails with HTTP error

Check:

- `responseArrayPath`
- auth-related properties
- pagination configuration
- whether the API returned `401`, `403`, or `5xx`

### Failsafe command cannot find newly added test classes

Use:

```bash
mvn test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=ApiExtractorIT,CsvExtractorIT failsafe:integration-test failsafe:verify
```

This matters when running IT directly without full `verify`.
