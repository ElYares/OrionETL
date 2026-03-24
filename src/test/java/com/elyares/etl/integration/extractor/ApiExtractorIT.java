package com.elyares.etl.integration.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.api.ApiExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExtractorIT {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReadFixtureResponseJson() throws IOException {
        Path fixture = Path.of("src/test/resources/fixtures/api_customers_response.json");
        String payload = Files.readString(fixture);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/customers", exchange -> respond(exchange, 200, payload));
        server.start();

        ApiExtractor extractor = new ApiExtractor(WebClient.builder(), new ObjectMapper());
        SourceConfig config = new SourceConfig(
            SourceType.API,
            "http://localhost:" + server.getAddress().getPort() + "/customers",
            "UTF-8",
            ',',
            false,
            Map.of(
                "method", "GET",
                "responseArrayPath", "customers"
            )
        );

        var result = extractor.extract(config, null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(3);
        assertThat(result.getRecords().getFirst().getData()).containsKeys(
            "customer_id", "first_name", "last_name", "status"
        );
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
