package ai.jarvis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        // ── Security ────────────────────────────────
        "jarvis.security.jwt.secret="
                + "test-secret-key-minimum-32-characters"
                + "-long-enough-for-jwt-hmac-sha256",

        // ── Shell ────────────────────────────────────
        // Disable interactive shell in tests
        "spring.shell.interactive.enabled=false",

        // ── Gemini — CORRECT property name ───────────
        // FIX: was "spring.ai.google.api-key="
        // must match application.yml exclusion key
        "spring.ai.google.genai.api-key=",

        // ── Database ─────────────────────────────────
        "spring.flyway.enabled=false",
        "spring.r2dbc.url="
                + "r2dbc:postgresql://localhost:5433/jarvis",
        "spring.datasource.url="
                + "jdbc:postgresql://localhost:5433/jarvis",
        "spring.datasource.username=jarvis",
        "spring.datasource.password=jarvis",
        "spring.flyway.url="
                + "jdbc:postgresql://localhost:5433/jarvis",
        "spring.flyway.user=jarvis",
        "spring.flyway.password=jarvis",

        // ── Redis ─────────────────────────────────────
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",

        // ── pgvector ──────────────────────────────────
        "spring.ai.vectorstore.pgvector"
                + ".initialize-schema=false"
})
class JarvisApplicationTests {

    @Test
    void contextLoads() {
        // Requires: docker-compose up -d
        // PostgreSQL on port 5433
        // Redis on port 6379
        // Flyway disabled - migrations skipped for speed
        // R2DBC pool still requires PostgreSQL connection
    }
}
