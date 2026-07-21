import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StorageService }
  from './storage.service';
import {
  ChatSession,
  ChatRequest
} from '../models/session.model';
import { Message } from '../models/message.model';
import {
  ApiResponse
} from '../models/api.model';

/**
 * Handles all chat and session API calls.
 *
 * Standard HTTP calls use Angular HttpClient.
 * SSE streaming uses fetch() + ReadableStream
 * because EventSource cannot:
 *   1. Send a POST body
 *   2. Send Authorization header
 * Both are required by our backend.
 */
@Injectable({
  providedIn: 'root'
})
export class ChatService {

  private readonly http    = inject(HttpClient);
  private readonly storage = inject(StorageService);

  // ── Sessions ──────────────────────────────────

  getSessions(): Observable<ApiResponse<ChatSession[]>> {
    return this.http.get<ApiResponse<ChatSession[]>>(
      '/api/v1/sessions'
    );
  }

  getSessionMessages(
    sessionId: string
  ): Observable<ApiResponse<Message[]>> {
    return this.http.get<ApiResponse<Message[]>>(
      `/api/v1/sessions/${sessionId}/messages`
    );
  }

  archiveSession(
    sessionId: string
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/v1/sessions/${sessionId}`
    );
  }

  // ── SSE Streaming ─────────────────────────────

  /**
   * Stream chat response via SSE.
   *
   * Uses fetch() + ReadableStream to support:
   * - POST body (message + sessionId)
   * - Authorization: Bearer JWT header
   *
   * Emits callbacks for each SSE event type:
   * - onSession: received new sessionId
   * - onToken:   received one token {"t":"..."}
   * - onDone:    stream completed
   * - onError:   stream error
   *
   * @param request   ChatRequest (message + sessionId)
   * @param onSession called with sessionId string
   * @param onToken   called with each token text
   * @param onDone    called when stream completes
   * @param onError   called on any error
   */
  streamChat(
    request: ChatRequest,
    onSession: (sessionId: string) => void,
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (error: string) => void
  ): void {

    const token = this.storage.getToken();

    if (!token) {
      onError('Not authenticated');
      return;
    }

    fetch('/api/v1/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type':  'application/json',
        'Authorization': `Bearer ${token}`,
        'Accept':        'text/event-stream'
      },
      body: JSON.stringify(request)
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(
            `HTTP ${response.status}`
          );
        }
        if (!response.body) {
          throw new Error('No response body');
        }
        return this.readStream(
          response.body.getReader(),
          onSession,
          onToken,
          onDone,
          onError
        );
      })
      .catch(err => {
        onError(
          err instanceof Error
            ? err.message
            : 'Stream failed'
        );
      });
  }

  // ── Private: SSE parser ───────────────────────

  /**
   * Reads the SSE stream line by line.
   *
   * SSE format per line:
   *   event: session\ndata: <uuid>\n\n
   *   event: token\ndata: {"t":"text"}\n\n
   *   event: done\ndata: [DONE]\n\n
   */
  private async readStream(
    reader: ReadableStreamDefaultReader<Uint8Array>,
    onSession: (sessionId: string) => void,
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (error: string) => void
  ): Promise<void> {

    const decoder = new TextDecoder();
    let buffer    = '';
    let eventType = '';

    try {
      while (true) {
        const { done, value } =
          await reader.read();

        if (done) {
          onDone();
          break;
        }

        buffer += decoder.decode(value, {
          stream: true
        });

        const lines = buffer.split('\n');

        // Keep last incomplete line in buffer
        buffer = lines.pop() ?? '';

        for (const line of lines) {

          // Event type line
          if (line.startsWith('event:')) {
            eventType = line
              .slice(6)
              .trim();
            continue;
          }

          // Data line
          if (line.startsWith('data:')) {
            const data = line
              .slice(5)
              .trim();

            this.handleEvent(
              eventType,
              data,
              onSession,
              onToken,
              onDone
            );

            eventType = '';
            continue;
          }
        }
      }
    } catch (err) {
      onError(
        err instanceof Error
          ? err.message
          : 'Stream read error'
      );
    } finally {
      reader.releaseLock();
    }
  }

  /**
   * Route each SSE event to the correct callback.
   *
   * token data format from backend: {"t":"text"}
   * Parses the JSON and extracts the "t" field.
   */
  private handleEvent(
    eventType: string,
    data: string,
    onSession: (sessionId: string) => void,
    onToken: (token: string) => void,
    onDone: () => void
  ): void {

    switch (eventType) {

      case 'session':
        onSession(data.trim());
        break;

      case 'token':
        try {
          const parsed = JSON.parse(data);
          if (parsed.t !== undefined) {
            onToken(parsed.t as string);
          }
        } catch {
          // Ignore malformed token
        }
        break;

      case 'done':
        onDone();
        break;
    }
  }
}
