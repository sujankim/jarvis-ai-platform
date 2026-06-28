# 🗺️ Jarvis AI Platform — Roadmap

> **Last Updated:** June 2026
> **Current Status:** Phase 5 — In Progress 🔨

---

## 📊 Status Legend

| Symbol | Meaning        |
| ------ | -------------- |
| ✅      | Complete       |
| 🔨     | In Progress    |
| 📋     | Planned        |
| 💭     | Considering    |
| ❌      | Will NOT Build |

---

## 🏗️ Phase 0 — Foundation ✅ Complete

Architecture designed, repository structured,
and documentation written before any code.

---

## 🔥 Phase 1 — AI Core Foundation ✅ Released (v0.1.0)

**Released:** June 2026

* ✅ Spring Boot 4 + Spring AI 2.0 (M8)
* ✅ Ollama (local) + Gemini (cloud fallback)
* ✅ Provider abstraction (`AiProvider` interface)
* ✅ SSE token streaming (WebFlux)
* ✅ JWT authentication (Argon2id)
* ✅ PostgreSQL + Flyway (V1–V8)
* ✅ Spring Shell 4 CLI (`jarvis:>` prompt)
* ✅ Custom JLine terminal
* ✅ Session management + message persistence
* ✅ Working memory (date/time/user injection)
* ✅ Redis session caching
* ✅ First-run setup wizard
* ✅ Swagger UI + health indicators
* ✅ GitHub Actions CI
* ✅ Dependabot + CodeRabbit

### Contributor Tasks
* 📋 Token count display after response — #3
* 📋 Examples command — #4
* 📋 Docker image + publish workflow — #6
* 📋 OpenRouter as third provider — #7
* 📋 Rate limiting implementation — #11

---

## 🧠 Phase 2 — Memory System ✅ Core Complete

**Target:** `v0.2.0`

### Core ✅ Complete
* ✅ Redis 7 session caching (~1ms vs ~50ms DB)
* ✅ pgvector 0.7.4 embeddings
* ✅ `memories` table (V9) + embedding column (V11)
* ✅ Unique constraint (V12)
* ✅ Memory entity + `MemoryType` enum
* ✅ `MemoryRepository` (R2DBC)
* ✅ `MemoryEmbeddingRepository` (JDBC/pgvector)
* ✅ `MemoryService` (CRUD + semantic search)
* ✅ `EmbeddingService` (`nomic-embed-text`)
* ✅ `MemoryExtractionService` (async AI extraction)
* ✅ `PromptAssembler` updated (memory injection)
* ✅ `AiOrchestrator` (parallel load via `Mono.zip`)
* ✅ Memory REST API (`GET`, `POST`, `DELETE`) — #35

### Contributor Tasks 📋
* 📋 CLI memory commands — #34
* 📋 Conversation summarization — #8 / #37

---

## 📚 Phase 3 — RAG Engine ✅ Core Complete

**Target:** `v0.3.0`

### Core ✅ Complete
* ✅ `documents` table (V13) + composite FK
* ✅ `document_chunks` + pgvector + HNSW index (V14)
* ✅ `Document` + `DocumentChunk` entities
* ✅ `TextExtractor` interface (Strategy Pattern)
* ✅ `PlainTextExtractor` + `MarkdownExtractor`
* ✅ `ChunkEmbeddingRepository` (JDBC/pgvector)
* ✅ `DocumentProcessingService` (chunking)
* ✅ `DocumentEmbeddingService` (async embedding)
* ✅ `RagSearchService` (semantic search)
* ✅ `PromptAssembler` updated (RAG injection step 4)
* ✅ `AiOrchestrator` (3-way parallel `Mono.zip`)

### Contributor Tasks 📋
* 📋 Document REST API
* 📋 CLI document commands
* 📋 PDF text extraction (PDFBox)
* 📋 Tests

---

## 🔧 Phase 4 — Tool Engine ✅ Core Complete

**Released:** v0.4.0 core — June 2026

### Core ✅ Complete
* ✅ `JarvisTool` marker interface (Strategy Pattern)
* ✅ `ToolRegistry` (auto-discovers all JarvisTool beans)
* ✅ `DateTimeTool` (current time, timezone queries)
* ✅ `CalculatorTool` (math expression evaluation)
* ✅ `WeatherTool` (OpenWeatherMap free tier)
* ✅ `WebSearchTool` (DuckDuckGo, no API key)
* ✅ `OllamaProvider` updated (tools support)
* ✅ `GeminiProvider` updated (tools support)
* ✅ `McpServerConfig` (expose tools via MCP protocol)
* ✅ Package structure: `tools/builtin/` + `tools/mcp/`

### Contributor Tasks 📋
* 📋 CLI tool commands (`tools`, `tool-test`)
* 📋 Tool integration tests
* 📋 WeatherTool + WebSearchTool mock HTTP tests
* 📋 OpenRouter as third provider — #7

---

## 🎙️ Phase 5 — Voice Assistant ✅ Core Complete

**Released:** v0.5.0 core — June 2026

### Core ✅ Complete
* ✅ `WhisperTranscriptionService` (Groq API + local whisper.cpp)
* ✅ `TextToSpeechService` (interface — Strategy Pattern)
* ✅ `SystemTextToSpeechService` (Windows/macOS/Linux)
* ✅ Voice selection (male/female per OS)
* ✅ Speed control (0.5x – 2.0x)
* ✅ `VoiceConversationService` (full voice loop)
* ✅ Sentence-buffered TTS (natural speech rhythm)
* ✅ Independent SSE token stream + background TTS
* ✅ `VoiceController` (5 REST endpoints)
* ✅ `VoiceChatEvent` record (SESSION/TOKEN/DONE)
* ✅ VoiceException hierarchy

### Endpoints
```text
POST /api/v1/voice/transcribe  (audio → text)
POST /api/v1/voice/speak       (text → play on server)
POST /api/v1/voice/speak/bytes (text → wav bytes)
POST /api/v1/voice/chat        (audio → SSE AI response)
GET  /api/v1/voice/status      (availability check)
```
---

## 🤖 Phase 6 — Agents ✅ Core Complete

* ✅ ReACT pattern (`Reason → Act → Observe`)
* ✅ `AgentPlanner` + `AgentExecutor`
* ✅ Multi-step workflow persistence
* ✅ Multi-agent collaboration
* ✅ Agents use Phase 4 tools natively

---

## 🌐 Phase 7 — Web UI 💭 Considering (`v1.0.0`)

* 💭 Angular 21 + Angular Material
* 💭 Real-time streaming chat
* 💭 Document upload UI
* 💭 Memory management panel
* 💭 Agent dashboard
* 💭 Voice interface in browser

---

## ❌ What We Will NOT Build

| Won't Build                | Why                            |
| -------------------------- | ------------------------------ |
| Cloud SaaS product         | Jarvis is self-hosted only     |
| Our own LLM                | We orchestrate existing models |
| Central telemetry          | No data collection ever        |
| Microservices from day one | Monolith-first architecture    |

---

## 🗓️ Timeline

| Version  | Target                          |
| -------- | ------------------------------- |
| `v0.1.0` | ✅ Released — June 2026          |
| `v0.2.0` | Core ✅ — Q3 2026                |
| `v0.3.0` | Core ✅ — Q3 2026                |
| `v0.4.0` | Core ✅ — June 2026              |
| `v0.5.0` | Q3 2026 — Voice Assistant       |
| `v0.6.0` | Q4 2026 — Agents                |
| `v1.0.0` | 2027 — Web UI + Full Platform   |

---

## 🤝 How To Help

| Area                      | Phase   | Skill Required    |
| ------------------------- | ------- | ----------------- |
| CLI tool commands         | Phase 4 | Spring Shell 4    |
| Tool integration tests    | Phase 4 | JUnit 5           |
| OpenRouter provider       | Phase 4 | Spring AI         |
| CLI memory commands       | Phase 2 | Spring Shell 4    |
| Document REST API         | Phase 3 | Spring WebFlux    |
| CLI document commands     | Phase 3 | Spring Shell 4    |
| PDF text extraction       | Phase 3 | Apache PDFBox     |
| CLI voice commands        | Phase 5 | Spring Shell 4    |
| Voice REST API            | Phase 5 | Spring WebFlux    |
| Architecture diagrams     | All     | draw.io           |
| Documentation             | All     | Technical Writing |

---

*Last reviewed: June 2026*