import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { getConfiguredOrigin, getPublicOrigin } from "../lib/origin";

const ENV_KEYS = ["APP_BASE_URL", "DISCORD_REDIRECT_URI"] as const;

function mockRequest(url: string, headers: Record<string, string> = {}): Request {
  return new Request(url, { method: "GET", headers });
}

describe("origin", () => {
  const saved: Record<string, string | undefined> = {};

  beforeEach(() => {
    for (const key of ENV_KEYS) {
      saved[key] = process.env[key];
      delete process.env[key];
    }
  });

  afterEach(() => {
    // undefined を代入すると文字列 "undefined" になるため、delete で復元する
    for (const key of ENV_KEYS) {
      if (saved[key] === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = saved[key];
      }
    }
  });

  describe("getPublicOrigin", () => {
    it("APP_BASE_URL が設定されていればそれを使う", () => {
      process.env.APP_BASE_URL = "https://production.example.com";

      const origin = getPublicOrigin(mockRequest("http://0.0.0.0:3000/api/auth/callback"));

      assert.strictEqual(origin, "https://production.example.com");
    });

    it("APP_BASE_URL はパスが付いていても origin のみ返す", () => {
      process.env.APP_BASE_URL = "https://production.example.com/foo/bar";

      const origin = getPublicOrigin(mockRequest("http://0.0.0.0:3000/"));

      assert.strictEqual(origin, "https://production.example.com");
    });

    it("APP_BASE_URL は x-forwarded-* より優先される", () => {
      process.env.APP_BASE_URL = "https://production.example.com";

      const origin = getPublicOrigin(
        mockRequest("http://0.0.0.0:3000/", {
          "x-forwarded-proto": "https",
          "x-forwarded-host": "other.example.com",
        })
      );

      assert.strictEqual(origin, "https://production.example.com");
    });

    it("APP_BASE_URL 未設定なら x-forwarded-* を使う", () => {
      process.env.DISCORD_REDIRECT_URI = "http://localhost:3000/api/auth/discord/callback";

      const origin = getPublicOrigin(
        mockRequest("http://0.0.0.0:3000/api/auth/callback", {
          "x-forwarded-proto": "https",
          "x-forwarded-host": "web-production-566670.up.railway.app",
        })
      );

      assert.strictEqual(origin, "https://web-production-566670.up.railway.app");
    });

    it("x-forwarded-* がカンマ区切りなら先頭の値を使う", () => {
      const origin = getPublicOrigin(
        mockRequest("http://0.0.0.0:3000/", {
          "x-forwarded-proto": "https, http",
          "x-forwarded-host": "public.example.com, internal.local",
        })
      );

      assert.strictEqual(origin, "https://public.example.com");
    });

    it("x-forwarded-host にポートが含まれていても扱える", () => {
      const origin = getPublicOrigin(
        mockRequest("http://0.0.0.0:3000/", {
          "x-forwarded-proto": "http",
          "x-forwarded-host": "example.com:8080",
        })
      );

      assert.strictEqual(origin, "http://example.com:8080");
    });

    it("x-forwarded-proto が http/https 以外なら無視する", () => {
      const origin = getPublicOrigin(
        mockRequest("http://localhost:3000/", {
          "x-forwarded-proto": "javascript",
          "x-forwarded-host": "example.com",
        })
      );

      assert.strictEqual(origin, "http://localhost:3000");
    });

    it("x-forwarded-host に不正な文字（パス・userinfo 等）があれば無視する", () => {
      for (const host of ["evil.com/path", "user:pass@evil.com", "evil.com?x=1", "evil .com"]) {
        const origin = getPublicOrigin(
          mockRequest("http://localhost:3000/", {
            "x-forwarded-proto": "https",
            "x-forwarded-host": host,
          })
        );

        assert.strictEqual(origin, "http://localhost:3000", `host=${host}`);
      }
    });

    it("env と x-forwarded-* が無ければ DISCORD_REDIRECT_URI の origin を使う", () => {
      process.env.DISCORD_REDIRECT_URI =
        "https://web-production-566670.up.railway.app/api/auth/discord/callback";

      const origin = getPublicOrigin(mockRequest("http://0.0.0.0:3000/api/auth/callback"));

      assert.strictEqual(origin, "https://web-production-566670.up.railway.app");
    });

    it("DISCORD_REDIRECT_URI が空文字なら request.url にフォールバックする", () => {
      process.env.DISCORD_REDIRECT_URI = "";

      const origin = getPublicOrigin(mockRequest("http://localhost:3000/api/auth/callback"));

      assert.strictEqual(origin, "http://localhost:3000");
    });

    it("全て無ければ request.url の origin を使う", () => {
      const origin = getPublicOrigin(mockRequest("http://localhost:3000/api/auth/callback"));

      assert.strictEqual(origin, "http://localhost:3000");
    });

    it("不正な APP_BASE_URL は無視して次の候補にフォールバックする", () => {
      process.env.APP_BASE_URL = "not-a-valid-url";

      const origin = getPublicOrigin(mockRequest("http://localhost:3000/api/auth/callback"));

      assert.strictEqual(origin, "http://localhost:3000");
    });

    it("x-forwarded-proto だけ（host なし）の場合は無視する", () => {
      const origin = getPublicOrigin(
        mockRequest("http://localhost:3000/", { "x-forwarded-proto": "https" })
      );

      assert.strictEqual(origin, "http://localhost:3000");
    });

    it("x-forwarded-host だけ（proto なし）の場合は無視する", () => {
      const origin = getPublicOrigin(
        mockRequest("http://localhost:3000/", { "x-forwarded-host": "example.com" })
      );

      assert.strictEqual(origin, "http://localhost:3000");
    });
  });

  describe("getConfiguredOrigin", () => {
    it("何も設定されていなければ null", () => {
      assert.strictEqual(getConfiguredOrigin(), null);
    });

    it("APP_BASE_URL のみ設定されていてもその origin を返す", () => {
      process.env.APP_BASE_URL = "https://app.example.com/";

      assert.strictEqual(getConfiguredOrigin(), "https://app.example.com");
    });

    it("APP_BASE_URL が無ければ DISCORD_REDIRECT_URI の origin を返す", () => {
      process.env.DISCORD_REDIRECT_URI = "https://app.example.com/api/auth/discord/callback";

      assert.strictEqual(getConfiguredOrigin(), "https://app.example.com");
    });

    it("APP_BASE_URL が DISCORD_REDIRECT_URI より優先される", () => {
      process.env.APP_BASE_URL = "https://app.example.com";
      process.env.DISCORD_REDIRECT_URI = "http://localhost:3000/api/auth/discord/callback";

      assert.strictEqual(getConfiguredOrigin(), "https://app.example.com");
    });
  });
});
