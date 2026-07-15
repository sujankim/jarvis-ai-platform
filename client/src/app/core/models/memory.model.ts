/**
 * Maps to: ai.jarvis.memory.MemoryType
 */
export type MemoryType =
  | 'FACT'
  | 'GOAL'
  | 'PREFERENCE'
  | 'CONTEXT'
  | 'EVENT';

/**
 * Maps to: ai.jarvis.memory.MemoryResponse
 */
export interface Memory {
  id: string;
  type: MemoryType;
  content: string;
  importance: number;
  accessCount: number;
  lastAccessed: string | null;
  createdAt: string;
}

/**
 * Maps to: ai.jarvis.memory.MemoryCountResponse
 */
export interface MemoryCount {
  count: number;
}

/**
 * Maps to: ai.jarvis.memory.MemoryRequest
 */
export interface MemoryRequest {
  memoryType: MemoryType;
  content: string;
}

/**
 * Badge color per memory type.
 * Used by the memory type badge component.
 */
export const MEMORY_TYPE_COLORS: Record<MemoryType, string> = {
  FACT:       '#3b82f6',
  GOAL:       '#10b981',
  PREFERENCE: '#8b5cf6',
  CONTEXT:    '#f59e0b',
  EVENT:      '#ef4444'
};
