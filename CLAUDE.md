# Jarvis AI Platform — Claude Instructions

> Architecture and development guide for AI assistants contributing to Jarvis AI Platform.

---

# Project Context

Jarvis is a **local-first, open-source AI assistant platform** built with **Java 21, Spring Boot 4, Spring AI**, and an **Angular 22 Web UI**.

**GitHub**
https://github.com/sujankim/jarvis-ai-platform

---

# Core Philosophy

Jarvis exists to give users full ownership of their AI.

Core principles:

- Local AI first (Ollama)
- Cloud providers are optional fallback only
- Your data never leaves your machine
- Privacy by architecture, not policy
- Reactive-first backend
- Modular, phase-driven development
- Open-source and developer-focused

---

# Current Phase Status

| Phase | Status | Notes |
|--------|--------|------|
| Phase 1 | ✅ Released | AI Chat + CLI |
| Phase 2 | ✅ Core Complete | Memory + pgvector |
| Phase 3 | ✅ Core Complete | RAG Engine |
| Phase 4 | ✅ Core Complete | Tool Engine + MCP |
| Phase 5 | ✅ Core Complete | Voice Assistant |
| Phase 6 | ✅ Core Complete | Agent System |
| Phase 7 | 🔨 In Progress | Angular 22 Web UI |

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

# Technology Stack

## Backend (`server/`)

| Layer | Technology |
|--------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| AI Framework | Spring AI 2.0.0-M8 |
| Web | Spring WebFlux |
| Security | Spring Security 7 |
| Authentication | JWT + Argon2id |
| Database | PostgreSQL 16 |
| Vector Database | pgvector 0.7.4 |
| Data Access | R2DBC + JDBC |
| Cache | Redis 7 |
| Migrations | Flyway (V1–V18+) |
| Local AI | Ollama |
| Chat Model | llama3.1:8b |
| Embeddings | nomic-embed-text |
| Cloud AI | Google Gemini |
| Tools | Spring AI @Tool + MCP |
| CLI | Spring Shell 4 |
| Mapping | MapStruct 1.6 |
| API Docs | SpringDoc OpenAPI |

---

## Frontend (`client/`)

| Layer | Technology |
|--------|------------|
| Framework | Angular 22 |
| Language | TypeScript 6 |
| UI Components | Angular Material 22 (partial) |
| Styling | Custom SCSS + CSS Variables |
| State | Angular Signals |
| HTTP | Angular HttpClient |
| Routing | Angular Router (lazy loading) |
| Markdown | ngx-markdown |
| Build | Angular CLI 22 + esbuild |

---

# Project Structure

```text
jarvis-ai-platform/

├── server/
│   └── src/main/java/ai/jarvis/
│       ├── ai/
│       │   ├── orchestrator/
│       │   ├── prompt/
│       │   └── provider/
│       │
│       ├── agents/
│       ├── chat/
│       ├── cli/
│       ├── common/
│       ├── config/
│       ├── memory/
│       ├── observability/
│       ├── rag/
│       ├── security/
│       ├── settings/
│       ├── tools/
│       │   ├── builtin/
│       │   └── mcp/
│       ├── user/
│       └── voice/
│
└── client/
    └── src/app/
        ├── core/
        │   ├── guards/
        │   ├── interceptors/
        │   ├── models/
        │   └── services/
        │
        ├── shared/
        │   └── components/
        │
        └── features/
            ├── agents/
            ├── chat/
            ├── documents/
            ├── login/
            ├── memory/
            ├── settings/
            └── voice/
```

---

# Backend Architecture Rules

## 1. AiProvider Interface Is Sacred

Every AI provider implements `AiProvider`.

Rules:

- Provider selection only through `ProviderRouter`
- Providers receive `ToolRegistry`
- Never inject provider implementations directly
- Never bypass ProviderRouter

---

## 2. Dependency Direction (STRICT)

```text
CLI
 ↓
Controllers
 ↓
Services
 ↓
Providers
 ↓
Database
```

Never bypass layers.

---

## 3. AiOrchestrator Is The Only AI Coordinator

Responsibilities:

- Load session history
- Load working memory
- Load long-term memory
- Load RAG context
- Assemble prompt
- Select provider
- Execute tools
- Persist conversation

Controllers and CLI **must never orchestrate AI workflows directly**.

---

## 4. Prompt Assembly Order (FIXED)

```text
1. System Prompt
2. Working Memory
3. Long-Term Memory
4. RAG Context
5. Session History
6. Current User Message
```

This order is architecture-critical.

Do not modify without an architecture discussion.

---

## 5. Tool Architecture

```text
tools/

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

Rules:

- Built-in tools belong only in `tools/builtin`
- MCP components belong only in `tools/mcp`
- Never place built-in tools elsewhere

---

## 6. Reactive Rules

Jarvis is **reactive-first**.

Rules:

- All application queries use **R2DBC**
- Vector operations use **JDBC**
- Blocking work runs on `Schedulers.boundedElastic()`
- Never block the WebFlux event loop
- Never introduce blocking calls into reactive chains

---

## 7. Voice Architecture

```text
VoiceController
        │
        ▼
VoiceConversationService
        │
        ├── WhisperTranscriptionService
        ├── AiOrchestrator
        └── SystemTextToSpeechService
```

Rules:

- VoiceController never injects Whisper directly
- VoiceController never injects TTS directly
- VoiceConversationService coordinates the pipeline
- Voice features reuse AiOrchestrator
- TTS playback runs on boundedElastic
- SSE token streaming remains independent from TTS

---

# Frontend Architecture Rules

## 1. Angular 22 Conventions

Always use Angular 22 best practices.

Required:

- Standalone components only
- Functional interceptors
- Functional guards
- `inject()` instead of constructor injection
- `@if`, `@for`, `@switch`
- Never use deprecated APIs
- Check https://v22.angular.dev before introducing new APIs

---

## 2. State Management

Use each tool for its intended purpose.

Angular Signals

- UI state
- Loading state
- Selected objects
- Component state

RxJS

- HTTP
- SSE
- Async operations
- Streaming

Never mix responsibilities.

---

## 3. HTTP Rules

- All API access through Angular HttpClient
- JWT automatically attached by authInterceptor
- Never manually attach Authorization header
- authInterceptor handles 401 redirects
- Never access localStorage directly
- Always use StorageService

---

## 4. Component Naming

Use Angular 22 naming.

```text
login.ts
chat.ts
settings.ts

NOT

login.component.ts
chat.component.ts
```

---

## 5. Routing Rules

- All feature pages lazy loaded
- Use `loadComponent`
- Only `/login` is public
- All remaining routes protected by authGuard

---

## 6. Styling Rules

Use CSS custom properties for themes.

```css
var(--bg-primary)
```

Do not use SCSS variables directly inside components.

Angular Material is limited to:

- Icons
- Buttons
- Form fields
- Dialogs

Everything else should use custom SCSS.

---

## 7. SSE Streaming

Never use EventSource.

Use:

```text
fetch()
+
ReadableStream
```

Reason:

- JWT Authorization header required
- POST body required

EventSource supports neither.

---

## 8. Models

All TypeScript interfaces belong in:

```text
src/app/core/models/
```

Rules:

- Match backend JSON exactly
- Never create inline interfaces inside components

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

## Backend

Do **not**:

- Bypass AiOrchestrator
- Change PromptAssembler ordering
- Break dependency direction
- Inject providers into controllers
- Move built-in tools outside `tools/builtin`
- Move MCP outside `tools/mcp`
- Inject Whisper directly into VoiceController
- Inject TTS directly into VoiceController
- Introduce blocking calls into reactive pipelines
- Introduce cloud-only features that violate local-first philosophy

---

## Frontend

Do **not**:

- Use deprecated Angular APIs
- Use NgModules
- Use class-based interceptors
- Use class-based guards
- Access localStorage directly
- Use `*ngIf`
- Use `*ngFor`
- Define inline TypeScript interfaces
- Use EventSource for SSE

Always use:

- Standalone components
- Functional APIs
- Angular Signals
- fetch() + ReadableStream for streaming
- StorageService
- CSS variables
- Angular 22 best practices