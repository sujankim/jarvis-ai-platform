# 🗺️ Jarvis AI Platform — Roadmap

> **Last Updated:** July 2026  
> **Current Status:** Phase 7 — In Progress 🔨

---

# 📊 Status Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Complete |
| 🔨 | In Progress |
| 📋 | Planned |
| 💭 | Considering |
| ❌ | Will NOT Build |

---

# 🏗️ Phase 0 — Foundation ✅ Complete

Architecture designed, repository structured, and documentation written before any code.

---

# 🔥 Phase 1 — AI Core Foundation ✅ Released (v0.1.0)

**Released:** June 2026

## Core

- ✅ Spring Boot 4 + Spring AI 2.0 (M8)
- ✅ Ollama (local) + Gemini (cloud fallback)
- ✅ Provider abstraction (`AiProvider` interface)
- ✅ SSE token streaming (WebFlux)
- ✅ JWT authentication (Argon2id)
- ✅ PostgreSQL + Flyway (V1–V8)
- ✅ Spring Shell 4 CLI (`jarvis:>` prompt)
- ✅ Custom JLine terminal
- ✅ Session management + message persistence
- ✅ Working memory (date/time/user injection)
- ✅ Redis session caching
- ✅ First-run setup wizard
- ✅ Swagger UI + Health Indicators
- ✅ GitHub Actions CI
- ✅ Dependabot + CodeRabbit

### Contributor Tasks

- 📋 Token count display after response — #3
- 📋 Examples command — #4
- 📋 Docker image + publish workflow — #6
- 📋 OpenRouter as third provider — #7 / #68
- 📋 Rate limiting implementation — #11

---

# 🧠 Phase 2 — Memory System ✅ Core Complete

**Released:** `v0.2.0`

## Core

- ✅ Redis 7 session caching (~1ms vs ~50ms DB)
- ✅ pgvector 0.7.4 embeddings
- ✅ `memories` table (V9) + embedding column (V11)
- ✅ Unique constraint (V12)
- ✅ Memory entity + `MemoryType`
- ✅ `MemoryRepository`
- ✅ `MemoryEmbeddingRepository`
- ✅ `MemoryService`
- ✅ `EmbeddingService`
- ✅ `MemoryExtractionService`
- ✅ Prompt memory injection
- ✅ Parallel loading via `Mono.zip`
- ✅ Memory REST API (#35)
- ✅ CLI memory commands (#34)
- ✅ Memory extraction tests (#53)
- ✅ AiOrchestrator tests (#55)
- ✅ Semantic search (#47)

### Contributor Tasks

- 📋 Conversation summarization — #8 / #37
- 📋 HNSW index tuning

---

# 📚 Phase 3 — RAG Engine ✅ Core Complete

**Released:** `v0.3.0`

## Core

- ✅ Documents table (V13)
- ✅ Document chunks + pgvector + HNSW (V14)
- ✅ Document entities
- ✅ Strategy-based text extraction
- ✅ Plain text & Markdown extractors
- ✅ ChunkEmbeddingRepository
- ✅ DocumentProcessingService
- ✅ DocumentEmbeddingService
- ✅ RagSearchService
- ✅ Prompt RAG injection
- ✅ Parallel loading via `Mono.zip`
- ✅ Document REST API (#50 / #115)
- ✅ Document status endpoint (#95)

### Contributor Tasks

- 📋 CLI document commands (#51)
- 📋 PDF extraction (#52)

---

# 🔧 Phase 4 — Tool Engine ✅ Core Complete

**Released:** `v0.4.0`

## Core

- ✅ Strategy-based `JarvisTool`
- ✅ ToolRegistry
- ✅ DateTimeTool
- ✅ CalculatorTool
- ✅ WeatherTool
- ✅ WebSearchTool
- ✅ Ollama tool calling
- ✅ Gemini tool calling
- ✅ MCP Server
- ✅ Tool integration tests (#67)
- ✅ Weather/WebSearch tests (#71)

### Contributor Tasks

- 📋 CLI tool commands (#66)
- 📋 OpenRouter provider (#7 / #68)

---

# 🎙️ Phase 5 — Voice Assistant ✅ Core Complete

**Released:** `v0.5.0`

## Core

- ✅ WhisperTranscriptionService
- ✅ Local whisper.cpp support
- ✅ Groq Whisper support
- ✅ TextToSpeechService
- ✅ Cross-platform system TTS
- ✅ Voice selection
- ✅ Voice speed control
- ✅ VoiceConversationService
- ✅ Sentence-buffered speech
- ✅ Independent SSE + TTS pipelines
- ✅ VoiceController
- ✅ VoiceChatEvent
- ✅ VoiceException hierarchy
- ✅ Runtime settings API (#93)
- ✅ Voice settings API (#94)
- ✅ Voice REST API (#70)

## Endpoints

```text
POST /api/v1/voice/transcribe
POST /api/v1/voice/speak
POST /api/v1/voice/speak/bytes
POST /api/v1/voice/chat
GET  /api/v1/voice/status
GET  /api/v1/settings
PATCH /api/v1/settings/voice
```

### Contributor Tasks

- 📋 CLI voice commands (#69)
- 📋 Voice integration tests (#78)

---

# 🤖 Phase 6 — Agent System ✅ Core Complete

**Released:** `v0.6.0` (July 2026)

## Core

- ✅ ReACT pattern
- ✅ AgentPlanner
- ✅ AgentExecutor
- ✅ AgentOrchestrator
- ✅ Agent & AgentStep domain models
- ✅ Lifecycle state machine
- ✅ Compare-and-set DB updates
- ✅ SSE streaming
- ✅ Client disconnect detection
- ✅ Multi-step persistence (V15–V16)
- ✅ Uses Phase 4 tools
- ✅ Agent REST API (#85)
- ✅ AgentController tests (#116)
- ✅ Agent API integration tests (#116)

## Endpoints

```text
POST   /api/v1/agents/stream
POST   /api/v1/agents
GET    /api/v1/agents
GET    /api/v1/agents/{id}
GET    /api/v1/agents/{id}/steps
DELETE /api/v1/agents/{id}
```

### Contributor Tasks

- 📋 CLI agent commands (#84)

---

# 🌐 Phase 7 — Web UI 🔨 In Progress (v1.0.0)

**Status:** Framework vote active — July 14, 2026

## Framework Options

- 💭 Angular 21 + Angular Material
- 💭 React 18 + TypeScript
- 💭 Vue 3 + TypeScript
- 💭 Next.js

## Planned Features

- 💭 Streaming chat
- 💭 Session sidebar
- 💭 Memory management
- 💭 Document upload
- 💭 Agent dashboard
- 💭 Browser voice interface
- 💭 Settings panel
- 💭 JWT authentication

## Backend Ready

| Endpoint | Status |
|----------|--------|
| GET /api/v1/settings | ✅ Done (#93) |
| PATCH /api/v1/settings/voice | ✅ Done (#94) |
| GET /api/v1/documents/{id}/status | ✅ Done (#95) |

Sub-issues will be opened after the framework vote.

---

# 🔧 Phase 8 — Local Workstation Hub 📋 Planned

**Target:** `v0.8.0` — After Phase 7 Web UI ships

**Vision:** Extend the ReACT `AgentExecutor` to orchestrate industry-standard open-source command-line tools already installed on the user's machine. No new infrastructure—just new `JarvisTool` implementations following the existing tool engine architecture.

> **Everything in Phase 8 runs 100% locally.**  
> No cloud. No telemetry. No new dependencies beyond what the user already has installed.

---

## 🎬 Media Processing Tools

### FfmpegTool — Natural Language Video & Audio Editing

```text
User: "Trim the last 10 seconds of recording.wav"
Jarvis → FfmpegTool → ffmpeg command → output.wav

User: "Compress this video to under 50MB"
Jarvis → FfmpegTool → ffmpeg command → compressed.mp4
```

**Implementation**

- Wraps the FFmpeg CLI via `ProcessBuilder`
- Follows the same pattern as `SystemTextToSpeechService`
- Requires FFmpeg to be installed
    - Linux/macOS: commonly pre-installed or easily available
    - Windows: documented installation step
- Prevents shell injection through strict input sanitization

---

### ImageMagickTool — Batch Image Processing

```text
User: "Convert all PNGs in this folder to WebP"

User: "Resize this image to 1920x1080"

User: "Strip metadata from these photos"
```

**Implementation**

- Wraps the ImageMagick CLI
- Converts natural-language requests into ImageMagick command chains
- Supports batch image operations

---

### SoxTool — Audio Cleanup Before Transcription

```text
Automatic noise removal before WhisperTranscriptionService

User: "Clean up this recording before transcribing"
```

**Benefits**

- Integrates directly into the voice pipeline
- Improves Whisper transcription accuracy
- Wraps the SoX CLI
- Available on most Linux systems and easily installable elsewhere

---

## 🌐 Browser Automation

### BrowserTool — Headless Chromium Research Assistant

```text
User: "Fetch the latest Spring Boot release notes"

User: "Summarize this documentation page"
```

**Implementation**

- Playwright or Selenium driving headless Chromium
- Returns rendered page content for AI summarization
- Runs entirely locally
- Privacy-respecting with no tracking

**Notes**

- Memory usage: ~200–500 MB per browser instance
- Planned for the later part of Phase 8 after media tools are stable

---

## 🔄 Cross-Device Sync

### Syncthing Integration — Local Peer-to-Peer Sync

```text
Sync memories, documents, and workspace configuration
across PC, laptop, and Android.

No cloud.
No accounts.
Local Wi-Fi only.
```

**Implementation**

- Syncthing REST API integration
- Backup and restore of memory database
- Sync uploaded documents across devices
- Sync text content only; regenerate embeddings on each device since `pgvector` embeddings are model-specific

---

## 📊 Progress Analytics

### Git Analytics Tool

```text
User: "How much did I code this week?"

User: "What files have I been working on?"
```

**Implementation**

- Wraps the Git CLI via `ProcessBuilder`
- Reads local Git history
- Generates AI-powered summaries
- No filesystem access beyond Git metadata
- Feeds insights into the memory system as contextual memories

---

### Learning Progress Dashboard

A new dashboard panel in the Phase 7 Web UI providing:

- Visual progress charts
- Chart.js or Mermaid visualizations
- Learning and productivity insights
- Powered entirely by the existing memory system
- No additional backend infrastructure required

---

# 🗓️ Phase 8 Candidate Issues

| Feature | Implementation Pattern | Difficulty |
|---------|-------------------------|------------|
| FfmpegTool | `ProcessBuilder` + `JarvisTool` | Intermediate |
| SoxTool (voice cleanup) | `ProcessBuilder` + `JarvisTool` | Beginner |
| ImageMagickTool | `ProcessBuilder` + `JarvisTool` | Beginner |
| BrowserTool (Chromium) | Playwright + `JarvisTool` | Advanced |
| Syncthing backup & restore | REST API + `JarvisTool` | Intermediate |
| Git analytics tool | `ProcessBuilder` + `JarvisTool` | Beginner |
| Progress dashboard panel | Chart.js (Web UI) | Intermediate |

---

**Phase 8 Goal**

Bring everyday workstation automation into Jarvis while staying true to the project's core philosophy:

> **Your AI. Your Data. Your Machine.**

# ❌ What We Will NOT Build

| Won't Build | Why |
|-------------|-----|
| Cloud SaaS | Self-hosted only |
| Our own LLM | Uses existing models |
| Central telemetry | Privacy-first |
| Microservices from day one | Monolith-first |
| VSCodium LSP bridge | A separate product requiring its own engineering effort, release cycle, and maintenance. Better developed as an independent project built on top of Jarvis. |
| Cross-device pgvector sync | Vector embeddings are model-specific and should be regenerated on each machine. Sync raw content instead of embeddings. |
| Cloud sync of any kind | Violates Jarvis' local-first philosophy: **Your AI. Your Data. Your Machine.** |
| Camera AI / pose detection | Outside the scope of Jarvis. This belongs to a different product category focused on computer vision rather than AI orchestration. |

---

# 🗓️ Timeline

| Version | Status |
|----------|--------|
| v0.1.0 | ✅ Released |
| v0.2.0 | ✅ Complete |
| v0.3.0 | ✅ Complete |
| v0.4.0 | ✅ Complete |
| v0.5.0 | ✅ Complete |
| v0.6.0 | ✅ Complete |
| v1.0.0 | 🔨 In Progress |

---

# 🤝 How To Help

| Area | Phase | Skill |
|------|-------|------|
| CLI Tool Commands | Phase 4 | Spring Shell |
| OpenRouter Provider | Phase 4 | Spring AI |
| CLI Voice Commands | Phase 5 | Spring Shell |
| Voice Integration Tests | Phase 5 | JUnit 5 |
| CLI Document Commands | Phase 3 | Spring Shell |
| PDF Extraction | Phase 3 | Apache PDFBox |
| CLI Agent Commands | Phase 6 | Spring Shell |
| Rate Limiting | Phase 1 | Spring WebFlux |
| Token Count Display | Phase 1 | Spring Shell |
| Docker Image | Phase 1 | Docker |
| Web UI | Phase 7 | Angular / React / Vue |
| Architecture Diagrams | All | draw.io |
| Documentation | All | Technical Writing |

---

# 📦 Complete Issue Tracker

| Issue | Title | Phase | Status |
|------|------|------|------|
| #3 | Token count display | Phase 1 | 📋 Open |
| #4 | Examples command | Phase 1 | 📋 Open |
| #6 | Docker image + publish | Phase 1 | 📋 Open |
| #7 / #68 | OpenRouter provider | Phase 4 | 📋 Open |
| #8 | Conversation summarization | Phase 2 | 📋 Open |
| #11 | Rate limiting | Phase 1 | 📋 Open |
| #34 | CLI memory commands | Phase 2 | ✅ Merged |
| #35 | Memory REST API | Phase 2 | ✅ Merged |
| #37 | Conversation summarization | Phase 2 | 📋 Open |
| #47 | Semantic search | Phase 2 | ✅ Merged |
| #50 | Document REST API | Phase 3 | ✅ Merged |
| #51 | CLI document commands | Phase 3 | 📋 Open |
| #52 | PDF extraction | Phase 3 | 📋 Open |
| #53 | MemoryExtractionService tests | Phase 2 | ✅ Merged |
| #55 | AiOrchestrator tests | Phase 1 | ✅ Merged |
| #66 | CLI tool commands | Phase 4 | 📋 Open |
| #67 | Tool integration tests | Phase 4 | ✅ Merged |
| #69 | CLI voice commands | Phase 5 | 📋 Open |
| #70 | Voice REST API | Phase 5 | ✅ Merged |
| #71 | WeatherTool + WebSearch tests | Phase 4 | ✅ Merged |
| #78 | Voice integration tests | Phase 5 | 📋 Open |
| #84 | CLI agent commands | Phase 6 | 📋 Open |
| #85 | Agent integration tests | Phase 6 | ✅ Merged |
| #93 | GET /settings | Phase 7 | ✅ Merged |
| #94 | PATCH /settings/voice | Phase 7 | ✅ Merged |
| #95 | GET /documents/{id}/status | Phase 7 | ✅ Merged |
| #114 | CLI memory commands PR | Phase 2 | ✅ Merged |
| #115 | Document REST API PR | Phase 3 | ✅ Merged |
| #116 | Agent tests PR | Phase 6 | ✅ Merged |

---
Last reviewed: July 2026
---
> **Your AI. Your Data. Your Machine.**