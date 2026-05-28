# 🗺️ Jarvis AI Platform — Roadmap

> **Last Updated:** May 2026
> **Current Status:** Phase 1 — In Development 🔨

This is the public roadmap for Jarvis AI Platform.
It shows exactly where we are, where we are going,
and how you can help get there.

---

# 🧭 Vision

Jarvis is not a chatbot. Jarvis is an AI orchestration platform.

AI Chat + Memory + Tools + Voice + RAG + Agents = A personal AI assistant that knows you, runs locally, and respects your privacy.

**Your AI. Your Data. Your Machine.**

---

# 📊 Status Legend

| Symbol | Meaning                     |
| ------ | --------------------------- |
| ✅      | Complete                    |
| 🔨     | In Progress                 |
| 📋     | Planned                     |
| 💭     | Considering (not committed) |
| ❌      | Will NOT build              |

---

# 🏗️ Phase 0 — Foundation (Complete ✅)

**Goal:** Architecture designed. Repository ready. Team prepared.

## Architecture

* ✅ Complete system architecture designed
* ✅ Technology stack decided (Java 21, Spring Boot 4, Spring AI 2.0)
* ✅ Database schema designed (PostgreSQL + pgvector)
* ✅ Security architecture designed (JWT + Argon2id)
* ✅ Memory architecture designed (4-layer system)
* ✅ Provider abstraction designed (Ollama-first + cloud fallback)
* ✅ CLI architecture designed (Spring Shell 4.0)
* ✅ Advisor pipeline designed

## Repository

* ✅ GitHub repository created
* ✅ Folder structure established
* ✅ All 8 Flyway migration SQL files written
* ✅ pom.xml with all dependencies confirmed
* ✅ application.yml complete configuration
* ✅ Docker Compose for PostgreSQL
* ✅ README, CONTRIBUTING, LICENSE (Apache-2.0)
* ✅ Architecture Decision Records (ADRs)

---

# 🔥 Phase 1 — AI Core Foundation

> **Target:** v0.1.0
> **Status:** 🔨 Starting May 28, 2026

**Goal:** A real, working AI assistant you can chat with via terminal.

## Core Infrastructure

* 🔨 Spring Boot 4.0 application setup
* 🔨 Flyway database migrations (schema + seed data)
* 🔨 R2DBC reactive database connection
* 🔨 Health check endpoints (`/actuator/health`)
* 🔨 Structured logging with request IDs
* 🔨 Micrometer metrics (`/actuator/prometheus`)

## AI Engine

* 🔨 Spring AI 2.0 integration
* 🔨 `OllamaProvider` — local AI (primary)
* 🔨 `GeminiProvider` — cloud AI (fallback)
* 🔨 `ProviderRouter` — smart failover logic
* 🔨 `PromptAssembler` — context builder
* 🔨 `WorkingMemoryBuilder` — date/time/user injection
* 🔨 SSE token streaming (real-time responses)

## Advisor Pipeline

* 🔨 `JarvisObservabilityAdvisor` — logging + metrics
* 🔨 `RateLimitAdvisor` — abuse protection
* 🔨 `InputGuardrailAdvisor` — input validation
* 🔨 `JarvisMemoryAdvisor` — session history injection

## Memory (Session-Level)

* 🔨 `SessionMemoryService` — load + inject chat history
* 🔨 `JarvisChatMemory` — PostgreSQL-backed
* 🔨 Summarization trigger — auto-compress long sessions
* 🔨 `SummarizationService` — async background compression
* 🔨 `conversation_summaries` — token-efficient history storage

## Security

* 🔨 JWT authentication (JJWT 0.12.7)
* 🔨 Argon2id password hashing (Bouncy Castle 1.84)
* 🔨 Refresh token rotation (7-day lifetime)
* 🔨 Spring Security 7 filter chain (WebFlux-reactive)
* 🔨 Role-based access (ADMIN / USER)
* 🔨 Rate limiting per user

## REST API

* 🔨 `POST /api/v1/auth/register`
* 🔨 `POST /api/v1/auth/login`
* 🔨 `POST /api/v1/auth/refresh`
* 🔨 `POST /api/v1/auth/logout`
* 🔨 `POST /api/v1/chat/stream`
* 🔨 `POST /api/v1/chat`
* 🔨 `GET /api/v1/sessions`
* 🔨 `GET /api/v1/sessions/{id}/messages`
* 🔨 `DELETE /api/v1/sessions/{id}`
* 🔨 `GET /api/v1/providers`
* 🔨 `GET /actuator/health`

## CLI (Spring Shell 4.0)

* 🔨 Jarvis ASCII art banner on startup
* 🔨 `login` / `logout` / `whoami`
* 🔨 `chat` — interactive streaming conversation loop
* 🔨 `ask "question"` — single question mode
* 🔨 `session list` / `new` / `switch` / `delete`
* 🔨 `config show` / `set-model` / `set-provider`
* 🔨 `status` — system health overview
* 🔨 `doctor` — diagnose all common problems
* 🔨 `logs --errors` — show recent errors
* 🔨 `help` — built-in help system
* 🔨 First-run setup wizard

## API Documentation

* 🔨 Swagger UI (`/swagger-ui.html`)
* 🔨 OpenAPI 3.0 spec (`/v3/api-docs`)

## Distribution (Phase 1)

* 🔨 Executable fat JAR
* 🔨 Launcher scripts (`jarvis.sh` / `jarvis.bat`)
* 🔨 `docker-compose.yml`
* 🔨 One-page install guide in README

## Testing

* 🔨 Unit tests for AI orchestration logic
* 🔨 Unit tests for JWT service
* 🔨 Unit tests for prompt assembly
* 🔨 Integration tests with Testcontainers
* 🔨 Integration test for SSE streaming endpoint

---

# 🧠 Phase 2 — Memory System

> **Target:** v0.2.0
> **Status:** 📋 Planned

**Goal:** Jarvis remembers you across sessions.

## Short-Term Cache

* 📋 Redis integration for active session caching
* 📋 Session context cached in Redis
* 📋 Cache invalidation strategy

## Long-Term Memory

* 📋 `memories` table (PostgreSQL)
* 📋 Memory types: FACT, GOAL, PREFERENCE, CONTEXT
* 📋 Explicit memory commands (`/remember`, `/recall`)
* 📋 Automatic memory extraction after sessions
* 📋 Memory management UI in CLI

## Semantic Memory

* 📋 `pgvector` extension enabled
* 📋 Local embedding model (`nomic-embed-text`)
* 📋 Embeddings stored alongside memories
* 📋 Semantic retrieval using cosine similarity
* 📋 Relevant memories injected into prompts

## Memory CLI Commands

* 📋 `memory list`
* 📋 `memory search "topic"`
* 📋 `memory delete [id]`
* 📋 `memory clear`

---

# 📚 Phase 3 — RAG Engine

> **Target:** v0.3.0
> **Status:** 📋 Planned

**Goal:** Chat with your own documents.

## Document Ingestion

* 📋 PDF document upload and parsing
* 📋 Plain text support
* 📋 Markdown support
* 📋 Smart text chunking
* 📋 Metadata storage

## Vector Storage

* 📋 `document_chunks` table with pgvector embeddings
* 📋 HNSW indexing
* 📋 Source attribution tracking

## Retrieval + Generation

* 📋 Semantic search on chunks
* 📋 Top-K chunk injection
* 📋 Source citations in responses
* 📋 `RagContextAdvisor`

## CLI Commands

* 📋 `doc upload [filepath]`
* 📋 `doc list`
* 📋 `doc delete [id]`
* 📋 `chat --rag`

## Distribution Update

* 📋 `curl` installer script
* 📋 Smart installer for Ollama + Docker

---

# 🔧 Phase 4 — Tool Engine

> **Target:** v0.4.0
> **Status:** 📋 Planned

**Goal:** Jarvis takes real actions in the world.

## Tool Framework

* 📋 `@Tool` annotation pattern
* 📋 `ToolRegistryService`
* 📋 MCP Server enabled
* 📋 External MCP client connections
* 📋 Tool observability

## Built-in Tools

* 📋 `DateTimeTool`
* 📋 `CalculatorTool`
* 📋 `WeatherTool`
* 📋 `WebSearchTool`
* 📋 `MemoryTool`
* 📋 `FileTool`

## MCP Ecosystem

* 📋 Jarvis tools exposed as MCP server
* 📋 GitHub MCP integration
* 📋 Filesystem MCP integration
* 📋 Community tool framework

---

# 🎙️ Phase 5 — Voice Assistant

> **Target:** v0.5.0
> **Status:** 📋 Planned

**Goal:** Talk to Jarvis. Hear Jarvis respond.

## Speech Processing

* 📋 Browser-based speech-to-text
* 📋 Local Whisper support
* 📋 Text-to-speech output
* 📋 Voice conversation loop

## Voice Interface

* 📋 Microphone UI
* 📋 Real-time transcription
* 📋 Wake word detection
* 📋 Voice activity detection

---

# 🤖 Phase 6 — Agent System

> **Target:** v0.6.0
> **Status:** 📋 Planned

**Goal:** Jarvis plans and executes multi-step tasks.

## Agent Architecture

* 📋 Graph-based agent design
* 📋 ReACT pattern
* 📋 `AgentPlanner`
* 📋 `AgentExecutor`
* 📋 Retry and recovery logic
* 📋 Progress reporting

## Workflows

* 📋 Multi-step workflows
* 📋 Parallel execution
* 📋 Workflow persistence
* 📋 Result summarization

## MCP Integration

* 📋 Agents use MCP tools
* 📋 Multi-agent collaboration

---

# 🌐 Phase 7 — Web UI (Angular)

> **Target:** v1.0.0
> **Status:** 💭 Considering

**Goal:** Beautiful web interface powered by the same backend.

## Frontend Stack

* 💭 Angular 21
* 💭 Angular Material
* 💭 SCSS
* 💭 Angular Signals
* 💭 RxJS

## Features

* 💭 Real-time streaming chat
* 💭 Session sidebar
* 💭 Document upload UI
* 💭 Memory management
* 💭 Settings panel
* 💭 Agent dashboard
* 💭 Voice interface

---

# 📦 Distribution Roadmap

| Phase    | Method           | Requirements            | Experience   |
| -------- | ---------------- | ----------------------- | ------------ |
| Phase 1  | JAR + scripts    | Java 21, Docker, Ollama | 20 min setup |
| Phase 3  | `curl` installer | Docker, Ollama          | 10 min setup |
| Phase 4  | Package managers | Ollama                  | 5 min setup  |
| Phase 5+ | GUI installer    | Nothing                 | 2 min setup  |

---

# ❌ What We Will NOT Build

* ❌ Cloud SaaS product
* ❌ Our own LLM
* ❌ Proprietary features
* ❌ Central telemetry
* ❌ Microservices from day one

---

# 🗓️ Timeline (Approximate)

| Timeline     | Milestone                |
| ------------ | ------------------------ |
| May 28, 2026 | Phase 1 coding begins    |
| June 4, 2026 | Spring AI 2.0 GA upgrade |
| Q3 2026      | Phase 1 — v0.1.0         |
| Q4 2026      | Phase 2 — Memory         |
| Q1 2027      | Phase 3 — RAG            |
| Q2 2027      | Phase 4 — Tools          |
| Q3 2027      | Phase 5 — Voice          |
| Q4 2027      | Phase 6 — Agents         |
| 2028         | Phase 7 — Full Platform  |

---

# 🤝 How To Contribute

Every phase needs contributors.

| Need                  | Phase   | Skill Required    |
| --------------------- | ------- | ----------------- |
| Core AI engine        | Phase 1 | Java, Spring Boot |
| Database + migrations | Phase 1 | SQL, R2DBC        |
| CLI interface         | Phase 1 | Spring Shell      |
| Unit tests            | All     | JUnit 5           |
| Integration tests     | All     | Testcontainers    |
| Documentation         | All     | Writing           |
| Memory system         | Phase 2 | Redis, pgvector   |
| RAG pipeline          | Phase 3 | Embeddings        |
| Tool development      | Phase 4 | REST APIs         |
| MCP tools             | Phase 4 | MCP protocol      |
| Voice pipeline        | Phase 5 | Audio processing  |
| Agent system          | Phase 6 | Graph algorithms  |
| Angular UI            | Phase 7 | Angular           |

**Start here:** Look for `good first issue` labels on GitHub.
**Ask questions:** Open a GitHub Discussion.

---

# 💡 Feature Requests

Good feature requests explain:

* The use case
* Which phase it fits
* Why it aligns with the local-first philosophy

---

# 📄 License

Jarvis AI Platform is licensed under the Apache License 2.0.

Free to use, modify, and distribute.

Your contributions remain open source.

---

*This roadmap is a living document. It reflects current thinking and will evolve as the project grows and community feedback shapes it.*

**Last reviewed: May 2026**
