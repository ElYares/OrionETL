package com.elyares.etl.e2e;

import com.elyares.etl.application.dto.ExecutionRequestDto;
import com.elyares.etl.application.dto.PipelineExecutionDto;
import com.elyares.etl.application.usecase.execution.ExecutePipelineUseCase;
import com.elyares.etl.domain.enums.ExecutionStatus;
import com.elyares.etl.domain.model.pipeline.Pipeline;
import com.elyares.etl.integration.persistence.support.PostgresIntegrationTestBase;
import com.elyares.etl.pipelines.customer.CustomerPipelineConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerPipelineE2EIT extends PostgresIntegrationTestBase {

    private static final HttpServer SERVER = startServer();
    private static final String BASE_URL = "http://localhost:" + SERVER.getAddress().getPort() + "/customers";

    @DynamicPropertySource
    static void customerProps(DynamicPropertyRegistry registry) {
        registry.add("ORION_CUSTOMER_API_URL", () -> BASE_URL);
    }

    @Autowired
    private ExecutePipelineUseCase executePipelineUseCase;

    @Autowired
    private CustomerPipelineConfig customerPipelineConfig;

    @BeforeEach
    void cleanTablesAndRegisterPipeline() {
        jdbcTemplate.execute("TRUNCATE TABLE customers_staging, customers");
        customerPipelineConfig.registerIfMissing();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void shouldExecuteCustomerPipelineAndProtectClosedCustomer() {
        Pipeline pipeline = customerPipelineConfig.registerIfMissing();
        jdbcTemplate.update("""
            INSERT INTO customers (
                crm_customer_id, document_type, document_number, first_name, last_name, email, phone,
                country_code, registration_date, status, preferred_language, birth_date, gender, customer_type,
                etl_execution_id, etl_pipeline_id, etl_source_file, etl_load_timestamp, etl_pipeline_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, NULL, NULL, ?, ?::uuid, ?::uuid, ?, now(), ?)
            """,
            "crm-existing", "CC", "1001", "Closed", "Customer", "closed@old.test", "+573000000000",
            "CO", "CLOSED", "es-CO", "INDIVIDUAL",
            "11111111-1111-1111-1111-111111111111",
            pipeline.getId().toString(),
            "seed",
            "seed"
        );

        PipelineExecutionDto execution = executePipelineUseCase.execute(
            ExecutionRequestDto.manual(pipeline.getId().toString(), "customer-e2e")
        );

        assertThat(execution.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.totalRead()).isEqualTo(3);
        assertThat(execution.totalRejected()).isEqualTo(0);
        assertThat(execution.totalLoaded()).isEqualTo(2);

        String normalizedPhone = jdbcTemplate.queryForObject(
            "SELECT phone FROM customers WHERE document_type = 'CE' AND document_number = '2002'",
            String.class
        );
        String closedEmail = jdbcTemplate.queryForObject(
            "SELECT email FROM customers WHERE document_type = 'CC' AND document_number = '1001'",
            String.class
        );
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Integer.class);

        assertThat(normalizedPhone).isEqualTo("+573001234567");
        assertThat(closedEmail).isEqualTo("closed@old.test");
        assertThat(rows).isEqualTo(3);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/customers", CustomerPipelineE2EIT::handleCustomers);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void handleCustomers(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String response;
        if (query != null && query.contains("cursor=cursor-2")) {
            response = """
                {
                  "customers": [
                    {
                      "customer_id": "crm-3",
                      "document_type": "NIT",
                      "document_number": "900123",
                      "first_name": "acme",
                      "last_name": "corp",
                      "email": "ventas@acme.co",
                      "phone": "6015551234",
                      "country_code": "170",
                      "registration_date": "2024-05-20",
                      "status": "A",
                      "preferred_language": "es-CO",
                      "customer_type": "BUSINESS"
                    }
                  ],
                  "meta": {
                    "next_cursor": null
                  }
                }
                """;
        } else {
            response = """
                {
                  "customers": [
                    {
                      "customer_id": "crm-1",
                      "document_type": "CC",
                      "document_number": "1001",
                      "first_name": "juan",
                      "last_name": "perez",
                      "email": "new-closed@test.co",
                      "phone": "3000000000",
                      "country_code": "CO",
                      "registration_date": "2024-01-10",
                      "status": "A",
                      "preferred_language": "es-CO",
                      "customer_type": "INDIVIDUAL"
                    },
                    {
                      "customer_id": "crm-2",
                      "document_type": "CE",
                      "document_number": "2002",
                      "first_name": "maria elena",
                      "last_name": "o'brien-smith",
                      "email": "MARIA@EXAMPLE.COM",
                      "phone": "3001234567",
                      "country_code": "COL",
                      "registration_date": "2024-02-15",
                      "status": "A",
                      "preferred_language": "es-CO",
                      "customer_type": "INDIVIDUAL"
                    }
                  ],
                  "meta": {
                    "next_cursor": "cursor-2"
                  }
                }
                """;
        }

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
