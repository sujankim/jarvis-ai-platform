# 🤝 Contributing to Jarvis AI Platform

First off — thank you for considering contributing to Jarvis AI Platform.

Jarvis is an open-source, privacy-first AI platform built for developers, researchers, and AI enthusiasts who believe personal AI should run locally and remain fully under user control.

Every contribution matters — whether it is code, documentation, bug reports, testing, ideas, or feedback.

---

# 📋 Table of Contents

* [Before You Start](#-before-you-start)
* [Ways to Contribute](#-ways-to-contribute)
* [Development Setup](#-development-setup)
* [Project Structure](#-project-structure)
* [Coding Standards](#-coding-standards)
* [Reactive Programming Rules](#-reactive-programming-rules)
* [Tool Development Standards](#-tool-development-standards)
* [Commit Message Convention](#-commit-message-convention)
* [Submitting Pull Requests](#-submitting-pull-requests)
* [Good First Issues](#-good-first-issues)
* [Community & Support](#-community--support)

---

# 🏗️ Project Structure

```text
jarvis-ai-platform/
│
├── server/
│   └── src/main/java/ai/jarvis/
│       ├── config/              Spring configuration
│       ├── security/            JWT + Spring Security
│       ├── user/                User management
│       ├── chat/                Chat sessions + messages
│       ├── ai/                  AI orchestration core
│       │   ├── orchestrator/    AiOrchestrator
│       │   ├── provider/        Ollama/Gemini adapters
│       │   └── prompt/          PromptAssembler
│       ├── memory/              Memory system (Phase 2)
│       ├── rag/                 RAG engine (Phase 3)
│       │   ├── extraction/      Text extractors
│       │   └── (chunking/embed) Processing pipeline
│       ├── tools/               Tool Engine (Phase 4)
│       │   ├── builtin/         Built-in tools
│       │   │   ├── DateTimeTool
│       │   │   ├── CalculatorTool
│       │   │   ├── WeatherTool
│       │   │   └── WebSearchTool
│       │   └── mcp/             MCP protocol
│       │       └── McpServerConfig
│       ├── voice/               Voice (Phase 5) 🔨
│       ├── agents/              Agents (Phase 6) 📋
│       ├── cli/                 Spring Shell CLI
│       ├── observability/       Logging + metrics
│       └── common/              Shared utilities
│
├── docs/                        Documentation + ADRs
├── docker/                      Docker configs
├── docker-compose.yml           Production setup
├── docker-compose.dev.yml       Development setup
└── .env.example                 Environment template
```

---

# 🔌 Tool Development Standards

Adding a new tool is simple — just implement `JarvisTool`.

## ✅ Correct Pattern

```java
package ai.jarvis.tools.builtin;

import ai.jarvis.tools.JarvisTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyTool implements JarvisTool {

    @Tool(
        description =
            "What this tool does. "
            + "When the AI should call it. "
            + "What it returns."
    )
    public String doSomething(
        @ToolParam(
            description =
                "What this parameter expects"
        )
        String input
    ) {

        try {
            return "result: " + input;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
```

## ❌ Bad Tool Example

```java
@Tool(description = "My tool")
public String myTool(String input) {
    throw new RuntimeException("fail");
}
```

## Tool Description Rules

```text
✅ Explain WHAT it does

✅ Explain WHEN the AI should use it

✅ List example parameter values

✅ Explain what it RETURNS

❌ Never throw exceptions to the AI model

❌ Never return null — always return a String
```

---

# 🌱 Good First Issues

| Label            | Issues Available        |
| ---------------- | ----------------------- |
| good first issue | #3, #4, Phase 4 CLI     |
| phase-2          | #34 CLI memory commands |
| phase-3          | Document REST API, CLI  |
| phase-4          | CLI tool commands       |
| testing          | All phases              |
| documentation    | Always open             |

---

# 📝 Commit Message Convention

Jarvis follows **Conventional Commits**.

## Examples

```text
feat: add weather tool with OpenWeatherMap integration

fix: resolve streaming timeout on long responses

docs: update architecture diagrams

test: add integration tests for AiOrchestrator

refactor: extract provider routing logic

chore: upgrade Spring AI dependencies
```

## Allowed Types

| Type     | Purpose            |
| -------- | ------------------ |
| feat     | New feature        |
| fix      | Bug fix            |
| docs     | Documentation      |
| test     | Tests              |
| refactor | Code restructuring |
| chore    | Maintenance        |

---

# 🔄 Submitting Pull Requests

## Before Opening a PR

```bash
git fetch upstream

git rebase upstream/main

./mvnw test

./mvnw compile
```

## Pull Request Rules

### Required

* One feature/fix per PR
* Tests must pass
* Follow coding standards
* Update documentation if needed
* Use conventional commits

### Not Allowed

* Breaking changes without discussion
* Committing secrets or API keys
* Large unrelated refactors

---

# 💬 Community & Support

Need help?

* Open a GitHub Discussion
* Ask architecture questions
* Share ideas and feedback
* Suggest improvements

---

# ❤️ Thank You

Thank you for helping build an open, privacy-first AI platform for everyone.

Together, we are building:

* Local-first AI
* Open AI infrastructure
* Community-driven tooling
* A developer-focused AI ecosystem

🚀 Welcome to the Jarvis contributor community.
