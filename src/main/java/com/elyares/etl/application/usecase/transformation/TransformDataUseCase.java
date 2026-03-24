package com.elyares.etl.application.usecase.transformation;

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.transformation.TransformationResult;
import com.elyares.etl.shared.exception.TransformationException;

import java.util.List;

/**
 * Caso de uso para transformar registros crudos a registros procesados.
 */
public class TransformDataUseCase {

    private final List<DataTransformer> transformers;

    public TransformDataUseCase(List<DataTransformer> transformers) {
        this.transformers = transformers;
    }

    public TransformationResult execute(List<RawRecord> records,
                                        Pipeline pipeline,
                                        PipelineExecution execution) {
        List<DataTransformer> exactMatches = transformers.stream()
            .filter(transformer -> transformer.getPipelineName().equalsIgnoreCase(pipeline.getName())
                || transformer.getPipelineName().equalsIgnoreCase(pipeline.getId().toString()))
            .toList();

        List<DataTransformer> candidates = !exactMatches.isEmpty()
            ? exactMatches
            : transformers.stream()
                .filter(transformer -> transformer.getPipelineName().equalsIgnoreCase("*")
                    || transformer.getPipelineName().equalsIgnoreCase("common"))
                .toList();

        return candidates.stream()
            .sorted(java.util.Comparator.comparingInt(DataTransformer::getOrder))
            .findFirst()
            .map(transformer -> transformer.transform(records, pipeline, execution))
            .orElseThrow(() -> new TransformationException(
                "No transformer registered for pipeline: " + pipeline.getName()
            ));
    }
}
