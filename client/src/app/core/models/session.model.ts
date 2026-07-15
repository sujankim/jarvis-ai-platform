/**
 * Maps to: ai.jarvis.chat.session.ChatSessionResponse
 */
export interface ChatSession {
  id: string;
  title: string | null;
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED';
  messageCount: number;
  totalTokens: number;
  createdAt: string;
  lastMessageAt: string | null;
}

/**
 * Maps to: ai.jarvis.chat.streaming.ChatRequest
 */
export interface ChatRequest {
  sessionId: string | null;
  message: string;
  providerId?: string | null;
}
