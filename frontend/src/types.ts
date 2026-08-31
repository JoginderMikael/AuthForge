export type HealthState = "checking" | "online" | "offline";

export type ActivityEntry = {
  id: number;
  method: string;
  path: string;
  status: number;
  ok: boolean;
  durationMs: number;
  createdAt: Date;
  response: unknown;
};

export type ApiResult<T> = {
  data: T;
  status: number;
  ok: boolean;
  durationMs: number;
};

export type ClientRegistrationResponse = {
  clientId: string;
  clientSecret: string;
  name: string;
  redirectUri?: string | null;
  scopes: string[];
  tokenEndpoint: string;
};

export type AuthResponse = {
  message: string;
  success: boolean;
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  id?: string;
  email?: string;
  clientId?: string;
  roles?: string[];
};

export type MachineTokenResponse = {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
};

export type ValidationResponse = {
  valid: boolean;
};

export type HealthResponse = {
  status: string;
};
