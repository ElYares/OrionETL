package com.elyares.etl.integration.extractor;

import com.elyares.etl.domain.enums.SourceType;
import com.elyares.etl.domain.model.source.SourceConfig;
import com.elyares.etl.infrastructure.extractor.database.DatabaseExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseExtractorIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("orionetl_db_extractor_it")
        .withUsername("orionetl")
        .withPassword("orionetl");

    private final DatabaseExtractor extractor = new DatabaseExtractor();

    @BeforeEach
    void setUpSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("""
                DROP TABLE IF EXISTS source_orders;
                CREATE TABLE source_orders (
                    order_id VARCHAR(50) PRIMARY KEY,
                    customer_id VARCHAR(50) NOT NULL,
                    amount NUMERIC(12,2) NOT NULL,
                    status VARCHAR(20) NOT NULL
                );
                """);
            statement.execute("""
                INSERT INTO source_orders (order_id, customer_id, amount, status) VALUES
                ('O-001', 'CUST-001', 10.50, 'OPEN'),
                ('O-002', 'CUST-002', 25.00, 'OPEN'),
                ('O-003', 'CUST-003', 5.00, 'CLOSED');
                """);
        }
    }

    @Test
    void shouldExtractRowsFromDatabaseUsingNamedParameters() {
        SourceConfig config = new SourceConfig(
            SourceType.DATABASE,
            POSTGRES.getJdbcUrl(),
            "UTF-8",
            ',',
            false,
            Map.of(
                "username", POSTGRES.getUsername(),
                "password", POSTGRES.getPassword(),
                "query", """
                    SELECT order_id, customer_id, amount, status
                    FROM source_orders
                    WHERE status = :status
                      AND amount >= :minAmount
                    ORDER BY order_id
                    """,
                "fetchSize", "100",
                "queryParam.status", "OPEN",
                "queryParam.minAmount", "10.00",
                "queryParamType.minAmount", "DECIMAL"
            )
        );

        var result = extractor.extract(config, null);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalRead()).isEqualTo(2);
        assertThat(result.getRecords().getFirst().getRowNumber()).isEqualTo(1L);
        assertThat(result.getRecords().getFirst().getData())
            .containsEntry("order_id", "O-001")
            .containsEntry("customer_id", "CUST-001")
            .containsEntry("status", "OPEN");
        assertThat(result.getRecords().get(1).getData().get("amount"))
            .isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result.getRecords().get(1).getData().get("amount"))
            .isEqualByComparingTo(new BigDecimal("25.00"));
    }
}
