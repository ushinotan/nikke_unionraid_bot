import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { NextRequest } from "next/server";
import { unstable_doesMiddlewareMatch } from "next/experimental/testing/server";
import { sealData } from "iron-session";
import { config, proxy } from "../proxy";
import type { SessionData } from "../lib/session";

const ENV_KEYS = ["APP_BASE_URL", "DISCORD_REDIRECT_URI", "SESSION_SECRET", "NODE_ENV"] as const;
const SECRET = "complex_password_at_least_32_characters_long";
const COOKIE_NAME = "nikke_raid_session";
const BASE = "http://localhost:3000";

async function sessionCookie(data: SessionData): Promise<string> {
  const sealed = await sealData(data, { password: SECRET });
  return `${COOKIE_NAME}=${sealed}`;
}

function request(path: string, cookie?: string): NextRequest {
  const headers = new Headers();
  if (cookie) {
    headers.set("cookie", cookie);
  }
  return new NextRequest(new URL(path, BASE), { headers });
}

/** NextResponse.next() は x-middleware-next ヘッダーで後続処理への通過を表す */
function isPassThrough(response: Response): boolean {
  return response.headers.get("x-middleware-next") === "1";
}

describe("proxy", () => {
  const saved: Record<string, string | undefined> = {};
  const env = process.env as Record<string, string | undefined>;

  beforeEach(() => {
    for (const key of ENV_KEYS) {
      saved[key] = env[key];
      delete env[key];
    }
    env.SESSION_SECRET = SECRET;
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

  describe("保護対象外のパス", () => {
    for (const path of ["/", "/icon.png", "/api/auth/discord"]) {
      it(`${path} は未ログインでも通過する`, async () => {
        const response = await proxy(request(path));
        assert.ok(isPassThrough(response));
      });
    }
  });

  describe("未ログイン", () => {
    for (const path of ["/raids", "/raids/1"]) {
      it(`${path} はログイン URL へリダイレクトする`, async () => {
        const response = await proxy(request(path));
        assert.strictEqual(response.status, 307);
        assert.strictEqual(response.headers.get("location"), `${BASE}/api/auth/discord`);
      });
    }

    it("リダイレクト先は APP_BASE_URL を優先する", async () => {
      env.APP_BASE_URL = "https://raid.example.com";
      const response = await proxy(request("/raids"));
      assert.strictEqual(response.headers.get("location"), "https://raid.example.com/api/auth/discord");
    });

    for (const path of [
      "/api/guilds",
      "/api/guilds/111/raids",
      "/api/raids/1",
      "/api/session/selected-guild",
    ]) {
      it(`${path} は 401 を返す`, async () => {
        const response = await proxy(request(path));
        assert.strictEqual(response.status, 401);
        const body = await response.json();
        assert.strictEqual(body.error, "Unauthorized");
      });
    }

    it("改ざんされた Cookie は未ログイン扱いになる", async () => {
      const response = await proxy(request("/api/guilds", `${COOKIE_NAME}=invalid`));
      assert.strictEqual(response.status, 401);
    });
  });

  describe("ログイン済み", () => {
    it("/raids は通過する", async () => {
      const cookie = await sessionCookie({ userId: "u1", guildIds: ["111"] });
      const response = await proxy(request("/raids", cookie));
      assert.ok(isPassThrough(response));
    });

    it("所属ギルドの API は通過する", async () => {
      const cookie = await sessionCookie({ userId: "u1", guildIds: ["111"] });
      const response = await proxy(request("/api/guilds/111/raids", cookie));
      assert.ok(isPassThrough(response));
    });

    it("所属していないギルドの API は 403 を返す", async () => {
      const cookie = await sessionCookie({ userId: "u1", guildIds: ["111"] });
      const response = await proxy(request("/api/guilds/222/raids", cookie));
      assert.strictEqual(response.status, 403);
      const body = await response.json();
      assert.strictEqual(body.error, "Forbidden");
    });

    it("ギルド ID の前方一致で所属判定しない", async () => {
      const cookie = await sessionCookie({ userId: "u1", guildIds: ["111"] });
      const response = await proxy(request("/api/guilds/1112/raids", cookie));
      assert.strictEqual(response.status, 403);
    });

    it("guildIds を持たないセッションはギルド API で 403 を返す", async () => {
      const cookie = await sessionCookie({ userId: "u1" });
      const response = await proxy(request("/api/guilds/111/raids", cookie));
      assert.strictEqual(response.status, 403);
    });

    it("ギルド一覧 API は所属判定なしで通過する", async () => {
      const cookie = await sessionCookie({ userId: "u1", guildIds: [] });
      const response = await proxy(request("/api/guilds", cookie));
      assert.ok(isPassThrough(response));
    });
  });

  describe("config.matcher", () => {
    // Next.js 16.4.4 のドキュメントは unstable_doesProxyMatch を案内しているが、
    // 同梱パッケージにあるのは unstable_doesMiddlewareMatch のみ
    const matches = (url: string) => unstable_doesMiddlewareMatch({ config, url });

    for (const path of ["/raids", "/raids/1", "/api/guilds", "/api/raids/1", "/api/session/selected-guild"]) {
      it(`${path} は proxy の対象になる`, () => {
        assert.ok(matches(path));
      });
    }

    for (const path of ["/api/auth/discord", "/api/auth/discord/callback", "/_next/static/chunk.js", "/favicon.ico"]) {
      it(`${path} は proxy の対象外になる`, () => {
        assert.ok(!matches(path));
      });
    }
  });
});
