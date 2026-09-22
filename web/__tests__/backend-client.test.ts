import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert";
import {
  fetchFromBackend,
  handleBackendError,
  validateSnowflakeId,
  validateNumericId,
  BackendError,
} from "../lib/backend-client";

global.fetch = async (url: string | URL | Request, init?: RequestInit) => {
  const urlStr = typeof url === "string" ? url : url.toString();

  if (init?.signal?.aborted) {
    const error = new Error("The operation was aborted");
    error.name = "AbortError";
    throw error;
  }

  if (urlStr.includes("/timeout")) {
    return new Promise((_, reject) => {
      setTimeout(() => {
        const error = new Error("The operation was aborted");
        error.name = "AbortError";
        reject(error);
      }, 100);
    });
  }

  if (urlStr.includes("/network-error")) {
    throw new Error("Network connection failed");
  }

  if (urlStr.includes("/api/guilds/123/raids")) {
    return {
      ok: true,
      status: 200,
      json: async () => ({ raids: [] }),
    } as Response;
  }

  if (urlStr.includes("/api/raids/456")) {
    return {
      ok: false,
      status: 404,
      json: async () => ({
        error: "NOT_FOUND",
        message: "Raid not found",
      }),
    } as Response;
  }

  if (urlStr.includes("/api/guilds")) {
    return {
      ok: true,
      status: 200,
      json: async () => ({ guilds: [] }),
    } as Response;
  }

  return {
    ok: false,
    status: 500,
    json: async () => ({ error: "UNKNOWN" }),
  } as Response;
};

describe("backend-client", () => {
  let consoleErrorSpy: typeof console.error;
  let errorLogs: unknown[][] = [];

  beforeEach(() => {
    errorLogs = [];
    consoleErrorSpy = console.error;
    console.error = (...args: unknown[]) => {
      errorLogs.push(args);
    };
  });

  afterEach(() => {
    console.error = consoleErrorSpy;
  });

  describe("fetchFromBackend", () => {
    it("should fetch successfully from backend", async () => {
      const result = await fetchFromBackend("/api/guilds");
      assert.deepStrictEqual(result, { guilds: [] });
    });

    it("should propagate 404 errors from backend", async () => {
      await assert.rejects(
        async () => {
          await fetchFromBackend("/api/raids/456");
        },
        (error: unknown) => {
          assert.ok(error instanceof BackendError);
          assert.strictEqual(error.status, 404);
          assert.strictEqual(error.errorData?.error, "NOT_FOUND");
          return true;
        }
      );
    });

    it("should handle network errors with 503 and fixed message", async () => {
      await assert.rejects(
        async () => {
          await fetchFromBackend("/network-error");
        },
        (error: unknown) => {
          assert.ok(error instanceof BackendError);
          assert.strictEqual(error.status, 503);
          assert.strictEqual(error.message, "Backend unavailable");
          assert.ok(errorLogs.some((log) => log[0]?.toString().includes("[Backend] Connection failed")));
          return true;
        }
      );
    });

    it("should handle timeout with 504 and fixed message", async () => {
      await assert.rejects(
        async () => {
          await fetchFromBackend("/timeout");
        },
        (error: unknown) => {
          assert.ok(error instanceof BackendError);
          assert.strictEqual(error.status, 504);
          assert.strictEqual(error.message, "Backend request timeout");
          assert.ok(errorLogs.some((log) => log[0]?.toString().includes("[Backend] Request timeout")));
          return true;
        }
      );
    });
  });

  describe("handleBackendError", () => {
    it("should return proper response for BackendError with status 404", () => {
      const error = new BackendError(
        404,
        { error: "NOT_FOUND", message: "Resource not found" },
        "Resource not found"
      );
      const response = handleBackendError(error);
      assert.strictEqual(response.status, 404);
    });

    it("should return 503 for network errors without exposing details", () => {
      const error = new BackendError(503, null, "Backend unavailable");
      const response = handleBackendError(error);
      assert.strictEqual(response.status, 503);
    });

    it("should return 500 for unexpected errors", () => {
      const error = new Error("Something went wrong");
      const response = handleBackendError(error);
      assert.strictEqual(response.status, 500);
    });
  });

  describe("validateSnowflakeId", () => {
    it("should accept valid numeric string", () => {
      const error = validateSnowflakeId("123456789012345678", "guildId");
      assert.strictEqual(error, null);
    });

    it("should reject non-numeric string", () => {
      const error = validateSnowflakeId("abc123", "guildId");
      assert.ok(error?.includes("guildId"));
      assert.ok(error?.includes("numeric"));
    });

    it("should reject empty string", () => {
      const error = validateSnowflakeId("", "guildId");
      assert.ok(error);
    });
  });

  describe("validateNumericId", () => {
    it("should accept valid numeric string", () => {
      const error = validateNumericId("123", "raidId");
      assert.strictEqual(error, null);
    });

    it("should reject non-numeric string", () => {
      const error = validateNumericId("12abc", "raidId");
      assert.ok(error?.includes("raidId"));
    });
  });
});
