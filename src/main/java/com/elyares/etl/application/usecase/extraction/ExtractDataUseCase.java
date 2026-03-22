package com.elyares.etl.application.usecase.extraction;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.shared.exception.ExtractionException;

import java.util.List;

/**
 * Caso de uso para extraer datos desde la fuente configurada en el pipeline.
 */
public class ExtractDataUseCase {

    private final List<DataExtractor> extractors;

    public ExtractDataUseCase(List<DataExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractionResult execute(Pipeline pipeline, PipelineExecution execution) {
        return extractors.stream()
            .filter(extractor -> extractor.supports(pipeline.getSourceConfig().getType()))
            .findFirst()
            .map(extractor -> extractor.extract(pipeline.getSourceConfig(), execution))
            .orElseThrow(() -> new ExtractionException(
                "No extractor registered for source type: " + pipeline.getSourceConfig().getType()
            ));
    }
}
