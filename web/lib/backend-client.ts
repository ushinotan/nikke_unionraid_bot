import { NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const BACKEND_TIMEOUT_MS = 5000;

export interface BackendErrorResponse {
  error: string;
  message: string;
  timestamp?: string;
}

export class BackendError extends Error {
  constructor(
    public status: number,
    public errorData: BackendErrorResponse | null,
    message: string
  ) {
    super(message);
    this.name = "BackendError";
  }
}

export async function fetchFromBackend<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const url = `${BACKEND_URL}${path}`;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), BACKEND_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
      cache: "no-store",
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      let errorData: BackendErrorResponse | null = null;
      try {
        errorData = await response.json();
      } catch {
        // ignore
      }

      throw new BackendError(
        response.status,
        errorData,
        errorData?.message || `Backend request failed: ${response.status}`
      );
    }

    return await response.json();
  } catch (error) {
    clearTimeout(timeoutId);

    if (error instanceof BackendError) {
      throw error;
    }

    if (error instanceof Error && error.name === "AbortError") {
      console.error(`[Backend] Request timeout: ${url}`);
      throw new BackendError(
        504,
        null,
        "Backend request timeout"
      );
    }

    console.error(`[Backend] Connection failed: ${url}`, error);
    throw new BackendError(
      503,
      null,
      "Backend unavailable"
    );
  }
}

export function handleBackendError(error: unknown): NextResponse {
  if (error instanceof BackendError) {
    const isClientError = error.status >= 400 && error.status < 500;

    if (isClientError && error.errorData) {
      return NextResponse.json(error.errorData, { status: error.status });
    }

    return NextResponse.json(
      {
        error: error.status === 504 ? "GATEWAY_TIMEOUT" : "BACKEND_ERROR",
        message: error.message,
      },
      { status: error.status }
    );
  }

  console.error("[Backend] Unexpected error:", error);
  return NextResponse.json(
    { error: "INTERNAL_ERROR", message: "An unexpected error occurred" },
    { status: 500 }
  );
}

export function validateSnowflakeId(id: string, paramName: string): string | null {
  if (!/^\d+$/.test(id)) {
    return `Invalid ${paramName} format: must be numeric`;
  }
  return null;
}

export function validateNumericId(id: string, paramName: string): string | null {
  if (!/^\d+$/.test(id)) {
    return `Invalid ${paramName} format: must be numeric`;
  }
  return null;
}
