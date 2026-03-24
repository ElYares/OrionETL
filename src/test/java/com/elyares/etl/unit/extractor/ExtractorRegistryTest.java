package com.elyares.etl.unit.extractor;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.infrastructure.extractor.ExtractorRegistry;
import com.elyares.etl.shared.exception.ExtractionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtractorRegistryTest {

    @Test
    void shouldResolveFirstSupportingExtractor() {
        DataExtractor csvExtractor = mock(DataExtractor.class);
        DataExtractor apiExtractor = mock(DataExtractor.class);

        when(csvExtractor.supports(SourceType.CSV)).thenReturn(true);
        when(apiExtractor.supports(SourceType.CSV)).thenReturn(false);

        ExtractorRegistry registry = new ExtractorRegistry(List.of(csvExtractor, apiExtractor));

        assertThat(registry.resolve(SourceType.CSV)).isSameAs(csvExtractor);
    }

    @Test
    void shouldThrowWhenNoExtractorSupportsSourceType() {
        DataExtractor apiExtractor = mock(DataExtractor.class);
        when(apiExtractor.supports(SourceType.CSV)).thenReturn(false);

        ExtractorRegistry registry = new ExtractorRegistry(List.of(apiExtractor));

        assertThatThrownBy(() -> registry.resolve(SourceType.CSV))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("No extractor registered");
    }
}
