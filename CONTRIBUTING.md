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

# 🚀 Before You Start

Please read the following documents before contributing:

- [README.md](README.md) — understand what Jarvis is
- [ROADMAP.md](ROADMAP.md) — understand where it is going
- [docs/architecture/](docs/architecture/) — understand how it is built
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — community standards

---

# 🛠️ Ways to Contribute

There are many ways to help improve Jarvis.

## 🐛 Report Bugs

Before opening a bug report:

1. Check existing [Issues](https://github.com/sujankim/jarvis-ai-platform/issues) first
2. Verify the issue still exists on the latest version
3. Include reproduction steps
4. Include logs and environment details

Please include:

* Operating system
* Java version
* Docker version
* Ollama version
* Error logs
* Steps to reproduce

Use the bug report template:

```text id="yzzxos"
.github/ISSUE_TEMPLATE/bug_report.md
```

---

## 💡 Suggest Features

Before requesting a feature:

1. Check the [ROADMAP.md](ROADMAP.md) — might already be planned
2. Search existing issues and discussions
3. Explain the problem being solved
4. Describe the real-world use case

Use the feature request template:

```text id="0d0lmv"
.github/ISSUE_TEMPLATE/feature_request.md
```

---

## 📝 Improve Documentation

Documentation contributions are highly valuable.

Examples:

* Fix typos
* Improve explanations
* Add architecture diagrams
* Add tutorials
* Improve setup instructions
* Add code comments

No coding experience required.

---

## 🔧 Contribute Code

### Contribution Workflow

1. Find an issue labeled:

    * `good first issue`
    * `help wanted`
    * `documentation`

2. Comment on the issue before starting work

3. Fork the repository

4. Create a feature branch

5. Implement your changes

6. Submit a Pull Request

---

# 💻 Development Setup

## 📋 Prerequisites

| Tool   | Version | Install                               |
| ------ | ------- | ------------------------------------- |
| Java   | 21+     | [adoptium.net](https://adoptium.net/) |
| Docker | Latest  | [docker.com](https://docker.com/)     |
| Ollama | Latest  | [ollama.ai](https://ollama.ai/)       |
| Git    | Latest  | [git-scm.com](https://git-scm.com/)   |

---

## 🤖 Pull Required Models

```bash
# Chat model (required)
ollama pull llama3.1:8b

# Embedding model (required for Phase 2+)
ollama pull nomic-embed-text
```

---

## 🚀 Setup Steps

### 1️⃣ Fork and Clone

```bash
git clone https://github.com/YOUR_USERNAME/jarvis-ai-platform.git
cd jarvis-ai-platform
```

### 2️⃣ Configure Environment

```bash
cp .env.example .env
```

Set `JARVIS_JWT_SECRET` to any **32+ character secret string**.

Example:

```env
JARVIS_JWT_SECRET=your-super-secret-key-with-at-least-32-characters
```

### 3️⃣ Start Infrastructure

> **Note:** Uses a custom PostgreSQL image with pgvector preinstalled.

```bash
docker-compose up -d
```

### 4️⃣ Run Jarvis

```bash
cd server
./mvnw spring-boot:run
```

### 5️⃣ First-Time Setup

```text
jarvis:> setup
jarvis:> login
jarvis:> chat
```


## 6. Verify Setup

You should see:

```text id="3s63y5"
Jarvis AI Platform v0.1.0
jarvis:>
```

---

# ✅ Verify Your Environment

## Compile Project

```bash id="tn7h9g"
./mvnw compile
```

---

## Run Tests

```bash id="kv0uh0"
./mvnw test
```

---

## Check Dependencies

```bash id="mjlwm0"
./mvnw dependency:tree
```

---

# 🏗️ Project Structure

```text id="6lz4ao"
jarvis-ai-platform/
│
├── server/
│   └── src/main/java/ai/jarvis/
│       ├── config/              # Spring configuration
│       ├── security/            # JWT + Spring Security
│       ├── user/                # User management
│       ├── chat/                # Chat sessions + messages
│       ├── ai/                  # AI orchestration core
│       │   ├── orchestrator/    # AI coordination engine
│       │   ├── provider/        # Ollama/Gemini adapters
│       │   ├── prompt/          # Prompt assembly
│       │   └── advisor/         # AI request pipeline
│       ├── memory/              # Memory system
│       ├── tools/               # Tool execution engine
│       ├── agents/              # Autonomous agents
│       ├── cli/                 # Spring Shell CLI
│       ├── observability/       # Logging + metrics
│       └── common/              # Shared utilities
├── docs/                        # Documentation
├── scripts/                     # Helper scripts
├── docker/                      # Docker configs
│
├── docker-compose.yml
├── docker-compose.dev.yml
├── README.md
└── CONTRIBUTING.md
```

---

# 📏 Coding Standards

## Java Standards

### ✅ Preferred

```java id="af4j3n"
// Use Java records for DTOs
public record UserResponse(
    UUID id,
    String username,
    String role
) {}
```

```java id="6qv9rj"
// Use builders for AI options
OllamaOptions options = OllamaOptions.builder()
    .model("llama3.1:8b")
    .temperature(0.7)
    .build();
```

```java id="vuj8qd"
// Use reactive return types
public Mono<User> findUser(UUID id) { }

public Flux<Message> getHistory(UUID sessionId) { }
```

---

### ❌ Avoid

* Using `.block()` outside CLI layer
* Returning raw `Object` from APIs
* Logging passwords, secrets, or AI conversations
* Mixing blocking and reactive code

---

# ⚡ Reactive Programming Rules

## Prefer `flatMap` for Async Operations

```java id="5m6g5z"
userRepository.findById(id)
    .flatMap(user ->
        sessionRepository.findByUserId(user.id())
    )
    .map(sessionMapper::toResponse);
```

---

## Handle Not Found Properly

```java id="cn0bhw"
userRepository.findById(id)
    .switchIfEmpty(
        Mono.error(new UserNotFoundException(id))
    );
```

---

## Handle Errors Gracefully

```java id="n79v0r"
provider.streamChat(prompt)
    .onErrorReturn(
        "I encountered an error. Please try again."
    );
```

---

# 🔌 Tool Development Standards

Good tool descriptions are critical for AI accuracy.

## ✅ Good Example

```java id="3h5xw8"
@Tool(
    description =
        "Gets current weather for a city. " +
        "Use when user asks about weather, temperature, or forecast. " +
        "Do not use for historical weather data."
)
public String getWeather(
    @ToolParam(
        description =
            "City name in English. Example: London, Kathmandu"
    )
    String city
) {
    ...
}
```

---

## ❌ Bad Example

```java id="a4hy9w"
@Tool(description = "Weather tool")
public String getWeather(String city) { }
```

---

# 📝 Commit Message Convention

Jarvis follows **Conventional Commits**.

## Examples

```text id="ftrh5x"
feat: add weather tool with OpenWeatherMap integration

fix: resolve streaming timeout on long responses

docs: update architecture diagrams

test: add integration tests for AiOrchestrator

refactor: extract provider routing logic

chore: upgrade Spring AI dependencies
```

---

## Allowed Types

| Type       | Purpose            |
| ---------- | ------------------ |
| `feat`     | New feature        |
| `fix`      | Bug fix            |
| `docs`     | Documentation      |
| `test`     | Tests              |
| `refactor` | Code restructuring |
| `chore`    | Maintenance        |

---

# 🔄 Submitting Pull Requests

## Before Opening a PR

```bash id="9m2ks7"
# Sync latest changes
git fetch upstream

git rebase upstream/main

# Run tests
./mvnw test

# Verify compilation
./mvnw compile
```

---

# ✅ Pull Request Rules

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

# 📄 Pull Request Template

```text id="1mj8ju"
## What does this PR do?
[Describe changes]

## Related Issue
Closes #[issue-number]

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation
- [ ] Refactor
- [ ] Tests

## Testing
- [ ] ./mvnw test passes
- [ ] Tested locally with Ollama
- [ ] Tested on my OS
```

---

# 🌱 Good First Issues

Look for these labels:

| Label              | Meaning               |
| ------------------ | --------------------- |
| `good first issue` | Beginner-friendly     |
| `documentation`    | Docs improvements     |
| `help wanted`      | Community help needed |

These are real contributions — not placeholder tasks.

---

# 💬 Community & Support

Need help?

## Open a GitHub Discussion

Use discussions for:

* Questions
* Ideas
* Architecture discussions
* Feature brainstorming

---

## Response Time

We aim to respond within:

* Issues → 72 hours
* Pull Requests → 5 days
* Discussions → 72 hours

---

# ❤️ Thank You

Thank you for helping build an open, privacy-first AI platform for everyone.

Together, we are building:

* Local-first AI
* Open AI infrastructure
* Community-driven tooling
* A developer-focused AI ecosystem

🚀 Welcome to the Jarvis contributor community.
