/**
 * Maps to: ai.jarvis.agents.AgentStatus
 */
export type AgentStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * Maps to: ai.jarvis.agents.AgentStepType
 */
export type AgentStepType =
  | 'THINK'
  | 'ACT'
  | 'OBSERVE'
  | 'FINAL';

/**
 * Maps to: ai.jarvis.agents.AgentStepStatus
 */
export type AgentStepStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'DONE'
  | 'FAILED';

/**
 * Maps to: ai.jarvis.agents.AgentStepResponse
 */
export interface AgentStep {
  id: string;
  stepIndex: number;
  stepType: AgentStepType;
  toolName: string | null;
  output: string | null;
  status: AgentStepStatus;
  durationMs: number | null;
  createdAt: string;
  completedAt: string | null;
}

/**
 * Maps to: ai.jarvis.agents.AgentResponse
 */
export interface Agent {
  id: string;
  goal: string;
  status: AgentStatus;
  finalAnswer: string | null;
  stepCount: number;
  errorMessage: string | null;
  durationMs: number | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  steps: AgentStep[] | null;
}

/**
 * Maps to: ai.jarvis.agents.AgentRequest
 */
export interface AgentRequest {
  goal: string;
  sessionId?: string | null;
}

/**
 * SSE events from POST /api/v1/agents/stream
 *
 * event: think   → {"step":0,"data":"reasoning"}
 * event: act     → {"step":0,"data":"input","tool":"name"}
 * event: observe → {"step":0,"data":"result","tool":"name"}
 * event: final   → {"step":1,"data":"final answer"}
 * event: error   → {"step":-1,"data":"error message"}
 * event: done    → "[DONE]"
 */
export interface AgentStreamEvent {
  type: 'think' | 'act' | 'observe' | 'final' | 'error' | 'done';
  step: number;
  data: string;
  tool?: string | null;
}
