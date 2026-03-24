# Flujo Para Crear Nuevos Pipelines CSV

## Objetivo

Este documento te enseña, paso por paso, cómo integrar un CSV nuevo al motor OrionETL.

El ejemplo real de este documento es:

- `/home/elyarestark/develop/datasets/archive/item_dim.csv`
- `/home/elyarestark/develop/datasets/archive/item_dim_utf8.csv`

La idea es que, si mañana te llega otro CSV, repitas exactamente este flujo.

---

## 1. Primero entiende el archivo

Antes de crear código, necesitas responder:

1. cómo se llama el pipeline
2. qué columnas trae el CSV
3. qué columnas quieres guardar en la tabla final
4. cuál es la llave de negocio
5. qué validaciones mínimas necesita
6. si necesitas transformación específica o solo normalización básica

Ejemplo `item_dim.csv`:

Columnas de entrada:

- `item_key`
- `item_name`
- `desc`
- `unit_price`
- `man_country`
- `supplier`
- `unit`

Columnas canónicas que queremos en el sistema:

- `item_key`
- `item_name`
- `description`
- `unit_price`
- `manufacturer_country`
- `supplier_name`
- `unit`

Aquí ya detectas mappings:

- `desc -> description`
- `man_country -> manufacturer_country`
- `supplier -> supplier_name`

---

## 2. Prueba primero solo la extracción

Antes del pipeline completo, valida cómo lee el CSV el `CsvExtractor`.

Ejemplo:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -v /home/elyarestark/develop/datasets/archive:/datasets/archive \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q spring-boot:run \
    -Dspring-boot.run.arguments="--spring.main.web-application-type=none,--orionetl.csv-preview.enabled=true,--orionetl.csv-preview.path=/datasets/archive/item_dim.csv,--orionetl.csv-preview.limit=10,--orionetl.csv-preview.null-values=,NULL,N/A,-,--orionetl.csv-preview.header-mapping.desc=description,--orionetl.csv-preview.header-mapping.man_country=manufacturer_country,--orionetl.csv-preview.header-mapping.supplier=supplier_name"
```

Qué debes confirmar:

- que el archivo se lea
- que los headers salgan con los nombres correctos
- que el delimitador sea correcto
- que los `null` se normalicen

Si aquí falla, todavía no pases al pipeline.

Nota importante del ejemplo `item_dim`:

- `item_dim.csv` no está en UTF-8 limpio
- por eso el pipeline productivo del ejemplo apunta a `item_dim_utf8.csv`
- si te pasa esto con otro archivo, tienes dos opciones:
  - corregir `encoding` en el YAML
  - generar un archivo convertido a UTF-8 y usar ese path

---

## 3. Crea la migración Flyway

Si el CSV va a una tabla nueva, primero diseña `staging` y `final`.

Para `item_dim.csv` se crearon:

- `etl_items_staging`
- `etl_items`

Archivo:

- [V12__create_item_tables.sql](/home/elyarestark/develop/OrionETL/src/main/resources/db/migration/V12__create_item_tables.sql)

Reglas que debes respetar:

1. ambas tablas deben tener las columnas del negocio
2. ambas tablas deben tener columnas ETL de trazabilidad

Columnas ETL mínimas:

- `etl_execution_id`
- `etl_pipeline_id`
- `etl_source_file`
- `etl_load_timestamp`
- `etl_pipeline_version`

Si no las pones, el loader actual falla.

---

## 4. Crea el YAML del pipeline

Archivo:

- `src/main/resources/pipelines/<nombre>.yml`

Para este ejemplo:

- [item.yml](/home/elyarestark/develop/OrionETL/src/main/resources/pipelines/item.yml)

La estructura correcta del engine es esta:

```yaml
pipeline:
  id: "uuid-fijo"
  name: "item-sync"
  version: "1.0.0"
  description: "..."
  status: ACTIVE

  source-config:
    ...

  target-config:
    ...

  validation-config:
    ...

  transformation-config:
    ...

  retry-policy:
    ...

  schedule-config:
    ...
```

Reglas importantes:

1. la raíz debe ser `pipeline:`
2. usa `source-config`, no `source`
3. usa `target-config`, no `target`
4. usa `validation-config`, no `validation`
5. el `id` debe ser estable, no generado en runtime

### Para CSV

En `source-config` necesitas:

- `type: CSV`
- `location`
- `delimiter`
- `encoding`
- `has-header`
- `connection-properties`

### Header mapping

En este proyecto, el `headerMapping` se pasa plano dentro de `connection-properties`:

```yaml
connection-properties:
  headerMapping.desc: "description"
  headerMapping.man_country: "manufacturer_country"
  headerMapping.supplier: "supplier_name"
```

No lo pongas como mapa anidado si quieres que el `CsvExtractor` actual lo entienda.

---

## 5. Crea la clase `*PipelineConfig`

Archivo:

- `src/main/java/com/elyares/etl/pipelines/<nombre>/<Nombre>PipelineConfig.java`

Para este ejemplo:

- [ItemPipelineConfig.java](/home/elyarestark/develop/OrionETL/src/main/java/com/elyares/etl/pipelines/item/ItemPipelineConfig.java)

Qué hace esta clase:

1. lee el YAML
2. construye el `Pipeline`
3. lo registra en `PipelineRepository`
4. deja el pipeline disponible para ejecución

Patrón que debes seguir siempre:

- cargar `YamlMapFactoryBean`
- parsear `source-config`
- parsear `target-config`
- parsear `transformation-config`
- parsear `validation-config`
- parsear `retry-policy`
- parsear `schedule-config`
- registrar con `registerIfMissing()`

Usa como plantilla:

- `SalesPipelineConfig`
- `InventoryPipelineConfig`
- `CustomerPipelineConfig`

Nunca inventes otro modelo distinto.

---

## 6. Crea el transformer específico

Archivo:

- `src/main/java/com/elyares/etl/infrastructure/transformer/<nombre>/<Nombre>Transformer.java`

Para este ejemplo:

- [ItemTransformer.java](/home/elyarestark/develop/OrionETL/src/main/java/com/elyares/etl/infrastructure/transformer/item/ItemTransformer.java)

Reglas importantes:

1. debe implementar `DataTransformer`
2. debe ser `@Component`
3. debe trabajar sobre `List<RawRecord>`
4. normalmente debe reutilizar `CommonTransformer`
5. debe regresar `TransformationResult`
6. debe implementar `getPipelineName()`

Flujo correcto:

1. `CommonTransformer` hace normalización base
2. tu transformer aplica reglas específicas del pipeline
3. si un registro falla, lo conviertes a `RejectedRecord`

En `item-sync`, las reglas específicas son:

- `item_key` en mayúsculas
- `item_name` limpio
- `unit_price` como `BigDecimal`
- `manufacturer_country` en title case
- `supplier_name` en title case
- `unit` en minúsculas

---

## 7. Asegura la ruta del archivo dentro de Docker

El pipeline no usa la ruta de tu host directamente. Usa la ruta que existe dentro del contenedor.

Para `item-sync`, la ruta elegida es:

- `/datasets/archive/item_dim.csv`

Por eso en `docker-compose.yml` se montó:

- `${ORION_DATASETS_HOST_PATH:-/home/elyarestark/develop/datasets/archive}:/datasets/archive:ro`

Y se configuró:

- `ORION_ITEM_SOURCE_PATH=/datasets/archive/item_dim.csv`

Regla práctica:

1. decide una ruta interna del contenedor
2. monta el directorio del host hacia esa ruta
3. usa esa ruta interna en el YAML

---

## 8. Crea pruebas

Siempre necesitas al menos:

### Unit test del transformer

Archivo:

- [ItemTransformerTest.java](/home/elyarestark/develop/OrionETL/src/test/java/com/elyares/etl/unit/transformer/ItemTransformerTest.java)

Qué valida:

- normalización esperada
- rechazo cuando una regla específica falla

### E2E del pipeline

Archivo:

- [ItemPipelineE2EIT.java](/home/elyarestark/develop/OrionETL/src/test/java/com/elyares/etl/e2e/ItemPipelineE2EIT.java)

Qué valida:

- lectura del CSV
- validación
- transformación
- carga a staging
- promoción a final
- rechazados

Regla del proyecto:

- si agregas un pipeline real, deja al menos un E2E

---

## 9. Cómo correr el pipeline nuevo

### Levantar stack

```bash
docker compose up -d --build
```

### Ver si el pipeline ya existe

```bash
curl http://localhost:8080/api/v1/pipelines
```

Debes ver `item-sync`.

### Ejecutarlo

```bash
curl -X POST http://localhost:8080/api/v1/pipelines/item-sync/execute \
  -H "Content-Type: application/json" \
  -d '{"triggeredBy":"manual:item-test"}'
```

### Ver estado

```bash
curl http://localhost:8080/api/v1/executions/<executionId>
```

### Ver rechazados

```bash
curl "http://localhost:8080/api/v1/executions/<executionId>/rejected?page=0&size=50"
```

### Ver tabla final

```bash
docker compose exec db psql -U orionetl -d orionetl -c "select * from etl_items limit 10;"
```

---

## 10. Checklist reusable para cualquier CSV nuevo

Cada vez que metas un nuevo CSV, repite esto:

1. inspeccionar columnas reales
2. probar preview del extractor
3. diseñar tabla `staging`
4. diseñar tabla final
5. crear migración Flyway
6. crear YAML del pipeline
7. crear `*PipelineConfig`
8. crear transformer específico si aplica
9. ajustar ruta Docker
10. crear unit test
11. crear E2E
12. levantar stack
13. ejecutar el pipeline
14. revisar final + rejected + audit

---

## 11. Errores comunes que debes evitar

1. usar un modelo viejo en `PipelineConfig`
   Usa siempre el patrón real de `sales/inventory/customer`.

2. hacer un transformer que vuelva a leer el CSV
   No se hace así. El extractor ya entrega `RawRecord`.

3. olvidar columnas ETL en la tabla
   El loader actual las inserta siempre.

4. usar una ruta del host dentro del YAML
   El YAML debe usar la ruta interna del contenedor.

5. dejar `headerMapping` como mapa anidado incorrecto
   En este proyecto actual va plano dentro de `connection-properties`.

6. no dejar E2E
   Sin E2E no sabes si el pipeline carga realmente.

---

## 12. Resumen corto

Para crear un pipeline CSV nuevo en OrionETL necesitas:

- migración
- YAML
- `PipelineConfig`
- transformer
- ruta Docker correcta
- unit test
- E2E

El ejemplo completo para copiar hoy es:

- `item-sync`

---

## 13. Scaffold por comando

Si no quieres crear todo a mano, ya existe un comando base que te genera el esqueleto:

```bash
scripts/new-csv-pipeline.sh <slug> <pipeline-name> <table-prefix> <business-key>
```

Ejemplo:

```bash
scripts/new-csv-pipeline.sh payments payments-sync etl_payments payment_key
```

Qué te genera:

- migración Flyway
- YAML del pipeline
- clase `*PipelineConfig`
- transformer
- unit test del transformer
- E2E del pipeline

Salida esperada:

- `src/main/java/com/elyares/etl/pipelines/payments/PaymentsPipelineConfig.java`
- `src/main/java/com/elyares/etl/infrastructure/transformer/payments/PaymentsTransformer.java`
- `src/main/resources/pipelines/payments.yml`
- `src/main/resources/db/migration/V<next>__create_payments_tables.sql`
- `src/test/java/com/elyares/etl/unit/transformer/PaymentsTransformerTest.java`
- `src/test/java/com/elyares/etl/e2e/PaymentsPipelineE2EIT.java`

Lo importante:

- el comando no “termina” el pipeline por ti
- te deja la estructura correcta del engine para que solo rellenes columnas,
  tablas, reglas y fixtures
