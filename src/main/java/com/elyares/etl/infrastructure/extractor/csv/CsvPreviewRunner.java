package com.elyares.etl.infrastructure.extractor.csv;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.shared.util.JsonUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runner opcional para imprimir una muestra de registros extraidos desde CSV.
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "orionetl.csv-preview", name = "enabled", havingValue = "true")
public class CsvPreviewRunner implements ApplicationRunner {

    private final CsvExtractor csvExtractor;
    private final Environment environment;

    public CsvPreviewRunner(CsvExtractor csvExtractor, Environment environment) {
        this.csvExtractor = csvExtractor;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        int limit = Integer.parseInt(environment.getProperty("orionetl.csv-preview.limit", "10"));
        Map<String, String> properties = new LinkedHashMap<>();

        String nullValues = environment.getProperty("orionetl.csv-preview.null-values");
        if (nullValues != null) {
            properties.put("nullValues", nullValues);
        }

        String quoteChar = environment.getProperty("orionetl.csv-preview.quote-char");
        if (quoteChar != null) {
            properties.put("quoteChar", quoteChar);
        }

        for (String optionName : args.getOptionNames()) {
            String prefix = "orionetl.csv-preview.header-mapping.";
            if (optionName.startsWith(prefix)) {
                String sourceHeader = optionName.substring(prefix.length());
                String targetHeader = environment.getProperty(optionName);
                if (targetHeader != null && !targetHeader.isBlank()) {
                    properties.put("headerMapping." + sourceHeader, targetHeader);
                }
            }
        }

        SourceConfig sourceConfig = new SourceConfig(
            SourceType.CSV,
            environment.getProperty("orionetl.csv-preview.path"),
            environment.getProperty("orionetl.csv-preview.encoding", "UTF-8"),
            environment.getProperty("orionetl.csv-preview.delimiter", ",").charAt(0),
            Boolean.parseBoolean(environment.getProperty("orionetl.csv-preview.has-header", "true")),
            properties
        );

        var result = csvExtractor.extract(sourceConfig, null);
        System.out.println("csv_preview_total_read=" + result.getTotalRead());
        result.getRecords().stream()
            .limit(limit)
            .forEach(record -> System.out.println(
                "row=" + record.getRowNumber() + " data=" + JsonUtils.toJson(record.getData())
            ));
    }
}
