package com.elyares.etl.infrastructure.extractor.api;

import com.elyares.etl.domain.contract.DataExtractor;
import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.execution.PipelineExecution;
import com.elyares.etl.domain.model.source.ExtractionResult;
import com.elyares.etl.domain.model.source.RawRecord;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.shared.exception.ExtractionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor HTTP/REST síncrono para fuentes API en V1.
 */
@Component
public class ApiExtractor implements DataExtractor {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ApiExtractor(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExtractionResult extract(SourceConfig sourceConfig, PipelineExecution execution) {
        Map<String, String> properties = sourceConfig.getConnectionProperties();
        HttpMethod method = resolveHttpMethod(properties);
        String responseArrayPath = requireProperty(properties, "responseArrayPath");
        String paginationType = properties.getOrDefault("paginationType", "NONE");
        String cursorField = properties.getOrDefault("cursorField", "meta.next_cursor");
        int pageSize = parseInt(properties.get("pageSize"), 200);
        int maxRetries = parseInt(properties.get("maxRetries"), 2);
        Duration timeout = Duration.ofMillis(parseLong(properties.get("timeoutMs"), 30_000L));

        WebClient client = buildClient(sourceConfig.getLocation(), properties, timeout);
        List<RawRecord> records = new ArrayList<>();
        long rowNumber = 1L;

        try {
            if ("CURSOR".equalsIgnoreCase(paginationType)) {
                String cursor = null;
                do {
                    JsonNode root = fetchResponse(client, method, sourceConfig.getLocation(), properties, timeout, maxRetries, cursor, null, pageSize);
                    for (JsonNode item : extractArray(root, responseArrayPath)) {
                        records.add(new RawRecord(
                            rowNumber++,
                            objectMapper.convertValue(item, new TypeReference<>() {}),
                            sourceConfig.getLocation(),
                            Instant.now()
                        ));
                    }
                    cursor = extractText(root, cursorField);
                } while (cursor != null && !cursor.isBlank());
            } else if ("OFFSET".equalsIgnoreCase(paginationType)) {
                String pageParam = properties.getOrDefault("pageParam", "page");
                int page = parseInt(properties.get("pageStart"), 0);
                while (true) {
                    JsonNode root = fetchResponse(client, method, sourceConfig.getLocation(), properties, timeout, maxRetries, null, Map.of(pageParam, String.valueOf(page)), pageSize);
                    JsonNode arrayNode = extractArray(root, responseArrayPath);
                    if (arrayNode.isEmpty()) {
                        break;
                    }
                    for (JsonNode item : arrayNode) {
                        records.add(new RawRecord(
                            rowNumber++,
                            objectMapper.convertValue(item, new TypeReference<>() {}),
                            sourceConfig.getLocation(),
                            Instant.now()
                        ));
                    }
                    page++;
                }
            } else {
                JsonNode root = fetchResponse(client, method, sourceConfig.getLocation(), properties, timeout, maxRetries, null, null, pageSize);
                for (JsonNode item : extractArray(root, responseArrayPath)) {
                    records.add(new RawRecord(
                        rowNumber++,
                        objectMapper.convertValue(item, new TypeReference<>() {}),
                        sourceConfig.getLocation(),
                        Instant.now()
                    ));
                }
            }
        } catch (WebClientResponseException e) {
            throw new ExtractionException("API extraction failed with HTTP status " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            if (e instanceof ExtractionException extractionException) {
                throw extractionException;
            }
            throw new ExtractionException("API extraction failed for source: " + sourceConfig.getLocation(), e);
        }

        return ExtractionResult.success(records, sourceConfig.getLocation());
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return SourceType.API == sourceType;
    }

    private WebClient buildClient(String baseUrl, Map<String, String> properties, Duration timeout) {
        HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
        WebClient.Builder builder = webClientBuilder.clone()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        applyAuth(builder, properties);
        return builder.build();
    }

    private void applyAuth(WebClient.Builder builder, Map<String, String> properties) {
        String authType = properties.getOrDefault("authType", "NONE");
        switch (authType.toUpperCase()) {
            case "BEARER" -> {
                String token = resolveSecret(properties, "authToken", "authTokenEnv");
                if (token == null || token.isBlank()) {
                    throw new ExtractionException("authType=BEARER requires authToken or authTokenEnv");
                }
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            case "BASIC" -> {
                String username = resolveSecret(properties, "authUsername", "authUsernameEnv");
                String password = resolveSecret(properties, "authPassword", "authPasswordEnv");
                if (username == null || password == null) {
                    throw new ExtractionException("authType=BASIC requires username/password credentials");
                }
                String encoded = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            case "API_KEY" -> {
                String headerName = requireProperty(properties, "apiKeyHeader");
                String keyValue = resolveSecret(properties, "apiKeyValue", "apiKeyValueEnv");
                if (keyValue == null || keyValue.isBlank()) {
                    throw new ExtractionException("authType=API_KEY requires apiKeyValue or apiKeyValueEnv");
                }
                builder.defaultHeader(headerName, keyValue);
            }
            case "NONE" -> {
                return;
            }
            default -> throw new ExtractionException("Unsupported API auth type: " + authType);
        }
    }

    private JsonNode fetchResponse(WebClient client, HttpMethod method, String baseUrl, Map<String, String> properties,
                                   Duration timeout, int maxRetries, String cursor, Map<String, String> extraQueryParams,
                                   int pageSize) {
        WebClient.RequestBodySpec request = client.method(method)
            .uri(uriBuilder -> {
                if (pageSize > 0) {
                    uriBuilder.queryParam(properties.getOrDefault("pageSizeParam", "size"), pageSize);
                }
                String filterParam = properties.get("filterParam");
                String filterValue = properties.get("filterValue");
                if (filterParam != null && filterValue != null) {
                    uriBuilder.queryParam(filterParam, filterValue);
                }
                if (cursor != null && !cursor.isBlank()) {
                    uriBuilder.queryParam(properties.getOrDefault("cursorParam", "cursor"), cursor);
                }
                if (extraQueryParams != null) {
                    extraQueryParams.forEach(uriBuilder::queryParam);
                }
                return uriBuilder.build();
            });

        WebClient.RequestHeadersSpec<?> headersSpec = request;
        if (HttpMethod.POST.equals(method) && properties.containsKey("requestBody")) {
            headersSpec = request
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(properties.get("requestBody")));
        }

        return headersSpec.retrieve()
            .bodyToMono(String.class)
            .timeout(timeout)
            .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(200))
                .filter(this::isRetryableServerError))
            .map(this::readJson)
            .block();
    }

    private boolean isRetryableServerError(Throwable throwable) {
        return throwable instanceof WebClientResponseException responseException
            && responseException.getStatusCode().is5xxServerError();
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ExtractionException("API response is not valid JSON", e);
        }
    }

    private JsonNode extractArray(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                break;
            }
            current = current.path(part);
        }
        if (current == null || !current.isArray()) {
            throw new ExtractionException("responseArrayPath does not point to a JSON array: " + path);
        }
        return current;
    }

    private String extractText(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            current = current.path(part);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.asText();
    }

    private HttpMethod resolveHttpMethod(Map<String, String> properties) {
        return HttpMethod.valueOf(properties.getOrDefault("method", "GET").toUpperCase());
    }

    private String resolveSecret(Map<String, String> properties, String literalKey, String envKey) {
        String literalValue = properties.get(literalKey);
        if (literalValue != null && !literalValue.isBlank()) {
            return literalValue;
        }
        String envName = properties.get(envKey);
        if (envName == null || envName.isBlank()) {
            return null;
        }
        return System.getenv(envName);
    }

    private String requireProperty(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new ExtractionException("Missing required API property: " + key);
        }
        return value;
    }

    private int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw);
    }

    private long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(raw);
    }
}
