# Runbook: Running a Pipeline

This runbook describes how to trigger, monitor, and debug a pipeline execution in OrionETL. It covers the REST API approach, the CLI approach, and the steps to take when an execution fails.

---

## Prerequisites

Before running any pipeline, confirm the following:

- [ ] Docker Compose is running: `docker ps` shows `orionetl-db` (PostgreSQL) as healthy.
- [ ] The OrionETL application is running (via `mvn spring-boot:run` or Docker).
- [ ] Flyway migrations have run successfully: check application startup logs for `Successfully applied N migrations`.
- [ ] The target pipeline is registered with status `ACTIVE`: query `SELECT name, status FROM etl_pipelines;` in the database.
- [ ] The source data is available (file exists at the expected path, or API is reachable).
- [ ] For file-based pipelines: the source file is in the expected location (check `sourceConfig.connectionDetails.file-path` in the pipeline config).

---

## Triggering a Pipeline via REST API

### Endpoint

```
POST /api/v1/pipelines/{pipelineId}/execute
Content-Type: application/json
```

### Request Body

```json
{
  "triggeredBy": "api:analyst@example.com",
  "parameters": {
    "batch_date": "2026-03-21"
  }
}
```

| Field | Required | Description |
|---|---|---|
| `triggeredBy` | No | Identifier of who triggered the run. Defaults to `"api:anonymous"`. |
| `parameters` | No | Optional execution parameters. Required parameters are defined per pipeline in the YAML config. |

### Example: Trigger the Sales Pipeline

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/sales-daily/execute \
  -H "Content-Type: application/json" \
  -d '{
    "triggeredBy": "api:analyst@example.com",
    "parameters": {
      "batch_date": "2026-03-21"
    }
  }'
```

### Example Response

```json
{
  "status": "ok",
  "data": {
    "executionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "pipelineId": "sales-daily",
    "status": "RUNNING",
    "startedAt": "2026-03-21T02:00:05.123Z",
    "triggeredBy": "api:analyst@example.com"
  }
}
```

Save the `executionId` — you will need it to monitor the execution.

### Common HTTP Status Codes

| Status | Meaning |
|---|---|
| `202 Accepted` | Execution started successfully. |
| `400 Bad Request` | Missing required parameters or invalid request body. |
| `404 Not Found` | Pipeline ID not found or pipeline is not `ACTIVE`. |
| `409 Conflict` | Another execution of this pipeline is already `RUNNING` or `RETRYING`. |

---

## Triggering a Pipeline via CLI

The `run-pipeline.sh` script wraps the REST API for convenience:

```bash
./scripts/run-pipeline.sh --pipeline sales-daily --env local
```

### Options

| Option | Description | Example |
|---|---|---|
| `--pipeline` | Pipeline ID | `--pipeline sales-daily` |
| `--env` | Spring profile to activate | `--env local` or `--env dev` |
| `--param` | Optional parameter (repeatable) | `--param batch_date=2026-03-21` |
| `--wait` | Block until execution completes | `--wait` |
| `--timeout` | Timeout seconds when using `--wait` | `--timeout 300` |

### Example with parameters

```bash
./scripts/run-pipeline.sh \
  --pipeline inventory-sync \
  --env local \
  --param batch_date=2026-03-21 \
  --wait \
  --timeout 600
```

The script exits with `0` on `SUCCESS` or `PARTIAL`, and `1` on `FAILED`, `SKIPPED`, or timeout. This makes it suitable for CI/CD pipeline orchestration.

---

## Monitoring an Execution

### Poll Execution Status

```bash
curl http://localhost:8080/api/v1/executions/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response:**

```json
{
  "status": "ok",
  "data": {
    "executionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "pipelineId": "sales-daily",
    "status": "RUNNING",
    "startedAt": "2026-03-21T02:00:05.123Z",
    "finishedAt": null,
    "totalRead": 12450,
    "totalTransformed": 12388,
    "totalRejected": 62,
    "totalLoaded": 0,
    "steps": [
      { "stepName": "INIT",            "status": "SUCCESS", "recordsProcessed": 0 },
      { "stepName": "EXTRACT",         "status": "SUCCESS", "recordsProcessed": 12450 },
      { "stepName": "VALIDATE_SCHEMA", "status": "SUCCESS", "recordsProcessed": 12450 },
      { "stepName": "TRANSFORM",       "status": "SUCCESS", "recordsProcessed": 12388 },
      { "stepName": "VALIDATE_BUSINESS","status": "RUNNING", "recordsProcessed": 7200 },
      { "stepName": "LOAD",            "status": "PENDING", "recordsProcessed": 0 },
      { "stepName": "CLOSE",           "status": "PENDING", "recordsProcessed": 0 },
      { "stepName": "AUDIT",           "status": "PENDING", "recordsProcessed": 0 }
    ]
  }
}
```

Poll this endpoint every 5–10 seconds until `status` reaches a terminal state: `SUCCESS`, `FAILED`, `PARTIAL`, or `SKIPPED`.

### View Execution Metrics

```bash
curl http://localhost:8080/api/v1/executions/3fa85f64-5717-4562-b3fc-2c963f66afa6/metrics
```

Returns named metrics such as `extract.duration.ms`, `transform.records.per.second`, `records.rejected.rate`.

---

## Checking Logs

### Application Logs (Local)

Application logs are written to the console when running locally. The execution ID and pipeline ID are injected into every log line via MDC:

```
2026-03-21 02:00:05.234 INFO  [exec=3fa85f64] [pipeline=sales-daily] [step=EXTRACT]
  c.e.etl.infrastructure.extractor.csv.CsvExtractor - Reading CSV file: /data/incoming/sales/sales_2026-03-21.csv

2026-03-21 02:00:08.891 INFO  [exec=3fa85f64] [pipeline=sales-daily] [step=VALIDATE_SCHEMA]
  c.e.etl.infrastructure.validator.schema.SchemaValidator - Schema validation completed:
  12450 records checked, 0 rejections, error_rate=0.00%

2026-03-21 02:00:45.123 WARN  [exec=3fa85f64] [pipeline=sales-daily] [step=VALIDATE_BUSINESS]
  c.e.etl.infrastructure.validator.business.BusinessValidator - Catalog lookup rejection:
  product_id 'PRD-99999' not found in products catalog (row 3421)
```

### Filtering Logs by Execution ID

```bash
# If logs are written to a file:
grep "exec=3fa85f64-5717-4562-b3fc-2c963f66afa6" /var/log/orionetl/application.log

# Or via Docker:
docker logs orionetl-app | grep "3fa85f64"
```

### Log Levels by Package

| Package | Default Level | What it logs |
|---|---|---|
| `com.elyares.etl` | `INFO` | Step transitions, record counts, warnings |
| `com.elyares.etl.infrastructure.extractor` | `DEBUG` | File read progress, API page fetches |
| `com.elyares.etl.infrastructure.validator` | `INFO` | Validation summaries, rejection reasons |
| `com.elyares.etl.infrastructure.loader` | `INFO` | Chunk commit progress, staging validation results |
| `com.elyares.etl.domain` | `DEBUG` | Rule evaluations, domain service calls |
| `org.springframework` | `WARN` | Spring framework messages only |
| `org.hibernate.SQL` | `DEBUG` | All SQL statements (enable only for debugging) |

To temporarily enable DEBUG logging for a specific package at runtime, use the Spring Actuator logger endpoint:

```bash
curl -X POST http://localhost:8080/actuator/loggers/com.elyares.etl.infrastructure.extractor \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## Checking the Audit Trail

After an execution completes, the full audit record is available in the `etl_audit_records` table:

```sql
SELECT
    ar.action,
    ar.actor_type,
    ar.timestamp,
    ar.details->>'status' AS status,
    ar.details->>'totalRead' AS total_read,
    ar.details->>'totalLoaded' AS total_loaded,
    ar.details->>'totalRejected' AS total_rejected,
    ar.details->>'durationMs' AS duration_ms,
    ar.details->>'triggeredBy' AS triggered_by
FROM etl_audit_records ar
JOIN etl_pipeline_executions e ON e.id = ar.execution_id
WHERE e.execution_ref = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
```

---

## What to Do If a Pipeline Fails

### Step 1: Check the Execution Status and Error Summary

```bash
curl http://localhost:8080/api/v1/executions/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

Look at:
- `status`: Should be `FAILED`.
- `steps`: Find the step with `status = "FAILED"` — this tells you which phase failed.
- `errorSummary`: Short description of the root cause.

### Step 2: Check the Execution Errors Table

```sql
SELECT
    step_name,
    error_type,
    error_code,
    message,
    record_reference,
    created_at
FROM etl_execution_errors
WHERE execution_id = (
    SELECT id FROM etl_pipeline_executions
    WHERE execution_ref = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
)
ORDER BY created_at;
```

**Interpreting error types:**

| `error_type` | What to look for |
|---|---|
| `TECHNICAL` | Check `stack_trace` for Java exception. Often a file system, network, or DB connectivity issue. |
| `EXTERNAL_INTEGRATION` | Check `message` for API status code or timeout detail. Verify the external service is available. |
| `FUNCTIONAL` | Check `message` and `record_reference` for the business rule that failed. |
| `DATA_QUALITY` | High volume of `DATA_QUALITY` errors indicates a problem with the source data or pipeline config. |

### Step 3: Inspect Rejected Records

```sql
SELECT
    source_row_number,
    step_name,
    rejection_reason,
    raw_data,
    validation_errors,
    rejected_at
FROM etl_rejected_records
WHERE execution_id = (
    SELECT id FROM etl_pipeline_executions
    WHERE execution_ref = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
)
ORDER BY source_row_number
LIMIT 50;
```

For a structured view of validation errors per field:

```sql
SELECT
    source_row_number,
    rejection_reason,
    jsonb_array_elements(validation_errors)->>'field' AS field,
    jsonb_array_elements(validation_errors)->>'rule' AS rule,
    jsonb_array_elements(validation_errors)->>'message' AS message
FROM etl_rejected_records
WHERE execution_id = (
    SELECT id FROM etl_pipeline_executions
    WHERE execution_ref = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
);
```

### Step 4: Determine the Resolution

| Failure Cause | Resolution |
|---|---|
| Source file not found | Verify the file path in `SourceConfig`. Place the correct file and re-trigger. |
| API unavailable (EXTERNAL_INTEGRATION) | Wait for the external service to recover. Re-trigger (the pipeline will retry automatically up to `maxRetries` times). |
| High data quality error rate | Investigate the rejected records. Fix the source data. Re-trigger with the corrected source. |
| Schema change (new/missing column) | Update the `ValidationConfig.mandatoryColumns` and `columnTypes` in the pipeline YAML. Re-deploy. |
| Staging validation failure | Inspect the staging table: `SELECT * FROM {table}_staging WHERE etl_execution_id = '{exec_id}'`. Identify the integrity issue. |
| DB connection failure | Verify PostgreSQL is running and accessible. Check connection pool settings. |

### Step 5: Re-Trigger the Execution

If the issue is transient (external service was down, file was missing but is now present):

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/sales-daily/execute \
  -H "Content-Type: application/json" \
  -d '{"triggeredBy": "api:analyst@example.com", "parameters": {"batch_date": "2026-03-21"}}'
```

If the pipeline has remaining automatic retries (`retryPolicy.maxRetries`), it may be retried automatically by the engine before you need to manually intervene.

---

## Health Check

Verify the application is healthy before triggering pipelines:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "etlEngine": { "status": "UP", "details": { "activePipelines": 3 } }
  }
}
```

If `db.status` is `DOWN`, check that PostgreSQL (Docker Compose) is running and the credentials in `.env` are correct.
