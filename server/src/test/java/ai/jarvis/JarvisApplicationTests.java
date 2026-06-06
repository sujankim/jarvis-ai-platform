package ai.jarvis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "jarvis.security.jwt.secret="
                + "test-secret-key-minimum-32-characters-long-enough",
        "spring.shell.interactive.enabled=false",
        "spring.ai.google.api-key=",
        "spring.profiles.active=test",
        "spring.r2dbc.url="
                + "r2dbc:postgresql://localhost:5432/jarvis",
        "spring.datasource.url="
                + "jdbc:postgresql://localhost:5432/jarvis",
        "spring.flyway.url="
                + "jdbc:postgresql://localhost:5432/jarvis",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class JarvisApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring application context
        // starts without errors.
        //
        // CI services required (see ci.yml):
        // - PostgreSQL with pgvector (pgvector/pgvector:pg16)
        // - Redis (redis:7-alpine)
        //
        // Flyway runs all migrations including:
        // V10 (pgvector extension) and V11 (embedding column)
    }
}