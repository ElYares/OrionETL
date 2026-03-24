package com.elyares.etl.application.usecase.extraction;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.infrastructure.extractor.ExtractorRegistry;

import java.util.List;

/**
 * Caso de uso para extraer datos desde la fuente configurada en el pipeline.
 */
public class ExtractDataUseCase {

    private final ExtractorRegistry extractorRegistry;

    public ExtractDataUseCase(ExtractorRegistry extractorRegistry) {
        this.extractorRegistry = extractorRegistry;
    }

    public ExtractDataUseCase(List<DataExtractor> extractors) {
        this.extractorRegistry = new ExtractorRegistry(extractors);
    }

    public ExtractionResult execute(Pipeline pipeline, PipelineExecution execution) {
        DataExtractor extractor = extractorRegistry.resolve(pipeline.getSourceConfig().getType());
        return extractor.extract(pipeline.getSourceConfig(), execution);
    }
}
