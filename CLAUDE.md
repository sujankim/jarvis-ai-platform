# Jarvis AI Platform — Claude Instructions

## Project Context

Jarvis is a local-first, open-source AI assistant platform built with the Java/Spring ecosystem.

GitHub: https://github.com/sujankim/jarvis-ai-platform

### Core Philosophy

* Local AI first (Ollama) — cloud providers as optional fallback
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
| Phase 5 | ✅ Core Complete | Voice Assistant   |
| Phase 6 | 🔨 Next         | Agents            |
| Phase 7 | 📋 Planned      | Web UI            |

---

# AI Architecture Overview

```text
                   User
                     │
         ┌───────────┴───────────┐
         │                       │
       CLI                    REST API
         │                       │
         └───────────┬───────────┘
                     │
                     ▼
              AiOrchestrator
                     │
                     ▼
             PromptAssembler
                     │
     ┌───────────────┼────────────────┐
     │               │                │
Working Memory   Long-Term Memory   RAG Context
     │               │                │
     └───────────────┼────────────────┘
                     │
              Session History
                     │
                     ▼
              ProviderRouter
         ┌───────────┴────────────┐
         │                        │
   OllamaProvider          GeminiProvider
         │                        │
         └───────────┬────────────┘
                     │
              ToolRegistry
                     │
    ┌────────────────┼────────────────┐
    │                │                │
DateTimeTool   CalculatorTool   WeatherTool
                     │
               WebSearchTool
                     │
                MCP Server
                     │
                     ▼
      PostgreSQL + pgvector + Redis

VoiceController
        │
        ▼
VoiceConversationService
        │
        ├── WhisperTranscriptionService
        ├── AiOrchestrator
        └── SystemTextToSpeechService
```

---

# Tech Stack

| Layer            | Technology              |
| ---------------- | ----------------------- |
| Language         | Java 21                 |
| Framework        | Spring Boot 4.0.6       |
| AI               | Spring AI 2.0 (M8+)     |
| Web              | Spring WebFlux          |
| Database         | PostgreSQL 16           |
| Vector Database  | pgvector 0.7.4          |
| Data Access      | R2DBC                   |
| Cache            | Redis 7                 |
| Security         | Spring Security 7 + JWT |
| Password Hashing | Argon2id                |
| CLI              | Spring Shell 4          |
| AI Tools         | Spring AI @Tool + MCP   |
| Mapping          | MapStruct 1.6           |
| API Docs         | SpringDoc OpenAPI       |
| Migrations       | Flyway (V1–V15+)        |

---

# Package Structure

```text
ai.jarvis/
│
├── ai/
│   ├── orchestrator/
│   ├── prompt/
│   └── provider/
│
├── chat/
│   ├── session/
│   └── message/
│
├── cli/
│
├── memory/
│   └── session/
│
├── rag/
│   ├── extraction/
│   └── processing/
│
├── tools/
│   ├── builtin/
│   │   ├── DateTimeTool
│   │   ├── CalculatorTool
│   │   ├── WeatherTool
│   │   └── WebSearchTool
│   │
│   └── mcp/
│       └── McpServerConfig
│
├── voice/                 Phase 5
│   ├── WhisperTranscriptionService
│   ├── SystemTextToSpeechService
│   ├── VoiceConversationService
│   ├── VoiceController
│   └── exception/
│       └── VoiceException
│
├── agents/                Phase 6
│
├── security/
├── user/
├── observability/
├── common/
└── config/
```

---

# Architecture Rules

## 1. AiProvider Interface Is Sacred

* Every AI provider implements `AiProvider`
* Provider selection is handled only by `ProviderRouter`
* Providers receive `ToolRegistry`
* Never call provider implementations directly

---

## 2. Dependency Direction (STRICT)

```text
CLI → Controllers → Services → Providers → Database
```

Never bypass layers.

---

## 3. AiOrchestrator Is the Only AI Coordinator

Responsibilities:

* Load session history
* Load memory context
* Load RAG context
* Build prompts
* Select provider
* Execute tools
* Save conversation history

Controllers and CLI must never orchestrate AI workflows directly.

---

## 4. Prompt Assembly Order

```text
1. System Prompt
2. Working Memory
3. Long-Term Memory
4. RAG Context
5. Session History
6. Current User Message
```

Do not change this ordering without an architecture discussion.

---

## 5. Tool Architecture

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

All built-in tools belong under `tools/builtin`.

---

## 6. Voice Architecture

```text
VoiceController
        │
        ▼
VoiceConversationService
        │
        ├── WhisperTranscriptionService
        ├── SystemTextToSpeechService
        └── AiOrchestrator
```

### Rules

* `VoiceController` must **never** inject `WhisperTranscriptionService` directly.
* `VoiceController` must **never** inject `SystemTextToSpeechService` directly.
* `VoiceConversationService` coordinates the complete voice pipeline.
* Voice features reuse the existing `AiOrchestrator`; do not create a separate AI workflow.
* TTS playback must execute on a background `boundedElastic` scheduler.
* SSE token streaming must remain independent from TTS playback.

---

# Development Principles

Every contribution should reinforce:

1. Privacy First
2. Local First
3. Reactive First
4. Tool Driven
5. Memory Aware
6. Voice Integrated
7. Modular by Phase
8. Developer Friendly
9. Open Source First

---

# What NOT To Change

Do **not**:

* Bypass `AiOrchestrator`
* Change `PromptAssembler` ordering
* Break dependency direction
* Move built-in tools outside `tools/builtin`
* Move MCP components outside `tools/mcp`
* Inject providers directly into controllers
* Inject Whisper or TTS directly into `VoiceController`
* Introduce blocking calls into the reactive pipeline
* Introduce cloud-only features that violate the local-first philosophy
