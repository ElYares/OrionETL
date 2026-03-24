package com.elyares.etl.unit.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.csv.CsvExtractor;
import com.elyares.etl.shared.exception.ExtractionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractCsvWithHeaderMappingAndNullNormalization() throws IOException {
        Path csvFile = tempDir.resolve("sample.csv");
        Files.writeString(csvFile, String.join(System.lineSeparator(),
            "TransactionID,CustomerID,notes,amount",
            "TX-001,CUST-01,,100.00",
            "TX-002,CUST-02,NULL,250.00",
            "\"TX,003\",CUST-03,\"hello, world\",50.00"
        ));

        SourceConfig config = new SourceConfig(
            SourceType.CSV,
            csvFile.toString(),
            "UTF-8",
            ',',
            true,
            Map.of(
                "headerMapping.TransactionID", "transaction_id",
                "headerMapping.CustomerID", "customer_id",
                "nullValues", ",NULL,N/A,-",
                "quoteChar", "\""
            )
        );

        CsvExtractor extractor = new CsvExtractor();
        var result = extractor.extract(config, null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(3);
        assertThat(result.getRecords().getFirst().getRowNumber()).isEqualTo(2);
        assertThat(result.getRecords().get(1).getData().get("notes")).isNull();
        assertThat(result.getRecords().get(2).getData().get("transaction_id")).isEqualTo("TX,003");
        assertThat(result.getRecords().get(2).getData().get("notes")).isEqualTo("hello, world");
    }

    @Test
    void shouldThrowWhenCsvFileDoesNotExist() {
        SourceConfig config = new SourceConfig(
            SourceType.CSV,
            tempDir.resolve("missing.csv").toString(),
            "UTF-8",
            ',',
            true,
            Map.of()
        );

        CsvExtractor extractor = new CsvExtractor();

        assertThatThrownBy(() -> extractor.extract(config, null))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void supportsShouldReturnTrueOnlyForCsv() {
        CsvExtractor extractor = new CsvExtractor();

        assertThat(extractor.supports(SourceType.CSV)).isTrue();
        assertThat(extractor.supports(SourceType.API)).isFalse();
    }
}
