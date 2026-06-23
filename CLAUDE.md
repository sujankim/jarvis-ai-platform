# Jarvis AI Platform — Claude Instructions

## Project Context

Jarvis is a local-first, open-source AI assistant platform built with the Java/Spring ecosystem.

GitHub: https://github.com/sujankim/jarvis-ai-platform

### Core Philosophy

* Local AI first (Ollama) — cloud (Gemini) as fallback
* Your data never leaves your machine
* Privacy by architecture, not policy
* Reactive-first architecture
* Modular, phase-driven development
* Open-source and developer-focused

---

# Current Phase Status

| Phase   | Status          | Notes             |
| ------- | --------------- | ----------------- |
| Phase 1 | ✅ Released      | AI Chat + CLI     |
| Phase 2 | ✅ Core Complete | Memory + pgvector |
| Phase 3 | ✅ Core Complete | RAG Engine        |
| Phase 4 | ✅ Core Complete | Tool Engine + MCP |
| Phase 5 | 🔨 In Progress  | Voice Assistant   |
| Phase 6 | 📋 Planned      | Agents            |
| Phase 7 | 📋 Planned      | Web UI            |

---

# AI Architecture Overview

```text
User
 │
 ├── CLI
 └── REST API
      │
      ▼
AiOrchestrator
      │
      ▼
PromptAssembler
      │
      ├── Working Memory
      ├── Long-Term Memory
      ├── RAG Context
      ├── Session History
      │
      ▼
ProviderRouter
      │
      ├── OllamaProvider
      └── GeminiProvider
      │
      ▼
ToolRegistry
      │
      ├── DateTimeTool
      ├── CalculatorTool
      ├── WeatherTool
      └── WebSearchTool
      │
      ▼
PostgreSQL + pgvector + Redis
```

---

# Tech Stack

| Layer            | Technology                |
| ---------------- | ------------------------- |
| Language         | Java 21                   |
| Framework        | Spring Boot 4.0.6         |
| AI               | Spring AI 2.0 (M8+)       |
| Web              | Spring WebFlux (Reactive) |
| DB Access        | R2DBC (Reactive)          |
| Database         | PostgreSQL 16             |
| Vector Database  | pgvector 0.7.4            |
| Migrations       | Flyway (V1–V15+)           |
| Mapping          | MapStruct 1.6             |
| Security         | Spring Security 7 + JWT   |
| Password Hashing | Argon2id (Bouncy Castle)  |
| CLI              | Spring Shell 4.0 + JLine  |
| Tools            | Spring AI @Tool + MCP     |
| Cache            | Redis 7                   |

---

# Package Structure

```text
ai.jarvis/
│
├── ai/
│   ├── orchestrator/      AiOrchestrator
│   ├── provider/          Ollama/Gemini providers
│   └── prompt/            PromptAssembler
│
├── chat/
│   ├── session/           ChatSession
│   └── message/           Message
│
├── cli/                   Spring Shell commands
│
├── memory/                Phase 2 Memory System
│   └── session/           Redis cache
│
├── rag/                   Phase 3 RAG Engine
│   ├── extraction/        Text extractors
│   └── processing/        Chunking + embeddings
│
├── tools/                 Phase 4 Tool Engine
│   ├── builtin/
│   │   ├── DateTimeTool
│   │   ├── CalculatorTool
│   │   ├── WeatherTool
│   │   └── WebSearchTool
│   │
│   └── mcp/
│       └── McpServerConfig
│
├── voice/                 Phase 5 (In Progress)
│
├── agents/                Phase 6 (Planned)
│
├── security/              JWT + Authentication
│
├── user/                  User Management
│
├── observability/         Metrics + Logging
│
├── common/                Shared Utilities
│
└── config/                Spring Configuration
```

---

# Architecture Rules

## 1. AiProvider Interface Is Sacred

Rules:

* All AI providers implement `AiProvider`
* All providers accept `ToolRegistry` injection
* Provider selection handled by `ProviderRouter`
* Never call provider implementations directly

---

## 2. Dependency Direction (STRICT)

```text
CLI → Controllers → Services → Providers → Database
```

Never:

* Skip layers
* Reverse dependencies
* Access repositories from controllers
* Access providers from controllers

---

## 3. AiOrchestrator Is The ONLY Coordinator

Responsibilities:

* Load session history
* Load memory context
* Load RAG context
* Build prompt
* Select provider
* Execute tools
* Save messages

Controllers and CLI must never orchestrate AI workflows.

---

## 4. PromptAssembler Assembly Order

This order is mandatory:

```text
1. System Prompt
2. Working Memory
3. Long-Term Memories
4. RAG Document Context
5. Session History
6. Current User Message
```

Do not change without architectural discussion.

---

## 5. Tool Package Structure

```text
tools/
│
├── JarvisTool
├── ToolRegistry
│
├── builtin/
│   ├── DateTimeTool
│   ├── CalculatorTool
│   ├── WeatherTool
│   └── WebSearchTool
│
└── mcp/
    └── McpServerConfig
```

---

# Code Standards

## Java

Use records for DTOs.

```java
public record UserResponse(
        UUID id,
        String username
) {}
```

Use builder APIs.

```java
OllamaOptions options =
        OllamaOptions.builder()
                .model("llama3.1:8b")
                .temperature(0.7)
                .build();
```

Never use deprecated setter APIs.

---

## Spring Shell 4

Preferred:

```java
@Component
public class MyCommands {

    @Command(
        name = "my-command",
        description = "Example command"
    )
    public String execute(
            @Option(
                longNames = "value")
            String value) {

        return value;
    }
}
```

Avoid:

```java
@ShellComponent
@ShellMethod
@ShellOption
```

---

# Tool Development Standards

Adding a new tool:

```java
package ai.jarvis.tools.builtin;

@Component
public class MyTool implements JarvisTool {

    @Tool(
        description =
                "What this tool does. "
                        + "When AI should call it. "
                        + "What it returns."
    )
    public String execute(
            @ToolParam(
                    description =
                            "Expected parameter")
            String input) {

        try {
            return "result";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

---

## Tool Rules

### Required

* Explain WHAT it does
* Explain WHEN AI should call it
* Explain expected parameters
* Explain return value
* Provide examples when useful

### Forbidden

* Throw exceptions
* Return null
* Perform destructive operations without validation
* Use vague descriptions

---

# Reactive Programming Rules

Correct:

```java
public Mono<String> process() {

    return Mono.zip(
            loadHistory(sessionId),
            loadMemory(userId, message),
            loadRag(userId, message)
    ).flatMap(tuple ->
            handle(tuple));
}
```

Incorrect:

```java
String result =
        service.process().block();
```

---

## Reactive Guidelines

Use:

* `flatMap()` for async operations
* `map()` for synchronous transforms
* `switchIfEmpty()` for missing data
* `onErrorResume()` for recovery

Never:

* `.block()` outside CLI
* Mix blocking JDBC with R2DBC
* Ignore reactive errors

---

# Database Migrations

| Version | Description                   |
| ------- | ----------------------------- |
| V1      | Create users                  |
| V2      | Create AI providers           |
| V3      | Create chat sessions          |
| V4      | Create messages               |
| V5      | Create system prompts         |
| V6      | Create conversation summaries |
| V7      | Create refresh tokens         |
| V8      | Seed default data             |
| V9      | Create memories               |
| V10     | Enable pgvector               |
| V11     | Add memory embeddings         |
| V12     | Memory constraints            |
| V13     | Create documents              |
| V14     | Create document chunks        |
| V15+    | Voice assistant tables        |

---

# Security Rules

Always:

* Use Argon2id
* Validate input with `@Valid`
* Verify ownership before access
* Sanitize user input

Never:

* Log passwords
* Log password hashes
* Log JWT tokens
* Log refresh tokens
* Log conversation content

Required:

```java
.onErrorMap(
        IllegalArgumentException.class,
        ex -> new UnauthorizedException()
)
```

for invalid UUID parsing.

---

# Testing Rules

Unit Test Example:

```java
@Test
void shouldReturnUser() {

    assertThat(result)
            .isNotNull();
}
```

Reactive Test Example:

```java
StepVerifier.create(flux)
        .expectNextCount(1)
        .verifyComplete();
```

Naming:

```text
*Test.java
*IntegrationTest.java
```

---

# Commit Message Convention

Examples:

```text
feat: add weather tool

fix: resolve streaming timeout

docs: update architecture diagrams

test: add PromptAssembler tests

refactor: extract provider routing

chore: upgrade Spring AI
```

Allowed Types:

* feat
* fix
* docs
* test
* refactor
* chore

---

# Review Checklist

## Reactive Correctness

* [ ] No `.block()` outside cli/
* [ ] `flatMap()` for async operations
* [ ] `map()` for sync transformations
* [ ] Proper error handling

## Security

* [ ] No sensitive logs
* [ ] @Valid present
* [ ] Ownership verification exists
* [ ] UUID errors handled

## Architecture

* [ ] Layers respected
* [ ] Tools implement JarvisTool
* [ ] Built-in tools in tools/builtin
* [ ] MCP code in tools/mcp
* [ ] Providers receive ToolRegistry

## Spring AI 2.0

* [ ] Builder pattern used
* [ ] MethodToolCallbackProvider.builder()
* [ ] ChatClient.tools(toolRegistry.asArray())

## Code Quality

* [ ] Records used for DTOs
* [ ] MapStruct used for mapping
* [ ] Tests added
* [ ] Conventional commits followed

---

# What NOT To Change

Do NOT:

* Change AiProvider without discussion
* Change PromptAssembler ordering
* Bypass AiOrchestrator
* Introduce microservices
* Break local-first architecture
* Add cloud services requiring user accounts
* Move built-in tools outside tools/builtin
* Move MCP code outside tools/mcp

---

# Development Principles

Every contribution should reinforce:

1. Privacy First
2. Local First
3. Reactive First
4. Tool Driven
5. Memory Aware
6. Modular By Phase
7. Developer Friendly
8. Open Source First

When in doubt, choose the solution that best aligns with these principles.
