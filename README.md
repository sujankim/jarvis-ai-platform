<div align="center">

<img src="docs/Jarvis-Logo.png" alt="Jarvis AI Platform Logo" width="180"/>

# Jarvis AI Platform

### A Modular, Local-First, Open-Source AI Assistant Platform

Built with **Java 21**, **Spring Boot 4**, and **Spring AI 2.0**

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M8-brightgreen.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/sujankim/jarvis-ai-platform/releases)

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

## What Is Jarvis?

Jarvis is not just a chatbot. It is a **modular AI orchestration platform**
that runs entirely on your own machine.

**Your AI. Your Data. Your Machine.**

### Key Differences from ChatGPT

* Your conversations never leave your computer
* Completely free (runs on Ollama locally)
* Self-hosted — you own everything

---

## What Works in v0.1.0

| Feature                          | Status    |
| -------------------------------- | --------- |
| AI chat via CLI (streaming)      | ✅ Working |
| JWT authentication (Argon2id)    | ✅ Working |
| Session management + persistence | ✅ Working |
| PostgreSQL (all messages saved)  | ✅ Working |
| Ollama local AI (primary)        | ✅ Working |
| Gemini cloud AI (fallback)       | ✅ Working |
| Provider abstraction layer       | ✅ Working |
| Working memory (date/time/user)  | ✅ Working |
| Swagger UI (API testing)         | ✅ Working |
| Health monitoring                | ✅ Working |
| Spring Shell CLI commands        | ✅ Working |

---

## Quick Start

### Prerequisites

| Tool     | Install                |
| -------- | ---------------------- |
| Java 21+ | https://adoptium.net   |
| Docker   | https://www.docker.com |
| Ollama   | https://ollama.ai      |

### Step 1 — Clone

```bash
git clone https://github.com/sujankim/jarvis-ai-platform.git
cd jarvis-ai-platform
```

### Step 2 — Pull AI Model (One Time, ~5GB)

```bash
ollama pull llama3.1:8b
```

### Step 3 — Configure

```bash
cp .env.example .env

# Edit .env
# Set JARVIS_JWT_SECRET (minimum 32 characters)
```

### Step 4 — Start Database

```bash
docker-compose up -d
```

### Step 5 — Run Jarvis

```bash
cd server
./mvnw spring-boot:run
```

### Step 6 — Use Jarvis

```text
+==============================================+
|        JARVIS AI PLATFORM v0.1.0             |
+==============================================+

$> login

Username: dravin
Password:

Welcome back, Dravin! (ADMIN)

$> chat

You: Hello Jarvis!
Jarvis: Hello Dravin! How can I help you today?

You: What day is it?
Jarvis: Today is Monday, June 1st, 2026.

You: exit

$> sessions

+----+---------------------------+----------+
| #  | Title                     | Messages |
+----+---------------------------+----------+
| 1  | Hello Jarvis!             | 4        |
+----+---------------------------+----------+
```

---

## CLI Commands

```text
login           Authenticate
logout          Sign out
whoami          Show current user
chat            Interactive streaming chat
ask -m "..."    Single question
sessions        List conversations
new-session     Start fresh session
status          System health
doctor          Full diagnostics
jarvis-version  Version info
help            All commands
```

---

## Architecture

```text
Spring Shell CLI / REST API (Swagger)
              |
    Spring Boot 4 AI Engine
              |
    +----+----+----+
    |              |
OllamaProvider  GeminiProvider
(primary)       (fallback)
    |
llama3.1:8b
(your machine)
              |
         PostgreSQL
    (sessions + messages)
```

---

## Tech Stack

| Layer      | Technology               |
| ---------- | ------------------------ |
| Language   | Java 21 (LTS)            |
| Framework  | Spring Boot 4.0.6        |
| AI         | Spring AI 2.0.0-M8       |
| Web        | Spring WebFlux           |
| Security   | Spring Security 7 + JWT  |
| Password   | Argon2id                 |
| Database   | PostgreSQL 16            |
| DB Access  | R2DBC (Reactive)         |
| Migrations | Flyway                   |
| Local AI   | Ollama (llama3.1:8b)     |
| Cloud AI   | Google Gemini (Fallback) |
| CLI        | Spring Shell 4.0         |
| Mapping    | MapStruct 1.6            |
| API Docs   | SpringDoc OpenAPI 3      |

---

## Roadmap

See **[ROADMAP.md](ROADMAP.md)** for the complete plan.

| Phase  | Feature                | Status     |
| ------ | ---------------------- | ---------- |
| v0.1.0 | AI Core + CLI + JWT    | ✅ Released |
| v0.2.0 | Memory System          | 📋 Next    |
| v0.3.0 | RAG Engine             | 📋 Planned |
| v0.4.0 | Tool Engine            | 📋 Planned |
| v0.5.0 | Voice Assistant        | 📋 Planned |
| v1.0.0 | Web UI + Full Platform | 📋 Planned |

---

## Contributing

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for how to help.

Good first issues are labeled on GitHub.

---

## Privacy

* No telemetry by default
* No cloud dependency (Ollama runs locally)
* Your conversations never leave your machine
* Self-hosted — we run nothing on our end

---

## License

Apache License 2.0 — see **[LICENSE](LICENSE)**

---

<div align="center">

Built by Sujan and the Open Source Community

⭐ Star this repo if Jarvis helps you!

</div>
