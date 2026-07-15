/**
 * Maps to: ai.jarvis.security.auth.request.LoginRequest
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Maps to: ai.jarvis.security.auth.request.RegisterRequest
 */
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}

/**
 * Maps to: ai.jarvis.security.auth.response.TokenResponse.UserInfo
 */
export interface UserInfo {
  userId: string;
  username: string;
  displayName: string;
  role: 'ADMIN' | 'USER';
}

/**
 * Maps to: ai.jarvis.security.auth.response.TokenResponse
 */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

/**
 * Maps to: ai.jarvis.security.auth.response.RegisterResponse
 */
export interface RegisterResponse {
  userId: string;
  username: string;
  displayName: string;
  role: 'ADMIN' | 'USER';
  message: string;
}
