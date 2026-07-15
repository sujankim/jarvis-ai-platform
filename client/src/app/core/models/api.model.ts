/**
 * Wrapper for all Jarvis API responses.
 * Maps to: ai.jarvis.common.model.ApiResponse<T>
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
  timestamp: string;
}

/**
 * Maps to: ai.jarvis.common.model.ErrorResponse
 */
export interface ErrorResponse {
  errorCode: string;
  message: string;
  details: string[] | null;
  path: string;
  timestamp: string;
}
