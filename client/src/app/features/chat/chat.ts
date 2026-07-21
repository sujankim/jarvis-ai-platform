import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  AfterViewChecked,
  ViewChild,
  ElementRef
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MarkdownModule } from 'ngx-markdown';
import { ChatService }
  from '../../core/services/chat.service';
import { ChatSession }
  from '../../core/models/session.model';
import { Message }
  from '../../core/models/message.model';

/**
 * Chat page — main feature of Jarvis UI.
 *
 * Layout:
 *   Left:  session sidebar (list + new session btn)
 *   Right: chat window (messages + input)
 *
 * SSE streaming via ChatService.streamChat()
 * which uses fetch() + ReadableStream.
 *
 * Tokens appended to the last message in real time.
 * Auto-scrolls to bottom as tokens arrive.
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MarkdownModule
  ],
  templateUrl: './chat.html',
  styleUrl: './chat.scss'
})
export class Chat implements OnInit, AfterViewChecked {

  private readonly chatService = inject(ChatService);

  // Reference to message list for auto-scroll
  @ViewChild('messageList')
  messageList!: ElementRef<HTMLDivElement>;

  // ── State signals ─────────────────────────────

  readonly sessions =
    signal<ChatSession[]>([]);

  readonly activeSessionId =
    signal<string | null>(null);

  readonly messages =
    signal<Message[]>([]);

  readonly inputText = signal('');

  readonly isStreaming = signal(false);

  readonly isLoadingMessages = signal(false);

  readonly errorMessage =
    signal<string | null>(null);

  // Streaming assistant message built token by token
  readonly streamingContent = signal('');

  // ── Computed ──────────────────────────────────

  readonly activeSession = computed(() =>
    this.sessions().find(
      s => s.id === this.activeSessionId()
    ) ?? null
  );

  readonly canSend = computed(() =>
    this.inputText().trim().length > 0
    && !this.isStreaming()
  );

  // ── Lifecycle ─────────────────────────────────

  private shouldScrollToBottom = false;

  ngOnInit(): void {
    this.loadSessions();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  // ── Sessions ──────────────────────────────────

  loadSessions(): void {
    this.chatService.getSessions().subscribe({
      next: response => {
        this.sessions.set(response.data);
      },
      error: () => {
        this.errorMessage.set(
          'Failed to load sessions.'
        );
      }
    });
  }

  selectSession(session: ChatSession): void {
    if (this.isStreaming()) return;

    this.activeSessionId.set(session.id);
    this.loadMessages(session.id);
    this.errorMessage.set(null);
  }

  newSession(): void {
    if (this.isStreaming()) return;

    this.activeSessionId.set(null);
    this.messages.set([]);
    this.streamingContent.set('');
    this.errorMessage.set(null);
  }

  archiveSession(
    session: ChatSession,
    event: Event
  ): void {
    event.stopPropagation();

    this.chatService
      .archiveSession(session.id)
      .subscribe({
        next: () => {
          if (this.activeSessionId() === session.id) {
            this.newSession();
          }
          this.loadSessions();
        }
      });
  }

  // ── Messages ──────────────────────────────────

  loadMessages(sessionId: string): void {
    this.isLoadingMessages.set(true);
    this.messages.set([]);

    this.chatService
      .getSessionMessages(sessionId)
      .subscribe({
        next: response => {
          this.messages.set(response.data);
          this.isLoadingMessages.set(false);
          this.shouldScrollToBottom = true;
        },
        error: () => {
          this.isLoadingMessages.set(false);
          this.errorMessage.set(
            'Failed to load messages.'
          );
        }
      });
  }

  // ── Send message ──────────────────────────────

  sendMessage(): void {
    const text = this.inputText().trim();
    if (!text || this.isStreaming()) return;

    // Add user message to UI immediately
    const userMessage: Message = {
      id:          crypto.randomUUID(),
      sessionId:   this.activeSessionId() ?? '',
      role:        'USER',
      content:     text,
      modelName:   null,
      totalTokens: null,
      durationMs:  null,
      error:       false,
      createdAt:   new Date().toISOString()
    };

    this.messages.update(msgs =>
      [...msgs, userMessage]
    );

    this.inputText.set('');
    this.isStreaming.set(true);
    this.streamingContent.set('');
    this.errorMessage.set(null);
    this.shouldScrollToBottom = true;

    this.chatService.streamChat(
      {
        sessionId: this.activeSessionId(),
        message:   text
      },

      // onSession
      (sessionId) => {
        this.activeSessionId.set(sessionId);
        // Reload sessions to show new one in sidebar
        this.loadSessions();
      },

      // onToken
      (token) => {
        this.streamingContent.update(
          c => c + token
        );
        this.shouldScrollToBottom = true;
      },

      // onDone
      () => {
        // Commit streaming content as real message
        const assistantMessage: Message = {
          id:          crypto.randomUUID(),
          sessionId:   this.activeSessionId() ?? '',
          role:        'ASSISTANT',
          content:     this.streamingContent(),
          modelName:   null,
          totalTokens: null,
          durationMs:  null,
          error:       false,
          createdAt:   new Date().toISOString()
        };

        this.messages.update(msgs =>
          [...msgs, assistantMessage]
        );

        this.streamingContent.set('');
        this.isStreaming.set(false);
        this.shouldScrollToBottom = true;
      },

      // onError
      (error) => {
        this.errorMessage.set(
          'AI response failed. Please try again.'
        );
        this.isStreaming.set(false);
        this.streamingContent.set('');
        console.error('Stream error:', error);
      }
    );
  }

  // ── Input handling ────────────────────────────

  onKeyDown(event: KeyboardEvent): void {
    // Enter sends, Shift+Enter adds new line
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  onInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.inputText.set(target.value);
    this.autoResizeTextarea(target);
  }

  private autoResizeTextarea(
    el: HTMLTextAreaElement
  ): void {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 200) + 'px';
  }

  // ── Scroll ────────────────────────────────────

  private scrollToBottom(): void {
    try {
      const el = this.messageList?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    } catch {
      // ignore
    }
  }

  // ── Helpers ───────────────────────────────────

  isUserMessage(msg: Message): boolean {
    return msg.role === 'USER';
  }

  trackByMessage(
    index: number,
    msg: Message
  ): string {
    return msg.id;
  }

  trackBySession(
    index: number,
    session: ChatSession
  ): string {
    return session.id;
  }

  formatSessionTitle(session: ChatSession): string {
    return session.title ?? 'New conversation';
  }
}
