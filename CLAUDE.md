# Jarvis AI Platform — Claude Instructions

## Project Context

Jarvis is a local-first, open-source AI assistant platform built with the Java/Spring ecosystem.

GitHub: https://github.com/sujankim/jarvis-ai-platform

**Core philosophy:**

* Local AI first (Ollama) — cloud (Gemini) as fallback
* Your data never leaves your machine
* Privacy by architecture, not policy

## Tech Stack

| Layer      | Technology                |
| ---------- | ------------------------- |
| Language   | Java 21                   |
| Framework  | Spring Boot 4.0.6         |
| AI         | Spring AI 2.0 (M8+)       |
| Web        | Spring WebFlux (reactive) |
| DB Access  | R2DBC (reactive)          |
| Database   | PostgreSQL 16             |
| Migrations | Flyway                    |
| Mapping    | MapStruct 1.6             |
| Security   | Spring Security 7 + JWT   |
| Password   | Argon2id (Bouncy Castle)  |
| CLI        | Spring Shell 4.0 + JLine  |

## Package Structure

```text
src/main/java/ai/jarvis/
├── ai/
│   ├── orchestrator/      AiOrchestrator (main brain)
│   ├── provider/
│   │   ├── OllamaProvider
│   │   ├── GeminiProvider
│   │   ├── AiProvider interface
│   │   └── ProviderRouter
│   ├── prompt/
│   │   ├── PromptAssembler
│   │   └── WorkingMemoryBuilder
│   └── streaming/
│       └── TokenStreamProcessor
│
├── chat/
│   ├── session/
│   │   └── ChatSession entity + service + controller
│   ├── message/
│   │   └── Message entity + service
│   └── streaming/
│       └── ChatRequest DTO
│
├── cli/
│   ├── AuthCommands
│   │   ├── login
│   │   ├── logout
│   │   └── whoami
│   ├── ChatCommands
│   │   ├── chat
│   │   └── ask
│   ├── SessionCommands
│   │   ├── session
│   │   └── switch-session
│   ├── SystemCommands
│   │   ├── status
│   │   ├── doctor
│   │   └── version
│   ├── CliStateManager
│   │   └── JWT + session state
│   ├── CliHttpClient
│   │   └── HTTP + SSE client
│   └── JarvisPromptProvider
│       └── custom jarvis:> prompt
│
├── security/
│   ├── auth/
│   │   ├── AuthController
│   │   └── AuthService
│   └── jwt/
│       ├── JwtService
│       └── JwtAuthenticationFilter
│
├── user/
│   ├── User entity
│   ├── UserRepository
│   └── UserMapper
│
├── memory/           Phase 2 (placeholders)
├── tools/            Phase 4 (placeholders)
├── agents/           Phase 6 (placeholders)
├── observability/    AiRequestLogger, OllamaHealthIndicator
├── common/           Exceptions, ApiResponse, ErrorResponse
└── config/           SecurityConfig, SwaggerConfig
```

## Architecture Rules

### 1. AiProvider Interface Is Sacred

* All AI providers MUST implement `AiProvider`
* Never call Ollama/Gemini HTTP directly from CLI
* Provider selection handled by `ProviderRouter` only

### 2. Dependency Direction (STRICT)

```text
CLI → Controllers → Services → Providers → DB
```

NEVER skip layers or go backwards.

### 3. AiOrchestrator Is The ONLY Coordinator

* Assembles prompts via `PromptAssembler`
* Routes to provider via `ProviderRouter`
* Saves messages via `MessageRepository`
* Never in controllers or CLI

## Code Standards

### Java

```java
// CORRECT: Java records for DTOs
public record UserResponse(UUID id, String username) {}

// CORRECT: Builder pattern for Spring AI
OllamaOptions options = OllamaOptions.builder()
    .model("llama3.1:8b")
    .temperature(0.7)
    .build();

// WRONG: Setters (removed in Spring AI 2.0)
OllamaOptions options = new OllamaOptions();
options.setModel("llama3.1:8b"); // DOES NOT COMPILE
```

### Spring Shell 4.0

```java
// CORRECT Spring Shell 4.0:
@Component
public class MyCommands {

    @Command(name = "my-cmd", description = "...")
    public String myCmd(
        @Option(longNames = "value") String value) {
        return value;
    }
}

// WRONG (removed in v4):
@ShellComponent
@ShellMethod(key = "x")
@ShellOption
```

### Reactive (WebFlux + R2DBC)

```java
// CORRECT:
public Mono<User> findUser(UUID id) {
    return userRepository.findById(id)
        .switchIfEmpty(
            Mono.error(new UserNotFoundException(id))
        );
}

// WRONG:
public User findUser(UUID id) {
    return userRepository.findById(id).block();
    // NEVER .block() outside cli/ package
}
```

## Security Rules

* NEVER log passwords (plain or hashed)
* NEVER log JWT tokens (access or refresh)
* NEVER log conversation content
* ALWAYS use Argon2id for password hashing
* ALWAYS validate user input with `@Valid`
* ALWAYS verify session ownership before access
* JWT access token = 15 minutes
* JWT refresh token = 7 days

## Testing Rules

```java
// Unit test example:
@Test
@DisplayName("Should do something when condition")
void shouldDoSomethingWhenCondition() {
    // given
    // when
    // then
    assertThat(result).isNotNull();
}

// Reactive test example:
@Test
void shouldStreamTokens() {
    StepVerifier.create(flux)
        .expectNextCount(1)
        .verifyComplete();
}
```

* File naming: `*Test.java` (unit), `*IntegrationTest.java`
* Run tests with: `./mvnw test`
* No Mockito for simple cases — use real objects

## Commit Message Format

```text
feat: add weather tool with OpenWeatherMap API
fix: resolve streaming timeout on long sessions
docs: update architecture diagram
test: add unit tests for PromptAssembler
refactor: extract provider routing to ProviderRouter
chore: upgrade Spring AI to 2.0.0-RC1
```

## Review Checklist

When reviewing a PR, always check:

### 1. Reactive Correctness

* [ ] No `.block()` outside `cli/` package
* [ ] `flatMap` used when next step returns Mono/Flux
* [ ] `map` used for synchronous transformations
* [ ] Error handling: `.onErrorReturn()` or `.onErrorResume()`

### 2. Security

* [ ] No sensitive data in log statements
* [ ] User input validated with `@Valid`
* [ ] Session ownership verified before access
* [ ] No hardcoded secrets or API keys

### 3. Architecture

* [ ] Layers respected (CLI → Service → Provider)
* [ ] AiProvider interface preserved
* [ ] No direct HTTP calls to Ollama/Gemini
* [ ] New providers implement AiProvider

### 4. Spring AI 2.0 API

* [ ] Builder pattern used (not setters)
* [ ] Correct artifact names (`spring-ai-starter-*`)
* [ ] `ChatClient.Builder` injected, not `ChatClient`

### 5. Code Quality

* [ ] MapStruct used for entity → DTO conversions
* [ ] Java records used for DTOs (not classes)
* [ ] UUID v7 for new entity IDs
* [ ] Conventional commit messages
* [ ] Tests added for new functionality

## What NOT To Change

* Do NOT change the AiProvider interface without discussing in an issue first
* Do NOT add microservices architecture (monolith-first is intentional)
* Do NOT add cloud-hosted services that require user accounts
* Do NOT break local-first privacy principle
