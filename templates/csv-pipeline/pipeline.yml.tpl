pipeline:
  id: "${SCAFFOLD_PIPELINE_UUID}"
  name: "${SCAFFOLD_PIPELINE_NAME}"
  version: "1.0.0"
  description: "TODO: describe el pipeline ${SCAFFOLD_PIPELINE_NAME}"
  status: ACTIVE

  source-config:
    type: CSV
    location: "\${ORION_${SCAFFOLD_UPPER_SLUG}_SOURCE_PATH:/datasets/archive/${SCAFFOLD_SLUG}.csv}"
    delimiter: ","
    encoding: "UTF-8"
    has-header: true
    connection-properties:
      nullValues: ",NULL,N/A,-"
      # TODO: agrega mappings si el CSV trae nombres no canónicos.
      # headerMapping.old_name: "new_name"

  target-config:
    type: DATABASE
    schema: "public"
    staging-table: "${SCAFFOLD_TABLE_PREFIX}_staging"
    final-table: "${SCAFFOLD_TABLE_PREFIX}"
    load-strategy: UPSERT
    business-key:
      - ${SCAFFOLD_BUSINESS_KEY}
    chunk-size: 500
    fail-fast-on-chunk-error: true
    rollback-strategy: DELETE_BY_EXECUTION
    closed-record-guard-enabled: false

  validation-config:
    mandatory-columns:
      - ${SCAFFOLD_BUSINESS_KEY}
    column-types:
      # TODO: define tipos si necesitas DECIMAL, DATE, INTEGER, etc.
      # amount: DECIMAL
    amount-fields: []
    allow-negative-amounts: false
    range-rules: {}
    unique-key-columns:
      - ${SCAFFOLD_BUSINESS_KEY}
    reject-all-duplicates: false
    error-threshold-percent: 2.0
    abort-on-threshold-breach: true

  transformation-config:
    source-timezone: "UTC"
    null-values:
      - ""
      - "NULL"
      - "N/A"
      - "-"
    rounding-scale: 2
    rounding-mode: HALF_UP
    rounding-fields: []

  retry-policy:
    max-retries: 2
    retry-delay-ms: 120000
    retry-on-errors:
      - TECHNICAL
      - EXTERNAL_INTEGRATION

  schedule-config:
    cron: "0 0 2 * * *"
    timezone: "UTC"
    allowed-windows:
      - start: "01:30"
        end: "04:00"
        days:
          - MONDAY
          - TUESDAY
          - WEDNESDAY
          - THURSDAY
          - FRIDAY
          - SATURDAY
          - SUNDAY
