package ai.jarvis.config;

import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    // Public endpoints — no JWT required.
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/actuator/health"
    };

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors
                        .configurationSource(
                                corsConfigurationSource()))
                .addFilterBefore(
                        jwtAuthFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }

    /**
     * CORS configuration source.
     *
     * Referenced by ServerHttpSecurity.cors() above
     * to handle CORS on security-protected endpoints.
     *
     * Allowed origins cover all common Angular dev ports:
     * - 4200: ng serve default
     * - 4201: ng serve --port 4201 (second instance)
     *
     * In production, restrict allowedOrigins to the
     * actual deployed frontend URL.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config =
                new CorsConfiguration();

        // Angular dev server origins
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://localhost:4201"
        ));

        // Allow all headers including Authorization
        config.setAllowedHeaders(List.of("*"));

        // HTTP methods used by the Jarvis API
        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // Allow Authorization header to be read
        // by the browser from responses
        config.setExposedHeaders(List.of(
                "Authorization"
        ));

        // Required for JWT in Authorization header.
        // Without this, browser strips the header.
        config.setAllowCredentials(true);

        // Cache preflight for 1 hour
        // Reduces OPTIONS requests in dev
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id: memory=65536KB, iterations=3,
        //           parallelism=1, hashLength=32, saltLength=16
        return new Argon2PasswordEncoder(
                16, 32, 1, 65536, 3);
    }
}