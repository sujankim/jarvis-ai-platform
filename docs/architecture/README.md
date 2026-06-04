# Jarvis AI Platform — Architecture

## System Overview

The diagram below shows the complete request flow from user input to AI response.

## System Architecture

![System Architecture](diagrams/system-overview.png)

## Request Flow

```text
User (CLI)
    ↓
Spring Shell 4.0 (jarvis:> prompt)
    ↓
ChatStreamController
(POST /api/v1/chat/stream)
    ↓
AiOrchestrator
(coordinates everything)
    ↓
PromptAssembler + ProviderRouter
    ↓                    ↓
OllamaProvider      GeminiProvider
(llama3.1:8b)      (gemini-2.0-flash)
(primary)          (fallback)
    ↓
PostgreSQL
(sessions + messages)
```

## Key Components

| Component               | Responsibility                              |
| ----------------------- | ------------------------------------------- |
| AiOrchestrator          | Coordinates the entire chat flow            |
| PromptAssembler         | Builds the complete prompt from all context |
| ProviderRouter          | Routes to Ollama first, Gemini as fallback  |
| OllamaProvider          | Connects to local Ollama instance           |
| GeminiProvider          | Connects to Google Gemini cloud API         |
| ChatStreamController    | Handles SSE streaming to clients            |
| JwtAuthenticationFilter | Validates JWT on every request              |
| SessionMemoryService    | Loads conversation history                  |

## Architecture Decisions

See the ADR (Architecture Decision Records) in `decisions/` for reasoning behind key choices:

- ADR-001: UUID v7
- ADR-002: WebFlux
- ADR-003: Local First

## Diagram Source

The source diagram is available at:

`diagrams/system-overview.drawio`

You can edit it using diagrams.net (Draw.io).
