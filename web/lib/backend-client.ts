const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

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

  try {
    const response = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers,
      },
    });

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
    if (error instanceof BackendError) {
      throw error;
    }

    throw new BackendError(
      500,
      null,
      `Failed to connect to backend: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}
