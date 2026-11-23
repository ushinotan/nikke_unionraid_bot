import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { applyLogin, getSessionOptions, type SessionData } from "../lib/session";

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

describe("applyLogin", () => {
  const NOW = Date.UTC(2026, 10, 1);
  const guild = { id: "111111111111111111", name: "Guild", icon: null, owner: true, permissions: "8" };

  it("ユーザー・ギルド情報と loggedInAt（ログイン時刻）を入れる", () => {
    const session: SessionData = {};
    applyLogin(session, { userId: "900000000000000001", guilds: [guild] }, NOW);

    assert.deepStrictEqual(session, {
      userId: "900000000000000001",
      guildIds: [guild.id],
      guilds: [guild],
      loggedInAt: NOW,
    });
  });

  it("再ログインで loggedInAt を新しい時刻に更新する", () => {
    const session: SessionData = { loggedInAt: NOW - 2 * 24 * 60 * 60 * 1000 };
    applyLogin(session, { userId: "900000000000000001", guilds: [guild] }, NOW);

    assert.strictEqual(session.loggedInAt, NOW);
  });

  it("選択中のギルドに所属しなくなっていたら選択を解除する", () => {
    const session: SessionData = { selectedGuildId: "222222222222222222" };
    applyLogin(session, { userId: "900000000000000001", guilds: [guild] }, NOW);

    assert.strictEqual(session.selectedGuildId, undefined);
  });

  it("選択中のギルドに所属していれば選択を残す", () => {
    const session: SessionData = { selectedGuildId: guild.id };
    applyLogin(session, { userId: "900000000000000001", guilds: [guild] }, NOW);

    assert.strictEqual(session.selectedGuildId, guild.id);
  });
});
