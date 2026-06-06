package ai.jarvis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * JDBC configuration for components that require
 * blocking JDBC access (PgVector, Flyway).
 *
 * WHY NEEDED:
 * Spring Boot 4 + WebFlux does not auto-configure
 * JdbcTemplate in reactive-only applications.
 * PgVectorStore requires JdbcTemplate internally.
 * We create it explicitly here.
 *
 * This does NOT conflict with R2DBC.
 * R2DBC handles: application queries (reactive)
 * JDBC handles:  PgVector operations + Flyway
 */
@Configuration
public class JdbcConfig {

    @Value("${spring.datasource.url:"
            + "jdbc:postgresql://localhost:5433/jarvis}")
    private String url;

    @Value("${spring.datasource.username:jarvis}")
    private String username;

    @Value("${spring.datasource.password:jarvis}")
    private String password;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds =
                new DriverManagerDataSource();
        ds.setDriverClassName(
                "org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(
            DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}