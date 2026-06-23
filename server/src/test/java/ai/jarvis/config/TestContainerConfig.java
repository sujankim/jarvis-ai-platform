package ai.jarvis.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public class TestContainerConfig {

    public static final String DATABASE_NAME = "jarvis";
    public static final String USERNAME = "jarvis";
    public static final String PASSWORD = "jarvis";

    static PostgreSQLContainer container = new PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @DynamicPropertySource
    static void registerFlywayProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("spring.flyway.url", container::getJdbcUrl);
        propertyRegistry.add("spring.flyway.user", container::getUsername);
        propertyRegistry.add("spring.flyway.password", container::getPassword);
    }
}
