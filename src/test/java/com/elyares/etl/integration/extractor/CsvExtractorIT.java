package com.elyares.etl.integration.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.csv.CsvExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CsvExtractorIT {

    @Test
    void shouldExtractHundredRowsFromSalesSampleFixture() {
        Path fixture = Path.of("src/test/resources/fixtures/sales_sample.csv");
        assumeTrue(Files.exists(fixture), "sales_sample fixture not found");

        SourceConfig config = new SourceConfig(
            SourceType.CSV,
            fixture.toString(),
            "UTF-8",
            ',',
            true,
            Map.of()
        );

        CsvExtractor extractor = new CsvExtractor();
        var result = extractor.extract(config, null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(100);
        assertThat(result.getRecords().getFirst().getRowNumber()).isEqualTo(2);
        assertThat(result.getRecords().getFirst().getData()).containsKeys(
            "payment_key", "coustomer_key", "quantity", "unit_price", "total_price"
        );
    }

    @Test
    void shouldExtractFromKaggleCsvWhenDatasetArchiveExists() throws IOException {
        String archivePath = System.getenv().getOrDefault(
            "ORION_DATASETS_ARCHIVE",
            "/home/elyarestark/develop/datasets/archive"
        );

        Path kaggleCsv = Path.of(archivePath, "Trans_dim.csv");
        assumeTrue(Files.exists(kaggleCsv), "Kaggle dataset CSV not found: " + kaggleCsv);

        SourceConfig config = new SourceConfig(
            SourceType.CSV,
            kaggleCsv.toString(),
            "UTF-8",
            ',',
            true,
            Map.of()
        );

        CsvExtractor extractor = new CsvExtractor();
        var result = extractor.extract(config, null);

        long expectedRows;
        try (var lines = Files.lines(kaggleCsv)) {
            expectedRows = lines.count() - 1;
        }

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(expectedRows);
        assertThat(result.getRecords().getFirst().getData()).containsKeys(
            "payment_key", "trans_type", "bank_name"
        );
    }
}
