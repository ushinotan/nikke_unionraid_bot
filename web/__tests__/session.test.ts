import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { getSessionOptions } from "../lib/session";

const ENV_KEYS = ["APP_BASE_URL", "DISCORD_REDIRECT_URI", "SESSION_SECRET", "NODE_ENV"] as const;

describe("session", () => {
  const saved: Record<string, string | undefined> = {};
  const env = process.env as Record<string, string | undefined>;

  beforeEach(() => {
    for (const key of ENV_KEYS) {
      saved[key] = env[key];
      delete env[key];
    }
    env.SESSION_SECRET = "complex_password_at_least_32_characters_long";
  });

  afterEach(() => {
    // undefined を代入すると文字列 "undefined" になるため、delete で復元する
    for (const key of ENV_KEYS) {
      if (saved[key] === undefined) {
        delete env[key];
      } else {
        env[key] = saved[key];
      }
    }
  });

  describe("getSessionOptions - secure cookie", () => {
    it("本番 + APP_BASE_URL が https なら secure になる", () => {
      env.NODE_ENV = "production";
      env.APP_BASE_URL = "https://app.example.com";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, true);
    });

    it("本番 + DISCORD_REDIRECT_URI が https なら secure になる", () => {
      env.NODE_ENV = "production";
      env.DISCORD_REDIRECT_URI = "https://app.example.com/api/auth/discord/callback";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, true);
    });

    it("本番でも http なら secure にならない", () => {
      env.NODE_ENV = "production";
      env.DISCORD_REDIRECT_URI = "http://localhost:3000/api/auth/discord/callback";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, false);
    });

    it("本番でも URL 未設定なら secure にならない", () => {
      env.NODE_ENV = "production";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, false);
    });

    it("本番以外は https でも secure にならない", () => {
      env.NODE_ENV = "development";
      env.APP_BASE_URL = "https://app.example.com";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, false);
    });

    it("APP_BASE_URL が DISCORD_REDIRECT_URI より優先される", () => {
      env.NODE_ENV = "production";
      env.APP_BASE_URL = "https://app.example.com";
      env.DISCORD_REDIRECT_URI = "http://localhost:3000/api/auth/discord/callback";

      assert.strictEqual(getSessionOptions().cookieOptions?.secure, true);
    });
  });

  it("SESSION_SECRET が無ければ例外を投げる", () => {
    delete env.SESSION_SECRET;

    assert.throws(() => getSessionOptions(), /SESSION_SECRET/);
  });
});
