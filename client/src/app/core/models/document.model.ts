/**
 * Maps to: ai.jarvis.rag.DocumentStatus
 */
export type DocumentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED';

/**
 * Maps to: ai.jarvis.rag.DocumentFileType
 */
export type DocumentFileType =
  | 'PDF'
  | 'TXT'
  | 'MARKDOWN';

/**
 * Maps to: ai.jarvis.rag.DocumentResponse
 */
export interface Document {
  id: string;
  userId: string;
  filename: string;
  fileType: DocumentFileType;
  fileSizeBytes: number;
  status: DocumentStatus;
  chunkCount: number;
  description: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Maps to: ai.jarvis.rag.DocumentStatusResponse
 * Used by GET /api/v1/documents/{id}/status
 */
export interface DocumentStatusResponse {
  id: string;
  filename: string;
  status: DocumentStatus;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
}

/**
 * Maps to: ai.jarvis.rag.DocumentUploadRequest
 */
export interface DocumentUploadRequest {
  filename: string;
  content: string;
  description?: string | null;
}
