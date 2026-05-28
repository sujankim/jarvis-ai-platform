# 🤖 Jarvis AI Platform

<div align="center">

### **A Modular, Local-First, Open-Source AI Assistant Platform**

Built with **Java 21**, **Spring Boot 4**, and **Spring AI 2**

---

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-brightgreen.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED.svg)](https://www.docker.com/)
[![Open Source](https://img.shields.io/badge/Open%20Source-Yes-success.svg)](#-contributing)

</div>

---

# 📖 Overview

Jarvis is not just another AI chatbot.

It is a **modular AI orchestration platform** designed to run entirely on your own machine with complete privacy and full ownership of your data.

Jarvis combines:

* 🧠 AI reasoning
* 🗂️ Memory systems
* 🔌 Tool execution
* 🤖 Autonomous agents
* 📄 Document understanding
* 🎙️ Voice interaction

into one extensible local-first platform.

---

# ✨ Core Philosophy

| Principle               | Description                         |
| ----------------------- | ----------------------------------- |
| 🔒 Privacy First        | Your data never leaves your machine |
| 🧩 Modular Architecture | Components can evolve independently |
| ⚡ Reactive by Default   | Non-blocking streaming architecture |
| 🔌 Provider Agnostic    | Swap Ollama, Gemini, OpenAI easily  |
| 🏠 Self Hosted          | No subscriptions or vendor lock-in  |
| 🌍 Open Source          | Community-driven development        |

---

# 🚀 Features

| Module             | Description                  | Status         |
| ------------------ | ---------------------------- | -------------- |
| AI Chat Engine     | Streaming conversational AI  | 🔨 In Progress |
| CLI Interface      | Terminal-first interaction   | 🔨 In Progress |
| REST API           | External integrations        | 🔨 In Progress |
| Authentication     | JWT-based security           | 🔨 In Progress |
| Memory System      | Persistent contextual memory | 📋 Planned     |
| RAG Engine         | Chat with documents          | 📋 Planned     |
| Tool Engine        | AI actions & automation      | 📋 Planned     |
| Voice Assistant    | Speech-to-text + TTS         | 📋 Planned     |
| Multi-Agent System | Autonomous AI agents         | 📋 Planned     |
| Angular Web UI     | Modern frontend dashboard    | 📋 Planned     |
| Plugin SDK         | Community extensions         | 📋 Planned     |

---

# 🏗️ System Architecture

```text
Spring Shell CLI / REST API (Swagger)
              ↓
    Spring Boot 4 — AI Engine
              ↓
   ┌──────────┴──────────┐
   AI Orchestrator    Memory
   (Spring AI 2.0)   (PostgreSQL)
              ↓
       Ollama (local)
       llama3.1:8b


```

---

# 🛠️ Tech Stack

| Layer              | Technology               |
| ------------------ | ------------------------ |
| Language           | Java 21                  |
| Framework          | Spring Boot 4.0.6        |
| AI Framework       | Spring AI 2.0            |
| API Layer          | Spring WebFlux           |
| Security           | Spring Security 7 + JWT  |
| Database           | PostgreSQL 16            |
| Vector Storage     | pgvector                 |
| Reactive Database  | R2DBC                    |
| Database Migration | Flyway                   |
| Local AI           | Ollama                   |
| Cloud AI           | Google Gemini            |
| CLI                | Spring Shell 4           |
| Mapping            | MapStruct                |
| Documentation      | SpringDoc OpenAPI        |
| Testing            | JUnit 5 + Testcontainers |
| Containerization   | Docker + Docker Compose  |

---

# ⚡ Quick Start

## Prerequisites

| Tool     | Required |
| -------- | -------- |
| Java 21+ | ✅        |
| Docker   | ✅        |
| Ollama   | ✅        |

---

## 1. Clone Repository

```bash
git clone https://github.com/sujankim/jarvis-ai-platform.git

cd jarvis-ai-platform
```

---

## 2. Pull Local AI Model

```bash
ollama pull llama3.1:8b
```

---

## 3. Configure Environment

```bash
cp .env.example .env
```

Update your secrets inside `.env`.

Example:

```env
JARVIS_JWT_SECRET=your-secret-key
```

---

## 4. Start Infrastructure

```bash
docker compose up -d
```

---

## 5. Run Backend Server

```bash
cd server

./mvnw spring-boot:run
```

---

## 6. Start Using Jarvis

```bash
jarvis:> login

jarvis:> chat

You: Hello Jarvis

Jarvis: Hello! I'm your local AI assistant.
```

---

# 🔌 AI Provider Support

Jarvis is designed with a provider abstraction layer.

Supported providers:

| Provider         | Status     |
| ---------------- | ---------- |
| Ollama           | ✅          |
| Google Gemini    | 🔨         |
| OpenAI           | 📋 Planned |
| Anthropic Claude | 📋 Planned |
| LM Studio        | 📋 Planned |

Switch providers using configuration only.

---

# 🧠 Long-Term Vision

Jarvis aims to become a complete personal AI operating system:

* Personal memory
* Autonomous workflows
* AI-powered productivity
* Local reasoning engine
* Multi-agent collaboration
* Voice-controlled assistant
* Developer automation
* Knowledge management system

---

# 📍 Roadmap

See [ROADMAP.md](ROADMAP.md) for detailed milestones and development plans.

---

# 📸 Screenshots

> Screenshots and demos will be added as development progresses.

Future previews:

* Angular dashboard
* CLI streaming chat
* Voice assistant
* Document RAG system
* Agent workflows

---

# 🧪 Development

## Run Tests

```bash
./mvnw test
```

## Run With Docker

```bash
docker compose up --build
```

---

# 🤝 Contributing

Contributions are welcome from everyone.

## Ways to Contribute

* 🐛 Report bugs
* 💡 Suggest features
* 📖 Improve documentation
* 🔧 Submit pull requests
* ⭐ Star the repository

---

## Development Workflow

1. Fork the repository
2. Create a feature branch
3. Commit changes
4. Push your branch
5. Open a Pull Request

---

## Good First Issues

Look for labels:

* `good first issue`
* `help wanted`
* `documentation`

Perfect for new contributors.

---

# 🔒 Privacy

Jarvis follows a strict privacy-first philosophy.

## By Default:

* No telemetry
* No tracking
* No analytics
* No cloud dependency
* No data collection

Your AI runs on your machine.

Your conversations stay yours.

---

# 📄 License

Licensed under the Apache License 2.0.

See [LICENSE](LICENSE) for more information.

---

# 🌟 Support The Project

If you find Jarvis useful:

* ⭐ Star the repository
* 🍴 Fork the project
* 🧠 Contribute ideas
* 🔧 Submit improvements

---

<div align="center">

## Built by Sujan and the Open Source Community

### Your AI. Your Data. Your Machine.

### ⭐ Star this repo if Jarvis helps you!
</div>
