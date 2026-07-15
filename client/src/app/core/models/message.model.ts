/**
 * Maps to: ai.jarvis.chat.message.MessageRole
 */
export type MessageRole =
  | 'USER'
  | 'ASSISTANT'
  | 'SYSTEM'
  | 'TOOL';

/**
 * Maps to: ai.jarvis.chat.message.MessageResponse
 */
export interface Message {
  id: string;
  sessionId: string;
  role: MessageRole;
  content: string;
  modelName: string | null;
  totalTokens: number | null;
  durationMs: number | null;
  error: boolean;
  createdAt: string;
}

/**
 * SSE events from POST /api/v1/chat/stream
 *
 * event: session → data = sessionId string
 * event: token   → data = {"t":"token text"}
 * event: done    → data = "[DONE]"
 */
export interface ChatStreamEvent {
  type: 'session' | 'token' | 'done' | 'error';
  data: string;
}
