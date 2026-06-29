# Phase 7 — Web UI Page Plan

## Tech Stack (TBD — decision July 14, 2026)
Options: Angular / React / Vue / Next.js

## Pages Overview

### 1. Login Page
Route: /login
Purpose: JWT authentication

API Calls:
- POST /api/v1/auth/register (first time)
- POST /api/v1/auth/login

State:
- JWT token → localStorage
- Username, role, userId

Flow:
1. User enters username + password
2. POST /auth/login
3. Store accessToken in localStorage
4. Redirect to /chat

Components needed:
- LoginForm
- RegisterForm (toggle)
- ErrorMessage

---

### 2. Chat Page (Main Page)
Route: /chat
Purpose: Primary AI conversation interface

API Calls:
- POST /api/v1/chat/stream (SSE streaming)
- GET  /api/v1/sessions (sidebar)
- DELETE /api/v1/sessions/{id} (archive)

SSE Events:
- event: session → store session ID
- event: token → append to message
- event: done → message complete

State:
- activeSessionId
- messages list
- sessions list (sidebar)
- isStreaming boolean

Components needed:
- ChatSidebar (session list)
- ChatWindow (message display)
- MessageBubble (user + AI messages)
- MessageInput (text + send button)
- StreamingIndicator (dots while AI responds)
- NewSessionButton

UX:
- Tokens appear one by one as streamed
- Auto-scroll to bottom as tokens arrive
- Session list updates after first message

---

### 3. Memory Page
Route: /memory
Purpose: View and manage long-term memories

API Calls:
- GET    /api/v1/memories
- POST   /api/v1/memories
- DELETE /api/v1/memories/{id}
- DELETE /api/v1/memories (clear all)
- GET    /api/v1/memories/count

State:
- memories list
- total count
- isLoading

Components needed:
- MemoryList
- MemoryCard (type badge, content, delete button)
- AddMemoryForm (type selector + content input)
- MemoryCountBadge
- ClearAllButton (with confirm dialog)
- EmptyState

Memory types to display with color badges:
FACT → blue
GOAL → green
PREFERENCE → purple
CONTEXT → orange
EVENT → red

---

### 4. Documents Page (RAG)
Route: /documents
Purpose: Upload and manage RAG documents

API Calls:
- POST   /api/v1/documents (upload)
- GET    /api/v1/documents
- GET    /api/v1/documents/{id}
- DELETE /api/v1/documents/{id}

Document status display:
PENDING    → spinner
PROCESSING → progress bar
READY      → green checkmark
FAILED     → red X with error

State:
- documents list
- uploadProgress (0-100)
- isUploading

Components needed:
- DocumentList
- DocumentCard (filename, status, chunks, delete)
- UploadZone (drag + drop)
- UploadProgress
- StatusBadge
- EmptyState ("Upload your first document")

UX:
- Drag and drop file upload
- Real-time status polling (every 2s while PROCESSING)
- Show chunk count when READY
- Supported formats: PDF, TXT, Markdown

---

### 5. Agents Page
Route: /agents
Purpose: Start and monitor AI agent tasks

API Calls:
- POST /api/v1/agents/stream (SSE)
- GET  /api/v1/agents
- GET  /api/v1/agents/{id}
- DELETE /api/v1/agents/{id} (cancel)

SSE Events from /agents/stream:
- event: think   → show THINK step
- event: act     → show ACT step + tool name
- event: observe → show OBSERVE step
- event: final   → show FINAL answer
- event: done    → stream complete

State:
- agents list (history)
- activeAgent (currently running)
- liveSteps (accumulating during stream)
- isRunning boolean

Components needed:
- AgentInput (goal text area + start button)
- AgentStepDisplay (live ReACT visualization)
    - ThinkStep (lightbulb icon)
    - ActStep (tool icon + tool name)
    - ObserveStep (eye icon + result)
    - FinalStep (checkmark + answer)
- AgentHistory (past agents list)
- AgentCard (goal, status, step count)
- CancelButton (for running agents)

UX:
- Each step appears with animation as SSE arrives
- THINK → ACT → OBSERVE sequence visible
- Final answer highlighted prominently
- History shows all past agents

---

### 6. Voice Page
Route: /voice
Purpose: Voice conversation interface

API Calls:
- POST /api/v1/voice/chat (multipart audio → SSE)
- POST /api/v1/voice/transcribe (test transcription)
- POST /api/v1/voice/speak (test TTS)
- GET  /api/v1/voice/status

Browser APIs needed:
- MediaRecorder (record audio)
- AudioContext (play audio)

State:
- isRecording boolean
- isProcessing boolean
- transcription string
- voiceStatus (transcription + TTS available)

Components needed:
- VoiceStatusCard (Whisper + TTS availability)
- RecordButton (hold to record)
- WaveformVisualizer (while recording)
- TranscriptionDisplay (what AI heard)
- ResponseDisplay (AI text response)
- AudioPlayer (play TTS response)

UX Flow:
1. User holds record button
2. MediaRecorder captures audio
3. Release → POST /voice/chat with audio file
4. SSE events stream AI response tokens
5. Audio plays when TTS completes

---

### 7. Settings Page
Route: /settings
Purpose: Configure Jarvis behavior

Sections:

A. Voice Settings
→ JARVIS_VOICE_NAME (text input)
→ JARVIS_VOICE_SPEED (slider 0.5-2.0)
→ Test TTS button (POST /voice/speak)

B. Provider Status
→ GET /actuator/health
→ Show Ollama status (UP/DOWN)
→ Show Redis status (UP/DOWN)
→ Show PostgreSQL status (UP/DOWN)
→ Current model name

C. Profile
→ Display username
→ Display role (ADMIN/USER)
→ Logout button

Components needed:
- VoiceSettingsPanel
- ProviderStatusCard
- SystemHealthIndicator
- ProfileCard
- LogoutButton

---

## Shared Components (All Pages)

- NavSidebar (links to all pages)
- TopBar (username + logout)
- LoadingSpinner
- ErrorBoundary
- ConfirmDialog
- ToastNotification
- EmptyState
- AuthGuard (redirect to /login if no JWT)

---

## Authentication Flow

Every page (except /login):
1. Check localStorage for accessToken
2. If missing → redirect to /login
3. Add Authorization: Bearer {token} to all requests
4. If 401 received → clear token → redirect to /login

---

## API Base URL

Development: http://localhost:8080
Production:  same origin as frontend

---

## Real-time Features Summary

| Page | Real-time | How |
|------|-----------|-----|
| Chat | Tokens streaming | SSE |
| Agents | Step-by-step progress | SSE |
| Voice | Response streaming | SSE |
| Documents | Processing status | Polling every 2s |
| Memory | None | REST only |
| Settings | Health status | REST only |

---

## Missing Backend Endpoints (Found During Planning)

These endpoints do not exist yet and
need to be created before Phase 7:

1. GET /api/v1/settings
   → Return current voice + provider config
   → Needed by Settings page

2. PATCH /api/v1/settings/voice
   → Update voice name + speed
   → Needed by Settings page

3. GET /api/v1/documents/{id}/status
   → Poll document processing status
   → Needed by Documents page
   → (Documents list exists but no single status check)

Create these as Phase 7 backend issues.