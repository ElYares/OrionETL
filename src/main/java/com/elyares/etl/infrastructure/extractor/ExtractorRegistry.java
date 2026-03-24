package com.elyares.etl.infrastructure.extractor;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.shared.exception.ExtractionException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registro central para resolver el extractor apropiado según el tipo de origen.
 */
@Component
public class ExtractorRegistry {

    private final List<DataExtractor> extractors;

    public ExtractorRegistry(List<DataExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public DataExtractor resolve(SourceType sourceType) {
        return extractors.stream()
            .filter(extractor -> extractor.supports(sourceType))
            .findFirst()
            .orElseThrow(() -> new ExtractionException(
                "No extractor registered for source type: " + sourceType
            ));
    }
}
