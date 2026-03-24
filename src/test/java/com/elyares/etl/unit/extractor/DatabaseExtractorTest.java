package com.elyares.etl.unit.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.database.DatabaseExtractor;
import com.elyares.etl.shared.exception.ExtractionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseExtractorTest {

    @Test
    void shouldThrowWhenQueryIsMissing() {
        DatabaseExtractor extractor = new DatabaseExtractor();
        SourceConfig config = new SourceConfig(
            SourceType.DATABASE,
            "jdbc:postgresql://localhost:5432/orionetl",
            "UTF-8",
            ',',
            false,
            Map.of("username", "orionetl", "password", "orionetl")
        );

        assertThatThrownBy(() -> extractor.extract(config, null))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("Missing required database property: query");
    }

    @Test
    void supportsShouldReturnTrueOnlyForDatabase() {
        DatabaseExtractor extractor = new DatabaseExtractor();

        assertThat(extractor.supports(SourceType.DATABASE)).isTrue();
        assertThat(extractor.supports(SourceType.CSV)).isFalse();
    }
}
