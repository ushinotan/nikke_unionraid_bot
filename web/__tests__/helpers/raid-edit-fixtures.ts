import type { RaidEditSession } from "../../lib/raid-authorization";
import type { RaidSummaryDto } from "../../lib/api-types";
import { PRIVILEGE_MAX_AGE_MS } from "../../lib/permissions";

export const GUILD_ID = "111111111111111111";
export const OTHER_GUILD_ID = "222222222222222222";
export const SELF = "900000000000000001";
export const OTHER = "900000000000000002";
/** テストの現在時刻（固定）。各ペルソナの loggedInAt もこの時刻にする */
export const NOW = Date.UTC(2026, 10, 1);
/** 権限情報の有効期限を過ぎた時刻（ログインから 1 日ちょうど） */
export const EXPIRED = NOW + PRIVILEGE_MAX_AGE_MS;

export function raid(overrides: Partial<RaidSummaryDto> = {}): RaidSummaryDto {
  return {
    id: 1,
    guildId: GUILD_ID,
    raidName: "test",
    startTime: "2026-11-01T00:00:00Z",
    endTime: "2026-11-08T00:00:00Z",
    notifyTime: null,
    channelId: "333333333333333333",
    ranking: null,
    percentage: null,
    finishedAt: null,
    createdAt: "2026-11-01T00:00:00Z",
    ...overrides,
  };
}

/** テスト間で書き換えられないよう、ペルソナは入れ子まで凍結する */
function deepFreeze<T>(value: T): T {
  if (value && typeof value === "object") {
    Object.values(value).forEach(deepFreeze);
    Object.freeze(value);
  }
  return value;
}

/** 本人（一般メンバー。userId が SELF） */
export const selfMember: RaidEditSession = deepFreeze({
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "0" }],
  loggedInAt: NOW,
});

/** 本人以外の一般メンバー（userId が OTHER）。SELF の分を操作すると 403 になる */
export const otherMember: RaidEditSession = deepFreeze({
  userId: OTHER,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "0" }],
  loggedInAt: NOW,
});

/** 特権（管理者権限）ユーザー */
export const privilegedMember: RaidEditSession = deepFreeze({
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "8" }],
  loggedInAt: NOW,
});

/** ギルドオーナー（特権ユーザーの一種） */
export const ownerMember: RaidEditSession = deepFreeze({
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: true, permissions: "0" }],
  loggedInAt: NOW,
});

/** 別ギルドのオーナー（対象レイドのギルドには所属していない） */
export const outsider: RaidEditSession = deepFreeze({
  userId: SELF,
  guildIds: [OTHER_GUILD_ID],
  guilds: [{ id: OTHER_GUILD_ID, name: "Other", owner: true, permissions: "8" }],
  loggedInAt: NOW,
});

/** GET /api/raids/{id} が返すレイド。null なら 404 */
type BackendRaid = RaidSummaryDto | null;

export type MockResponse = { status?: number; body?: unknown };

/** 書き込み（POST / PUT / PATCH / DELETE）の呼び出し記録。body は送られた文字列のまま */
export type RecordedCall = { url: string; method: string; body: string | null };

export type BackendMock = {
  /** 書き込みの呼び出し記録 */
  writes: RecordedCall[];
  /** すべての呼び出し URL */
  fetchedUrls: string[];
  setRaid(next: BackendRaid): void;
  /** method と path（クエリを除く）が一致する呼び出しの応答を決める。setRaid の既定応答より優先する */
  respond(method: string, path: string, response: MockResponse): void;
  restore(): void;
};

/** 書き込みとして扱う HTTP メソッド。write-route-guard.test.ts の検査対象と共通 */
export const WRITE_METHODS = ["POST", "PUT", "PATCH", "DELETE"] as const;
const isWrite = (method: string) => (WRITE_METHODS as readonly string[]).includes(method);

/** ボディを持てないステータス。new Response() に body を渡すと例外になる */
const NULL_BODY_STATUSES = new Set([204, 205, 304]);

function jsonResponse({ status = 200, body = {} }: MockResponse): Response {
  if (NULL_BODY_STATUSES.has(status)) return new Response(null, { status });
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * global.fetch を差し替える。
 * - respond() で登録した method + path にはその応答を返す
 * - GET /api/raids/{id} は、id が現在のレイドと一致すればレイド詳細、それ以外は 404
 * - 書き込みは呼び出しを記録して 200 {} を返す。それ以外の未登録の呼び出しは 404
 * afterEach で必ず restore() を呼ぶこと。
 */
export function mockBackend(initialRaid: BackendRaid = raid()): BackendMock {
  const original = global.fetch;
  let current = initialRaid;
  const overrides = new Map<string, MockResponse>();
  const writes: RecordedCall[] = [];
  const fetchedUrls: string[] = [];
  const notFound = () =>
    jsonResponse({ status: 404, body: { error: "NOT_FOUND", message: "Not found" } });

  global.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const request = input instanceof Request ? input : null;
    const target = request ? request.url : input.toString();
    const method = (init?.method ?? request?.method ?? "GET").toUpperCase();
    const path = new URL(target, "http://backend.test").pathname;
    fetchedUrls.push(target);

    if (isWrite(method)) {
      const rawBody = init?.body ?? (request ? await request.clone().text() : null);
      writes.push({ url: target, method, body: rawBody == null ? null : String(rawBody) });
    }

    const override = overrides.get(`${method} ${path}`);
    if (override) return jsonResponse(override);

    if (isWrite(method)) return jsonResponse({});

    const raidMatch = method === "GET" && path.match(/^\/api\/raids\/(\d+)\/?$/);
    if (raidMatch) {
      if (!current || String(current.id) !== raidMatch[1]) {
        return jsonResponse({ status: 404, body: { error: "NOT_FOUND", message: "Raid not found" } });
      }
      return jsonResponse({ body: { raid: current, participants: [], reports: [] } });
    }
    return notFound();
  }) as typeof fetch;

  return {
    writes,
    fetchedUrls,
    setRaid(next) {
      current = next;
    },
    respond(method, path, response) {
      overrides.set(`${method.toUpperCase()} ${path}`, response);
    },
    restore() {
      global.fetch = original;
    },
  };
}
