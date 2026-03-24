#!/usr/bin/env bash

set -euo pipefail

# Genera un esqueleto completo para un pipeline CSV nuevo siguiendo la estructura real de OrionETL.
# La salida incluye:
# - migración Flyway
# - YAML del pipeline
# - clase *PipelineConfig
# - transformer específico
# - test unitario del transformer
# - E2E del pipeline
#
# Uso:
#   scripts/new-csv-pipeline.sh payments payments-sync etl_payments payment_key
#
# Parámetros:
#   1. slug base           -> payments
#   2. nombre pipeline     -> payments-sync
#   3. prefijo de tablas   -> etl_payments
#   4. business key        -> payment_key

if [[ $# -ne 4 ]]; then
  cat <<'EOF'
Uso:
  scripts/new-csv-pipeline.sh <slug> <pipeline-name> <table-prefix> <business-key>

Ejemplo:
  scripts/new-csv-pipeline.sh payments payments-sync etl_payments payment_key
EOF
  exit 1
fi

slug="$1"
pipeline_name="$2"
table_prefix="$3"
business_key="$4"

camel_case() {
  local input="$1"
  echo "$input" | awk -F'[-_]' '{
    for (i = 1; i <= NF; i++) {
      $i = toupper(substr($i, 1, 1)) tolower(substr($i, 2))
    }
    gsub(" ", "", $0)
    print $0
  }'
}

upper_slug="$(echo "$slug" | tr '[:lower:]-' '[:upper:]_')"
class_prefix="$(camel_case "$slug")"
pipeline_uuid="$(cat /proc/sys/kernel/random/uuid)"

pipeline_dir="src/main/java/com/elyares/etl/pipelines/${slug}"
transformer_dir="src/main/java/com/elyares/etl/infrastructure/transformer/${slug}"
unit_test_dir="src/test/java/com/elyares/etl/unit/transformer"
e2e_test_dir="src/test/java/com/elyares/etl/e2e"
yaml_path="src/main/resources/pipelines/${slug}.yml"

mkdir -p "$pipeline_dir" "$transformer_dir" "$unit_test_dir" "$e2e_test_dir"

next_migration_number() {
  local latest
  latest="$(find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*__*.sql' \
    | sed 's#.*/V\([0-9]\+\)__.*#\1#' \
    | sort -n \
    | tail -n 1)"
  if [[ -z "${latest}" ]]; then
    echo "1"
  else
    echo $((latest + 1))
  fi
}

migration_number="$(next_migration_number)"
migration_path="src/main/resources/db/migration/V${migration_number}__create_${slug}_tables.sql"

export SCAFFOLD_SLUG="$slug"
export SCAFFOLD_PIPELINE_NAME="$pipeline_name"
export SCAFFOLD_TABLE_PREFIX="$table_prefix"
export SCAFFOLD_BUSINESS_KEY="$business_key"
export SCAFFOLD_CLASS_PREFIX="$class_prefix"
export SCAFFOLD_PIPELINE_UUID="$pipeline_uuid"
export SCAFFOLD_UPPER_SLUG="$upper_slug"
export SCAFFOLD_MIGRATION_PATH="$migration_path"

render_template() {
  local source_template="$1"
  local target_file="$2"
  envsubst < "$source_template" > "$target_file"
}

render_template "templates/csv-pipeline/PipelineConfig.java.tpl" \
  "${pipeline_dir}/${class_prefix}PipelineConfig.java"
render_template "templates/csv-pipeline/Transformer.java.tpl" \
  "${transformer_dir}/${class_prefix}Transformer.java"
render_template "templates/csv-pipeline/pipeline.yml.tpl" \
  "$yaml_path"
render_template "templates/csv-pipeline/migration.sql.tpl" \
  "$migration_path"
render_template "templates/csv-pipeline/TransformerTest.java.tpl" \
  "${unit_test_dir}/${class_prefix}TransformerTest.java"
render_template "templates/csv-pipeline/PipelineE2EIT.java.tpl" \
  "${e2e_test_dir}/${class_prefix}PipelineE2EIT.java"

cat <<EOF
Scaffold generado:

- ${pipeline_dir}/${class_prefix}PipelineConfig.java
- ${transformer_dir}/${class_prefix}Transformer.java
- ${yaml_path}
- ${migration_path}
- ${unit_test_dir}/${class_prefix}TransformerTest.java
- ${e2e_test_dir}/${class_prefix}PipelineE2EIT.java

Siguientes pasos:
1. Ajusta columnas de negocio en la migración.
2. Ajusta mandatory columns, tipos y mappings en ${yaml_path}.
3. Implementa reglas específicas en ${class_prefix}Transformer.
4. Edita el CSV fixture del E2E.
5. Agrega el mount o variable ORION_${upper_slug}_SOURCE_PATH en docker-compose si aplica.
6. Corre:
   mvn -q -Dtest=${class_prefix}TransformerTest test
   docker compose --profile integration-tests run --rm it-runner mvn -q test-compile -Dtest=none -DfailIfNoTests=false -Dit.test=${class_prefix}PipelineE2EIT failsafe:integration-test failsafe:verify
EOF
