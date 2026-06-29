package ai.jarvis.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Flyway configuration.
 *
 * WHY THIS IS NEEDED:
 * With both JDBC and R2DBC DataSources present,
 * Spring Boot Flyway autoconfiguration does not
 * trigger reliably.
 *
 * This bean uses initMethod="migrate" to guarantee:
 * 1. Flyway runs during bean creation (synchronous)
 * 2. All tables created BEFORE ApplicationRunners
 * 3. No "relation does not exist" errors
 */
@Configuration
@ConditionalOnProperty(
        prefix = "spring.flyway",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.flyway.url:"
            + "jdbc:postgresql://localhost:5433/jarvis}")
    private String url;

    @Value("${spring.flyway.user:jarvis}")
    private String user;

    @Value("${spring.flyway.password:jarvis}")
    private String password;

    @Value("${spring.flyway.locations:"
            + "classpath:db/migration}")
    private String locations;

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations)
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }
}
