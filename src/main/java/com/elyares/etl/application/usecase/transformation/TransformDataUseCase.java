package com.elyares.etl.application.usecase.transformation;

import com.elyares.etl.domain.contract.DataTransformer;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.target.ProcessedRecord;
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

    public List<ProcessedRecord> execute(List<RawRecord> records,
                                         Pipeline pipeline,
                                         PipelineExecution execution) {
        return transformers.stream()
            .filter(transformer -> transformer.getPipelineName().equalsIgnoreCase(pipeline.getName()))
            .findFirst()
            .map(transformer -> transformer.transform(records, execution))
            .orElseThrow(() -> new TransformationException(
                "No transformer registered for pipeline: " + pipeline.getName()
            ));
    }
}
