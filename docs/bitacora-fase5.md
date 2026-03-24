# Bitácora Fase 5

Fecha de actualización: `2026-03-23`

## Objetivo

Implementar la infraestructura base de transformación y validación:

- `CommonTransformer`
- `TransformerChain`
- `SchemaValidator`
- `BusinessValidator`
- `QualityValidator`
- coordinador de validación para casos de uso

## Qué se implementó

### 1) Modelo de transformación

Se agregó configuración explícita de transformación y resultado agregado:

- `src/main/java/com/elyares/etl/domain/model/transformation/TransformationConfig.java`
- `src/main/java/com/elyares/etl/domain/model/transformation/TransformationResult.java`

También se actualizó `Pipeline` para incluir `transformationConfig`:

- `src/main/java/com/elyares/etl/domain/model/pipeline/Pipeline.java`

### 2) Contrato de transformación

`DataTransformer` dejó de regresar solo `List<ProcessedRecord>` y ahora regresa `TransformationResult`, para poder acumular:

- registros transformados
- registros rechazados durante `TRANSFORM`

Archivo:

- `src/main/java/com/elyares/etl/domain/contract/DataTransformer.java`

### 3) CommonTransformer + TransformerChain

Se implementó:

- `src/main/java/com/elyares/etl/infrastructure/transformer/CommonTransformer.java`
- `src/main/java/com/elyares/etl/infrastructure/transformer/TransformerChain.java`

Reglas cubiertas en esta fase:

- normalización de nombres de columnas a `snake_case`
- trim de strings
- normalización de nulls configurables
- conversión de fechas a UTC
- code mapping
- conversión monetaria con `BigDecimal`
- columnas derivadas con fórmulas aritméticas simples
- redondeo monetario configurable

### 4) Validadores

Se implementó:

- `src/main/java/com/elyares/etl/infrastructure/validator/SchemaValidator.java`
- `src/main/java/com/elyares/etl/infrastructure/validator/BusinessValidator.java`
- `src/main/java/com/elyares/etl/infrastructure/validator/QualityValidator.java`

Cobertura funcional:

- columnas obligatorias ausentes a nivel dataset
- mandatory field null/empty
- compatibilidad de tipos
- regex por columna
- amounts negativos
- rangos
- future dates
- catálogos configurados
- referencias activas configuradas
- unicidad de business key dentro del batch
- cálculo de `DataQualityReport`

### 5) Cadena de validación

Se agregó:

- `src/main/java/com/elyares/etl/application/usecase/validation/ValidationChainExecutor.java`

Y se actualizaron:

- `src/main/java/com/elyares/etl/application/usecase/validation/ValidateInputDataUseCase.java`
- `src/main/java/com/elyares/etl/application/usecase/validation/ValidateBusinessDataUseCase.java`

Ahora ambos casos de uso:

1. llaman al validador correspondiente
2. enriquecen el resultado con `QualityValidator`

### 6) Ajuste del orquestador

Se actualizó el flujo para trabajar con resultados agregados de validación y transformación:

- `src/main/java/com/elyares/etl/application/orchestrator/ETLOrchestrator.java`
- `src/main/java/com/elyares/etl/application/orchestrator/OrchestrationContext.java`

Cambios importantes:

- `VALIDATE_SCHEMA` ya filtra `validRecords`
- `TRANSFORM` ya puede devolver rechazados
- `VALIDATE_BUSINESS` ya acumula rechazados reales
- `AUDIT` conserva el acumulado de `RejectedRecord`

### 7) Wiring Spring

Se agregó configuración explícita para exponer los casos de uso de Fase 5:

- `src/main/java/com/elyares/etl/infrastructure/config/Phase5UseCaseConfig.java`

### 8) Persistencia de pipeline

`PipelineRepositoryAdapter` ahora serializa/deserializa también:

- `transformationConfig`
- configuración extendida de validación

Archivo:

- `src/main/java/com/elyares/etl/infrastructure/persistence/adapter/PipelineRepositoryAdapter.java`

## Pruebas agregadas

Nuevas pruebas unitarias:

- `src/test/java/com/elyares/etl/unit/transformer/CommonTransformerTest.java`
- `src/test/java/com/elyares/etl/unit/validator/SchemaValidatorTest.java`
- `src/test/java/com/elyares/etl/unit/validator/BusinessValidatorTest.java`
- `src/test/java/com/elyares/etl/unit/validator/QualityValidatorTest.java`

También se adaptaron pruebas existentes de:

- use cases
- orchestrator
- persistence adapters

## Evidencia de ejecución

Comando ejecutado en Docker:

```bash
docker run --rm \
  -v "$PWD":/workspace \
  -w /workspace \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -q test
```

Resultado:

- `tests=100`
- `failures=0`
- `errors=0`
- `skipped=0`

Reportes:

- `target/surefire-reports/`

## Qué ya puedes hacer al terminar Fase 5

- extraer datos desde CSV y API
- validar estructura del batch extraído
- transformar registros con reglas comunes
- rechazar registros por transformación/validación
- calcular calidad de datos por lote
- orquestar en memoria extracción -> validación -> transformación -> validación de negocio

## Qué todavía falta

Fase 5 deja listo el bloque de transformación/validación, pero todavía faltan piezas para flujo productivo completo:

- Fase 6: loaders reales a staging/final
- Fase 7: primer pipeline end-to-end real
- interfaces REST/monitoring de fases posteriores

## Cierre de fase

Estado: `implementada y validada a nivel unitario`

Pendiente para cierre funcional completo del sistema:

- loaders reales
- pipeline end-to-end
- ejecución completa contra destino final
