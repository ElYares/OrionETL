package com.elyares.etl.infrastructure.transformer.${SCAFFOLD_SLUG};

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.domain.model.validation.RejectedRecord;
import com.elyares.etl.domain.model.validation.ValidationError;
import com.elyares.etl.infrastructure.transformer.CommonTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plantilla base para un transformador específico de pipeline.
 *
 * Qué debes cambiar:
 * - las columnas de negocio dentro de apply${SCAFFOLD_CLASS_PREFIX}Rules
 * - las reglas específicas de validación/normalización
 * - los mensajes de error del pipeline
 */
@Component
public class ${SCAFFOLD_CLASS_PREFIX}Transformer implements DataTransformer {

    private final CommonTransformer commonTransformer;

    public ${SCAFFOLD_CLASS_PREFIX}Transformer(CommonTransformer commonTransformer) {
        this.commonTransformer = commonTransformer;
    }

    @Override
    public TransformationResult transform(List<RawRecord> records, Pipeline pipeline, PipelineExecution execution) {
        CommonTransformer.CommonTransformationOutput commonOutput = commonTransformer.transform(records, pipeline);
        Map<Long, RawRecord> rawByRow = records.stream()
            .collect(Collectors.toMap(RawRecord::getRowNumber, Function.identity(), (left, right) -> left));

        List<ProcessedRecord> processed = new ArrayList<>();
        List<RejectedRecord> rejected = new ArrayList<>(commonOutput.rejectedRecords());

        for (ProcessedRecord record : commonOutput.processedRecords()) {
            try {
                processed.add(apply${SCAFFOLD_CLASS_PREFIX}Rules(record));
            } catch (IllegalArgumentException ex) {
                RawRecord original = rawByRow.getOrDefault(
                    record.getSourceRowNumber(),
                    new RawRecord(record.getSourceRowNumber(), record.getData(), record.getSourceReference(), record.getTransformedAt())
                );
                rejected.add(new RejectedRecord(
                    original,
                    "TRANSFORM",
                    ex.getMessage(),
                    List.of(ValidationError.critical(null, original.getData(), "${SCAFFOLD_UPPER_SLUG}_TRANSFORM_ERROR", ex.getMessage()))
                ));
            }
        }

        return TransformationResult.of(processed, rejected);
    }

    private ProcessedRecord apply${SCAFFOLD_CLASS_PREFIX}Rules(ProcessedRecord record) {
        // Copia mutable del mapa normalizado por CommonTransformer.
        Map<String, Object> data = new LinkedHashMap<>(record.getData());

        // TODO: reemplaza estas columnas por las del pipeline nuevo.
        // Ejemplo de patrón:
        // String businessKey = requireText(data.get("${SCAFFOLD_BUSINESS_KEY}"), "${SCAFFOLD_BUSINESS_KEY}");
        // data.put("${SCAFFOLD_BUSINESS_KEY}", businessKey.toUpperCase());

        return new ProcessedRecord(
            record.getSourceRowNumber(),
            data,
            record.getPipelineVersion(),
            record.getSourceReference(),
            record.getTransformedAt()
        );
    }

    @Override
    public String getPipelineName() {
        return "${SCAFFOLD_PIPELINE_NAME}";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
