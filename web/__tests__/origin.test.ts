import { describe, it } from "node:test";
import assert from "node:assert";
import { getPublicOrigin } from "../lib/origin";
import { NextRequest } from "next/server";

function mockRequest(url: string, headers: Record<string, string> = {}): NextRequest {
  const req = new NextRequest(url, {
    method: "GET",
  });
  for (const [key, value] of Object.entries(headers)) {
    req.headers.set(key, value);
  }
  return req;
}

describe("origin", () => {
  describe("getPublicOrigin", () => {
    it("should use APP_BASE_URL when set", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      process.env.APP_BASE_URL = "https://production.example.com";

      const req = mockRequest("http://0.0.0.0:3000/api/auth/callback");
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "https://production.example.com");
      process.env.APP_BASE_URL = originalAppBaseUrl;
    });

    it("should fallback to DISCORD_REDIRECT_URI origin when APP_BASE_URL is not set", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      delete process.env.APP_BASE_URL;
      process.env.DISCORD_REDIRECT_URI = "https://web-production-566670.up.railway.app/api/auth/discord/callback";

      const req = mockRequest("http://0.0.0.0:3000/api/auth/callback");
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "https://web-production-566670.up.railway.app");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });

    it("should use x-forwarded-* headers when env vars are not set", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      delete process.env.APP_BASE_URL;
      process.env.DISCORD_REDIRECT_URI = "";

      const req = mockRequest("http://0.0.0.0:3000/api/auth/callback", {
        "x-forwarded-proto": "https",
        "x-forwarded-host": "web-production-566670.up.railway.app",
      });
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "https://web-production-566670.up.railway.app");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });

    it("should fallback to request.url origin when all else fails", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      delete process.env.APP_BASE_URL;
      delete process.env.DISCORD_REDIRECT_URI;

      const req = mockRequest("http://localhost:3000/api/auth/callback");
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "http://localhost:3000");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });

    it("should handle invalid APP_BASE_URL gracefully", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      process.env.APP_BASE_URL = "not-a-valid-url";
      delete process.env.DISCORD_REDIRECT_URI;

      const req = mockRequest("http://localhost:3000/api/auth/callback");
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "http://localhost:3000");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });

    it("should handle only x-forwarded-proto without x-forwarded-host", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      delete process.env.APP_BASE_URL;
      delete process.env.DISCORD_REDIRECT_URI;

      const req = mockRequest("http://localhost:3000/api/auth/callback", {
        "x-forwarded-proto": "https",
      });
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "http://localhost:3000");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });

    it("should handle only x-forwarded-host without x-forwarded-proto", () => {
      const originalAppBaseUrl = process.env.APP_BASE_URL;
      const originalDiscordRedirectUri = process.env.DISCORD_REDIRECT_URI;
      
      delete process.env.APP_BASE_URL;
      delete process.env.DISCORD_REDIRECT_URI;

      const req = mockRequest("http://localhost:3000/api/auth/callback", {
        "x-forwarded-host": "example.com",
      });
      const origin = getPublicOrigin(req);

      assert.strictEqual(origin, "http://localhost:3000");
      
      process.env.APP_BASE_URL = originalAppBaseUrl;
      process.env.DISCORD_REDIRECT_URI = originalDiscordRedirectUri;
    });
  });
});
