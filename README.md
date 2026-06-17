<div align="center">

<img src="docs/Jarvis-Logo.png" alt="Jarvis AI Platform Logo" width="180"/>

#  Jarvis AI Platform

### A Modular, Local-First, Open-Source AI Assistant Platform

Built with **Java 21**, **Spring Boot 4**, and **Spring AI 2.0**

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M8-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![pgvector](https://img.shields.io/badge/pgvector-0.7.4-purple.svg)
![Redis](https://img.shields.io/badge/Redis-7-red.svg)
![Version](https://img.shields.io/badge/version-0.2.0--SNAPSHOT-blue.svg)

</div>
---
<p align="center">
  <img src="docs/social-banner.png" alt="Jarvis AI Platform Banner">
</p>

---
<p align="center">
  <img src="docs/demo.gif" alt="Jarvis Demo">
</p>

---

## 🚀 What Is Jarvis?

Jarvis is not just a chatbot.

It is a **modular AI orchestration platform** that runs entirely on your own machine with complete privacy.

**Your AI. Your Data. Your Machine.**

### Key Differences from ChatGPT

* Your conversations **never leave your computer**
* Completely **free** (runs on Ollama locally)
* **Remembers you** across sessions (Phase 2 ✅)
* **Searches your documents** (Phase 3 — coming soon)

---

## 📈 Current Status

| Phase   | Version | Feature             | Status           |
| ------- | ------- | ------------------- | ---------------- |
| Phase 1 | v0.1.0  | AI Chat + CLI + JWT | ✅ Released       |
| Phase 2 | v0.2.0  | Memory System       | 🔨 Core Complete |
| Phase 3 | v0.3.0  | RAG Engine          | 🔨 In Progress   |
| Phase 4 | v0.4.0  | Tool Engine         | 📋 Planned       |
| Phase 5 | v0.5.0  | Voice Assistant     | 📋 Planned       |
| Phase 6 | v0.6.0  | Agent System        | 📋 Planned       |
| Phase 7 | v1.0.0  | Web UI              | 📋 Planned       |

---

## ✅ What Works Right Now

| Feature                          | Status | Since  |
| -------------------------------- | ------ | ------ |
| AI chat via CLI (streaming)      | ✅      | v0.1.0 |
| JWT authentication (Argon2id)    | ✅      | v0.1.0 |
| Session management + persistence | ✅      | v0.1.0 |
| PostgreSQL (all messages saved)  | ✅      | v0.1.0 |
| Ollama local AI (primary)        | ✅      | v0.1.0 |
| Gemini cloud AI (fallback)       | ✅      | v0.1.0 |
| Provider abstraction             | ✅      | v0.1.0 |
| Redis session caching            | ✅      | v0.2.0 |
| Long-term memory storage         | ✅      | v0.2.0 |
| Automatic memory extraction      | ✅      | v0.2.0 |
| Memory injection into prompts    | ✅      | v0.2.0 |
| pgvector semantic search         | ✅      | v0.2.0 |
| Personalized responses           | ✅      | v0.2.0 |
| Swagger UI                       | ✅      | v0.1.0 |
| Health monitoring                | ✅      | v0.1.0 |

---

# ⚡ Quick Start

## Prerequisites

| Tool   | Version |
| ------ | ------- |
| Java   | 21+     |
| Docker | Latest  |
| Ollama | Latest  |

---

## Step 1 — Clone Repository

```bash
git clone https://github.com/sujankim/jarvis-ai-platform.git
cd jarvis-ai-platform
```

---

## Step 2 — Pull AI Models

```bash
# Chat model (~5GB)
ollama pull llama3.1:8b

# Embedding model (~274MB)
ollama pull nomic-embed-text
```

---

## Step 3 — Configure Environment

```bash
cp .env.example .env
```

Edit `.env` and set:

```env
JARVIS_JWT_SECRET=your-super-secret-key-at-least-32-characters
```

---

## Step 4 — Start Infrastructure

```bash
docker-compose up -d
```

This starts:

* PostgreSQL + pgvector
* Redis

---

## Step 5 — Run Jarvis

```bash
cd server
./mvnw spring-boot:run
```

---

## Step 6 — First-Time Setup

```text
+==============================================+
|          JARVIS AI PLATFORM v0.2.0           |
+==============================================+

jarvis:> setup
jarvis:> login
jarvis:> chat

You: I am a Java developer building Jarvis
Jarvis: That is exciting! Tell me more...

You: exit

# Next session
jarvis:> chat

You: hello!
Jarvis: Welcome back! How is your Java project going?
```

---

# 💻 CLI Commands

## First Time

```bash
setup
```

Create administrator account.

---

## Authentication

```bash
login
logout
whoami
```

---

## Chat

```bash
chat
ask -m "What is Spring AI?"
```

---

## Sessions

```bash
session
new-session
switch-session
```

---

## Memory (Phase 2)

Coming soon via CLI commands.

---

## System

```bash
status
doctor
jarvis-version
about
examples
help
```

---

# 🏗 Architecture

```text
Spring Shell CLI (jarvis:> prompt)
           |
  Spring Boot 4 AI Engine
           |
  ┌────────┴────────────┐
  │                     │
AiOrchestrator     Memory System
  │                (Phase 2 ✅)
  │
PromptAssembler
  │
  ├── Working Memory (date/time/user)
  ├── Long-term Memories (pgvector)
  ├── RAG Context (Phase 3 🔨)
  └── Session History (Redis)
           |
    ProviderRouter
    │             │
OllamaProvider  GeminiProvider
(local)         (cloud fallback)
           |
PostgreSQL + pgvector    Redis
(sessions, memories,     (session cache)
documents, embeddings)
```

---

# 🛠 Tech Stack

| Layer            | Technology          |
| ---------------- | ------------------- |
| Language         | Java 21 (LTS)       |
| Framework        | Spring Boot 4.0.6   |
| AI Framework     | Spring AI 2.0.0-M8  |
| Web              | Spring WebFlux      |
| Security         | Spring Security 7   |
| Authentication   | JWT                 |
| Password Hashing | Argon2id            |
| Database         | PostgreSQL 16       |
| Vector Database  | pgvector 0.7.4      |
| Data Access      | R2DBC + JDBC        |
| Cache            | Redis 7             |
| Migrations       | Flyway              |
| Local AI         | Ollama              |
| Chat Model       | llama3.1:8b         |
| Embeddings       | nomic-embed-text    |
| Cloud AI         | Google Gemini       |
| CLI              | Spring Shell 4      |
| Mapping          | MapStruct 1.6       |
| API Docs         | SpringDoc OpenAPI 3 |

---

# 🤝 Contributing

We welcome contributions of all sizes.

### Good First Issues

* Memory REST API endpoints
* CLI memory commands
* Unit tests for MemoryService
* Architecture diagrams
* Document upload REST API
* CLI document commands

### Advanced Issues

* Conversation summarization
* pgvector HNSW index tuning
* PDF text extraction
* RAG optimization

See:

* [CONTRIBUTING.md](CONTRIBUTING.md)
* [ROADMAP.md](ROADMAP.md)

---

# 📝 Articles

* [Building a Local-First AI Assistant with Spring Boot 4 and Spring AI 2.0 — Dev.to](https://dev.to/sujankim/building-a-local-first-ai-assistant-with-spring-boot-4-and-spring-ai-20-6ci)
* [Jarvis AI Platform: Building Long-Term Memory with pgvector and Spring AI — Medium ](https://medium.com/@sujan.lamichhane32/jarvis-ai-platform-building-long-term-memory-with-pgvector-and-spring-ai-c114b79dceda)

---

# 🔒 Privacy

* No telemetry by default
* Ollama runs locally
* Conversations never leave your machine
* Self-hosted by design
* Embeddings stored locally

---

# 📄 License

Licensed under the **Apache License 2.0**.

See the [LICENSE](LICENSE) file for details.

---

<div align="center">

### Built by Sujan and the Open Source Community

⭐ **Star this repository if Jarvis helps you!**

</div>
