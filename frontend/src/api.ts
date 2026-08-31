import type { ApiResult } from "./types";

const PROXY_PREFIX = "/api-proxy";

async function readResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  const startedAt = performance.now();

  try {
    const response = await fetch(`${PROXY_PREFIX}${path}`, init);
    const data = await readResponse(response);

    return {
      data: data as T,
      status: response.status,
      ok: response.ok,
      durationMs: Math.round(performance.now() - startedAt),
    };
  } catch (error) {
    return {
      data: {
        message: error instanceof Error ? error.message : "The backend could not be reached",
        success: false,
      } as T,
      status: 0,
      ok: false,
      durationMs: Math.round(performance.now() - startedAt),
    };
  }
}

export function jsonRequest(method: string, body: unknown, headers?: HeadersInit): RequestInit {
  return {
    method,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: JSON.stringify(body),
  };
}

export function errorMessage(data: unknown, status: number): string {
  if (data && typeof data === "object") {
    const record = data as Record<string, unknown>;
    if (typeof record.message === "string") return record.message;

    const fieldErrors = Object.entries(record)
      .filter(([, value]) => typeof value === "string")
      .map(([field, value]) => `${field}: ${String(value)}`);
    if (fieldErrors.length) return fieldErrors.join(" · ");
  }

  if (typeof data === "string" && data.trim()) return data;
  if (status === 0) return "The AuthForge backend is unreachable";
  return `Request failed with status ${status}`;
}

export function decodeJwt(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
  } catch {
    return null;
  }
}
