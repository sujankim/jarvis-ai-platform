package ai.jarvis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI jarvisOpenAPI() {
        return new OpenAPI()

                // ─── Project Info ────────────────────────────────
                .info(new Info()
                        .title("Jarvis AI Platform API")
                        .description(
                                "Local-first, open-source AI assistant. " +
                                        "Built with Spring Boot 4 and Spring AI 2."
                        )
                        .version("0.1.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("Sujan")
                                .url("https://github.com/sujankim/jarvis-ai-platform")
                        )
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")
                        )
                )

                // ─── Security Scheme ─────────────────────────────
                // Adds the "Authorize" button to Swagger UI
                // Users paste their JWT token there
                .addSecurityItem(
                        new SecurityRequirement().addList("Bearer Auth")
                )
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth",
                                new SecurityScheme()
                                        .name("Bearer Auth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Enter JWT token from /api/v1/auth/login"
                                        )
                        )
                );
    }
}