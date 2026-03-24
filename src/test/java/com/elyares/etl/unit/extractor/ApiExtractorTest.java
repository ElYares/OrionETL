package com.elyares.etl.unit.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.api.ApiExtractor;
import com.elyares.etl.shared.exception.ExtractionException;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiExtractorTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldExtractAllPagesUsingCursorPagination() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/customers", exchange -> {
            requests.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("cursor=")) {
                respond(exchange, 200, """
                    {"customers":[{"customer_id":"C001"},{"customer_id":"C002"}],"meta":{"next_cursor":"page-2"}}
                    """);
                return;
            }
            respond(exchange, 200, """
                {"customers":[{"customer_id":"C003"}],"meta":{"next_cursor":null}}
                """);
        });
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
                "responseArrayPath", "customers",
                "paginationType", "CURSOR",
                "cursorField", "meta.next_cursor",
                "pageSize", "50"
            )
        );

        var result = extractor.extract(config, null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(3);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(result.getRecords().get(2).getData().get("customer_id")).isEqualTo("C003");
    }

    @Test
    void shouldRetryOn503AndSucceed() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/customers", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, """
                    {"error":"temporary outage"}
                    """);
                return;
            }
            respond(exchange, 200, """
                {"customers":[{"customer_id":"C010"}]}
                """);
        });
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
                "responseArrayPath", "customers",
                "maxRetries", "2",
                "timeoutMs", "5000"
            )
        );

        var result = extractor.extract(config, null);

        assertThat(result.getTotalRead()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldWrapUnauthorizedHttpErrors() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/customers", exchange -> respond(exchange, 401, """
            {"error":"unauthorized"}
            """));
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

        assertThatThrownBy(() -> extractor.extract(config, null))
            .isInstanceOf(ExtractionException.class)
            .hasMessageContaining("HTTP status 401");
    }

    @Test
    void supportsShouldReturnTrueOnlyForApi() {
        ApiExtractor extractor = new ApiExtractor(WebClient.builder(), new ObjectMapper());

        assertThat(extractor.supports(SourceType.API)).isTrue();
        assertThat(extractor.supports(SourceType.CSV)).isFalse();
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
