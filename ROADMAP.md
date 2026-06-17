# 🗺️ Jarvis AI Platform — Roadmap

> **Last Updated:** June 2026
> **Current Status:** Phase 3 — In Progress 🔨

---

# 📊 Status Legend

| Symbol | Meaning        |
| ------ | -------------- |
| ✅      | Complete       |
| 🔨     | In Progress    |
| 📋     | Planned        |
| 💭     | Considering    |
| ❌      | Will NOT Build |

---

# 🏗️ Phase 0 — Foundation ✅ Complete

Architecture designed, repository structured, and documentation written before any code.

---

# 🔥 Phase 1 — AI Core Foundation ✅ Released (v0.1.0)

**Released:** June 2026

* ✅ Spring Boot 4 + Spring AI 2.0 (M8)
* ✅ Ollama (local) + Gemini (cloud fallback)
* ✅ Provider abstraction (`AiProvider` interface)
* ✅ SSE token streaming (WebFlux)
* ✅ JWT authentication (Argon2id)
* ✅ PostgreSQL + Flyway (8 migrations)
* ✅ Spring Shell 4 CLI (`jarvis:>` prompt)
* ✅ Custom JLine terminal (backspace, history)
* ✅ Session management + message persistence
* ✅ Working memory (date/time/user injection)
* ✅ Redis session caching
* ✅ First-run setup wizard
* ✅ Swagger UI + health indicators
* ✅ GitHub Actions CI
* ✅ Dependabot + CodeRabbit

---

# 🧠 Phase 2 — Memory System 🔨 Core Complete

**Target:** `v0.2.0`

## Infrastructure ✅

* ✅ Redis 7 (session caching ~1ms vs ~50ms DB)
* ✅ pgvector 0.7.4 (custom Docker build)
* ✅ `memories` table (V9)
* ✅ pgvector extension (V10)
* ✅ embedding column on memories (V11)

## Core Memory Pipeline ✅

* ✅ Memory entity + `MemoryType` enum
* ✅ `MemoryRepository` (R2DBC)
* ✅ `MemoryEmbeddingRepository` (JDBC/pgvector)
* ✅ `MemoryService` (CRUD + semantic search)
* ✅ `EmbeddingService` (`nomic-embed-text`)
* ✅ `MemoryExtractionService` (async AI extraction)
* ✅ `PromptAssembler` updated (memory injection)
* ✅ `AiOrchestrator` (parallel load via `Mono.zip`)
* ✅ Prompt injection security (sanitization)

## Contributor Tasks 📋

* 📋 Memory REST API (`GET`, `POST`, `DELETE /api/v1/memories`)
* 📋 CLI memory commands (`memory list`, `memory add`, `memory delete`, `memory clear`)
* 📋 Conversation summarization
* 📋 Unit tests for `MemoryExtractionService`
* 📋 HNSW index for memories (Phase 2.5)

---

# 📚 Phase 3 — RAG Engine 🔨 In Progress

**Target:** `v0.3.0`
**Goal:** Chat with your own documents.

## Foundation 🔨

* ✅ `documents` table (V12)
* ✅ `document_chunks` table with pgvector (V13)
* ✅ `Document` entity + `DocumentChunk` entity
* ✅ `DocumentRepository` + `DocumentChunkRepository`
* 🔨 `DocumentProcessingService` (chunking)
* 🔨 `DocumentEmbeddingService` (embed chunks)
* 🔨 `RagSearchService` (pgvector chunk search)
* 🔨 `PromptAssembler` updated (inject chunks)

## Contributor Tasks 📋

* 📋 REST API: `POST /api/v1/documents` (upload)
* 📋 REST API: `GET /api/v1/documents` (list)
* 📋 REST API: `DELETE /api/v1/documents/{id}`
* 📋 CLI: `doc upload`, `doc list`, `doc delete`
* 📋 PDF text extraction (`Apache PDFBox`)
* 📋 Markdown parsing
* 📋 Tests

---

# 🔧 Phase 4 — Tool Engine 📋 Planned (`v0.4.0`)

* 📋 `@Tool` annotation framework (Spring AI)
* 📋 `DateTimeTool`
* 📋 `CalculatorTool`
* 📋 `WeatherTool` (OpenWeatherMap Free API)
* 📋 `WebSearchTool` (DuckDuckGo)
* 📋 MCP Server (`@EnableMcpServer`)
* 📋 MCP Client connections

---

# 🎙️ Phase 5 — Voice 📋 Planned (`v0.5.0`)

* 📋 Browser speech-to-text (Web Speech API)
* 📋 Local Whisper (via Ollama)
* 📋 Text-to-speech output
* 📋 Voice conversation loop
* 📋 Wake-word detection

---

# 🤖 Phase 6 — Agents 📋 Planned (`v0.6.0`)

* 📋 ReACT pattern (`Reason → Act → Observe`)
* 📋 `AgentPlanner` + `AgentExecutor`
* 📋 Multi-step workflow persistence
* 📋 Multi-agent collaboration

---

# 🌐 Phase 7 — Web UI 💭 Considering (`v1.0.0`)

* 💭 Angular 21 + Angular Material
* 💭 Real-time streaming chat
* 💭 Document upload UI
* 💭 Memory management panel
* 💭 Agent dashboard

---

# ❌ What We Will NOT Build

| Won't Build                | Why                            |
| -------------------------- | ------------------------------ |
| Cloud SaaS product         | Jarvis is self-hosted          |
| Our own LLM                | We orchestrate existing models |
| Central telemetry          | No data collection             |
| Microservices from day one | Monolith first                 |

---

# 🗓️ Timeline

| Version  | Target                        |
| -------- | ----------------------------- |
| `v0.1.0` | ✅ Released — June 2026        |
| `v0.2.0` | Q3 2026 — Memory System       |
| `v0.3.0` | Q3 2026 — RAG Engine          |
| `v0.4.0` | Q4 2026 — Tool Engine         |
| `v0.5.0` | Q1 2027 — Voice Assistant     |
| `v0.6.0` | Q2 2027 — Agents              |
| `v1.0.0` | 2028 — Web UI + Full Platform |

---

# 🤝 How To Help

| Area                       | Phase   | Skill Required    |
| -------------------------- | ------- | ----------------- |
| Memory REST API            | Phase 2 | Spring WebFlux    |
| CLI memory commands        | Phase 2 | Spring Shell 4    |
| Conversation summarization | Phase 2 | Spring AI         |
| Document upload API        | Phase 3 | Spring WebFlux    |
| PDF text extraction        | Phase 3 | Apache PDFBox     |
| CLI document commands      | Phase 3 | Spring Shell 4    |
| Unit tests                 | All     | JUnit 5           |
| Architecture diagrams      | All     | draw.io           |
| Documentation              | All     | Technical Writing |

---

*Last reviewed: June 2026*
