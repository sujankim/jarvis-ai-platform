package ai.jarvis.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers configuration for integration tests.
 *
 * Registers ALL three connection stacks from the same container:
 * 1. R2DBC   — application queries (reactive, all repositories)
 * 2. JDBC    — datasource (JdbcConfig, pgvector operations)
 * 3. Flyway  — schema migrations
 *
 * Without registering R2DBC and datasource URLs, integration tests
 * fall back to application.yml which points to localhost:5433.
 * This causes tests to either fail if localhost is not running,
 * or hit the wrong database entirely if it is running.
 */
public class TestContainerConfig {

    public static final String DATABASE_NAME = "jarvis";
    public static final String USERNAME = "jarvis";
    public static final String PASSWORD = "jarvis";

    // Testcontainers 2.x — PostgreSQLContainer is not parameterized.
    // Do not add type parameter <> here.
    static PostgreSQLContainer container =
            new PostgreSQLContainer("pgvector/pgvector:pg16")
                    .withDatabaseName(DATABASE_NAME)
                    .withUsername(USERNAME)
                    .withPassword(PASSWORD);

    @DynamicPropertySource
    static void registerProperties(
            DynamicPropertyRegistry registry) {

        // R2DBC — all Spring Data R2DBC repositories use this.
        // Format: r2dbc:postgresql://host:port/database
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://"
                        + container.getHost()
                        + ":"
                        + container.getMappedPort(5432)
                        + "/"
                        + DATABASE_NAME);
        registry.add("spring.r2dbc.username",
                container::getUsername);
        registry.add("spring.r2dbc.password",
                container::getPassword);

        // JDBC DataSource — used by JdbcConfig for pgvector operations.
        registry.add("spring.datasource.url",
                container::getJdbcUrl);
        registry.add("spring.datasource.username",
                container::getUsername);
        registry.add("spring.datasource.password",
                container::getPassword);

        // Flyway — must point to the same container as R2DBC and JDBC
        // so schema is created before application queries run.
        registry.add("spring.flyway.url",
                container::getJdbcUrl);
        registry.add("spring.flyway.user",
                container::getUsername);
        registry.add("spring.flyway.password",
                container::getPassword);
    }
}