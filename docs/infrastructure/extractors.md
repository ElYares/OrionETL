# Extractors

Extractors are the first active component in every ETL pipeline. They are responsible for reading raw data from a configured source and producing a list of `RawRecord` objects for downstream processing.

All extractors implement the `DataExtractor` domain contract and live in `infrastructure/extractor/`.

## Current Status (2026-03-23)

- `ExtractorRegistry`: implemented.
- `CsvExtractor`: implemented and tested (unit + integration + Kaggle dataset run in Docker).
- `ApiExtractor`: implemented and tested (pagination, auth, retry, HTTP error wrapping).
- `ExcelExtractor`: implemented and tested for `.xlsx`.
- `DatabaseExtractor`: implemented and tested with PostgreSQL/Testcontainers.

---

## Domain Contract

```java
// domain/contract/DataExtractor.java
public interface DataExtractor {
    /**
     * Returns true if this extractor supports the given source type.
     * Used by the extractor registry to resolve the correct implementation.
     */
    boolean supports(SourceType sourceType);

    /**
     * Extracts records from the configured source.
     *
     * @param config    Full source configuration (connection details, format, auth, etc.)
     * @param execution The active pipeline execution (for logging and step tracking)
     * @return ExtractionResult containing the list of RawRecord objects and metadata
     * @throws ExtractionException if the source cannot be read
     */
    ExtractionResult extract(SourceConfig config, PipelineExecution execution);
}
```

### `ExtractionResult`

```java
public record ExtractionResult(
    List<RawRecord> records,
    long totalRecords,
    String sourceReference,     // File path, API URL, or DB query description
    Instant extractedAt,
    Map<String, Object> metadata  // Extractor-specific metadata (e.g., sheet name, API cursor state)
) {}
```

---

## Extractor Registry

The application context holds an `ExtractorRegistry` bean that resolves the correct `DataExtractor` at runtime:

```java
@Component
public class ExtractorRegistry {
    private final List<DataExtractor> extractors;

    public DataExtractor resolve(SourceType sourceType) {
        return extractors.stream()
            .filter(e -> e.supports(sourceType))
            .findFirst()
            .orElseThrow(() -> new EtlException("No extractor found for source type: " + sourceType));
    }
}
```

---

## CsvExtractor

**Location:** `infrastructure/extractor/csv/CsvExtractor.java`
**Source Type:** `SourceType.CSV`
**Library:** OpenCSV (`com.opencsv`) or Apache Commons CSV (configurable)

### Features

- Custom delimiter (`,`, `;`, `|`, `\t` supported).
- Custom quote character (default: `"`).
- Custom escape character.
- File encoding (UTF-8, ISO-8859-1, UTF-16, and others via `Charset.forName()`).
- Header row detection: if `hasHeaders = true`, the first row is used as the key names for the `RawRecord.data` map.
- Header mapping: if `sourceConfig.headerMapping` is configured, source header names are remapped to canonical names during extraction (before returning `RawRecord`s).
- Null value handling: configurable strings to treat as null (e.g., `["", "NULL", "null", "N/A", "-"]`).
- Large file support: records are read lazily via an iterator; the full file is not loaded into memory at once.

### Implementation Details

```java
@Component
public class CsvExtractor implements DataExtractor {

    @Override
    public boolean supports(SourceType sourceType) {
        return sourceType == SourceType.CSV;
    }

    @Override
    public ExtractionResult extract(SourceConfig config, PipelineExecution execution) {
        Path filePath = resolveFilePath(config, execution);
        char delimiter = config.getDelimiter() != null ? config.getDelimiter() : ',';
        Charset charset = Charset.forName(config.getEncoding() != null ? config.getEncoding() : "UTF-8");

        List<RawRecord> records = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(Files.newInputStream(filePath), charset))
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build()) {

            String[] headers = config.isHasHeaders() ? reader.readNext() : null;
            String[] line;
            long rowNumber = config.isHasHeaders() ? 2L : 1L;

            while ((line = reader.readNext()) != null) {
                Map<String, Object> data = buildDataMap(headers, line, config.getHeaderMapping());
                records.add(new RawRecord(rowNumber++, data, filePath.toString(), Instant.now()));
            }
        } catch (IOException e) {
            throw new ExtractionException("Failed to read CSV file: " + filePath, e);
        }

        return new ExtractionResult(records, records.size(), filePath.toString(), Instant.now(), Map.of());
    }
}
```

### Configuration Example

```yaml
source-config:
  type: CSV
  connection-details:
    file-path: "/data/incoming/sales/sales_2026-03-21.csv"
  delimiter: ","
  encoding: "UTF-8"
  has-headers: true
  header-mapping:
    "TransactionID": "transaction_id"
    "CustomerID": "customer_id"
    "SaleAmount": "amount"
  null-values:
    - ""
    - "NULL"
    - "N/A"
```

---

## ExcelExtractor

**Location:** `infrastructure/extractor/excel/ExcelExtractor.java`
**Source Type:** `SourceType.EXCEL`
**Library:** Apache POI (`org.apache.poi`)

### Features

- Supports `.xlsx` (XSSF) and `.xls` (HSSF) formats.
- Sheet selection by name or by index (0-based).
- Configurable header row number (default: row 1).
- Configurable data start row (default: row 2).
- Cell type handling: numeric, string, boolean, date, formula (formula cells are evaluated).
- Date cell detection: Excel stores dates as numeric values. Apache POI's `DateUtil.isCellDateFormatted()` is used to correctly identify date cells and return them as `LocalDate` or `LocalDateTime`.
- Empty row handling: configurable — skip empty rows (default) or include them as records with all-null data.
- Large file support: uses `SXSSFWorkbook` for streaming reads of large `.xlsx` files to avoid OOM errors.

### Configuration Example

```yaml
source-config:
  type: EXCEL
  connection-details:
    file-path: "/data/incoming/inventory/inventory_2026-03-21_06.xlsx"
    sheet-name: "Inventory"
    header-row: 1
    data-start-row: 2
  has-headers: true
```

---

## JsonExtractor

**Location:** `infrastructure/extractor/json/JsonExtractor.java`
**Source Type:** `SourceType.JSON`
**Library:** Jackson (`com.fasterxml.jackson.databind`)

### Features

- **Array mode:** expects the file to be a JSON array `[{...}, {...}]`. Each array element becomes a `RawRecord`.
- **Object mode:** expects the file to be a JSON object with a configured key pointing to an array (e.g., `{"data": [{...}], "meta": {...}}`). The `responseArrayPath` config specifies the key.
- **Nested field flattening:** optional. If enabled, nested objects are flattened to dot-notation keys: `{"address": {"city": "Bogotá"}}` → `{"address.city": "Bogotá"}`.
- Field type preservation: values are read as their natural JSON types (String, Number, Boolean, null) without coercion.

### Configuration Example

```yaml
source-config:
  type: JSON
  connection-details:
    file-path: "/data/incoming/customers/customers_export.json"
    response-array-path: "customers"
    flatten-nested: true
  has-headers: false  # N/A for JSON — keys are used directly
```

---

## ApiExtractor

**Location:** `infrastructure/extractor/api/ApiExtractor.java`
**Source Type:** `SourceType.API`
**Library:** Spring WebClient (reactive), with blocking `.block()` calls for synchronous ETL flow

### Features

- **HTTP methods:** GET and POST.
- **Authentication:**
  - `BEARER`: `Authorization: Bearer {token}` header.
  - `BASIC`: `Authorization: Basic {base64(user:password)}` header.
  - `API_KEY`: custom header name and value configured per pipeline.
  - `NONE`: no authentication.
- **Pagination strategies:**
  - `CURSOR`: follows `next_cursor` or equivalent field in the response metadata. Stops when cursor is null or absent.
  - `OFFSET`: uses `page` and `size` query parameters. Stops when response array is empty.
  - `LINK_HEADER`: follows `Link: <url>; rel="next"` HTTP response header (RFC 5988).
  - `NONE`: single request, no pagination.
- **Response parsing:** uses `responseArrayPath` to extract the record array from the JSON response (e.g., `"data"`, `"results"`, `"transactions"`).
- **Retry on 5xx:** configurable number of retries with exponential backoff for transient server errors.
- **Request filtering:** supports `updated_since` or similar filter parameters for incremental sync.
- **Timeout:** configurable per request (default: 30 seconds).

---

## DatabaseExtractor

**Location:** `infrastructure/extractor/database/DatabaseExtractor.java`
**Source Type:** `SourceType.DATABASE`
**Library:** Spring JDBC (`JdbcTemplate` + named parameter utilities)

### Features

- JDBC connection resolved from `SourceConfig.location` or `connectionProperties.jdbcUrl`.
- Username/password from `connectionProperties` or env indirection (`usernameEnv`, `passwordEnv`).
- Query execution with named parameters using `queryParam.*`.
- Optional explicit parameter typing with `queryParamType.*` or `queryParamTypes.*`.
- `fetchSize` hint on the prepared statement for cursor-friendly reads.
- Result rows mapped to `RawRecord` preserving database column labels.

### Configuration Example

```yaml
source-config:
  type: DATABASE
  location: "jdbc:postgresql://postgres:5432/analytics"
  connection-properties:
    username: "reader"
    password: "${DB_PASSWORD}"
    query: >
      SELECT order_id, customer_id, amount, status
      FROM source_orders
      WHERE status = :status
        AND amount >= :minAmount
      ORDER BY order_id
    fetchSize: "500"
    queryParam.status: "OPEN"
    queryParam.minAmount: "10.00"
    queryParamType.minAmount: "DECIMAL"
```

### Operational Notes

- V1 implementation is synchronous and optimized for straightforward relational reads.
- It is appropriate for pull-style ingestion from PostgreSQL and similar JDBC sources.
- Advanced cross-database features such as vendor-specific cursors or partitioned parallel reads remain V2 work.

### Implementation Sketch

```java
@Component
public class ApiExtractor implements DataExtractor {

    private final WebClient.Builder webClientBuilder;

    @Override
    public boolean supports(SourceType sourceType) {
        return sourceType == SourceType.API;
    }

    @Override
    public ExtractionResult extract(SourceConfig config, PipelineExecution execution) {
        WebClient client = buildWebClient(config);
        List<RawRecord> allRecords = new ArrayList<>();
        String cursor = null;
        long rowNumber = 1;

        do {
            ApiResponse response = fetchPage(client, config, cursor);
            List<Map<String, Object>> pageData = extractArray(response, config.getResponseArrayPath());

            for (Map<String, Object> item : pageData) {
                allRecords.add(new RawRecord(rowNumber++, item, config.getUrl(), Instant.now()));
            }

            cursor = extractNextCursor(response, config.getPaginationType(), config.getCursorField());
        } while (cursor != null);

        return new ExtractionResult(allRecords, allRecords.size(), config.getUrl(), Instant.now(), Map.of());
    }
}
```

### Configuration Example

```yaml
source-config:
  type: API
  connection-details:
    url: "https://crm.internal/api/v3/customers"
    method: GET
    response-array-path: "customers"
    pagination-type: CURSOR
    cursor-field: "meta.next_cursor"
    page-size: 200
    filter-param: "updated_since"
  auth-config:
    type: BEARER
    token-env: "CRM_API_TOKEN"
  timeout-ms: 30000
```

---

## DatabaseExtractor

**Location:** `infrastructure/extractor/database/DatabaseExtractor.java`
**Source Type:** `SourceType.DATABASE`
**Library:** Spring JDBC (`JdbcTemplate`)

### Features

- Executes a configured SQL query against a configured data source.
- **Parameter binding:** supports named parameters in the SQL query (e.g., `WHERE updated_at > :since`), bound from execution parameters.
- **Cursor-based reading:** uses `JdbcTemplate.queryForStream()` or `ResultSetExtractor` with `fetchSize` hint for large result sets. Does not load the entire result set into memory.
- **Column mapping:** result set column names are automatically snake_cased and used as `RawRecord` keys.
- **Multiple data sources:** each `DatabaseExtractor` instance can be configured with a specific DataSource (not necessarily the same PostgreSQL instance as OrionETL's metadata store).
- **JDBC URL configuration:** supports PostgreSQL, MySQL, Oracle, and any JDBC-compliant database.

### Configuration Example

```yaml
source-config:
  type: DATABASE
  connection-details:
    jdbc-url: "jdbc:postgresql://source-db:5432/erp"
    username-env: "SOURCE_DB_USER"
    password-env: "SOURCE_DB_PASSWORD"
    query: |
      SELECT
          t.id AS transaction_id,
          t.customer_id,
          t.product_id,
          t.amount,
          t.currency,
          t.transaction_date AS sale_date,
          t.salesperson_id,
          t.channel_code AS channel
      FROM erp.transactions t
      WHERE t.transaction_date = :batch_date
        AND t.status = 'CONFIRMED'
      ORDER BY t.id
    fetch-size: 1000
    parameters:
      batch_date: "{execution.parameters.batch_date}"
```

---

## Error Handling Summary

| Extractor | Common Errors | Error Type |
|---|---|---|
| CsvExtractor | File not found, permission denied, malformed CSV | `TECHNICAL` |
| ExcelExtractor | Corrupted `.xlsx`, password-protected file, missing sheet | `TECHNICAL` |
| JsonExtractor | Malformed JSON, missing array path key | `TECHNICAL` |
| ApiExtractor | Network timeout, 401/403 auth failure, 503 unavailable | `EXTERNAL_INTEGRATION` |
| DatabaseExtractor | JDBC connection failure, SQL syntax error, timeout | `TECHNICAL` or `EXTERNAL_INTEGRATION` |

All extractors throw `ExtractionException` (which extends `EtlException`) on failure. The `ETLOrchestrator` catches this and transitions the execution to `FAILED` state, skipping all downstream steps.
