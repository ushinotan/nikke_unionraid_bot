import type { RaidEditSession } from "../../lib/raid-authorization";
import type { RaidSummaryDto } from "../../lib/api-types";

export const GUILD_ID = "111111111111111111";
export const OTHER_GUILD_ID = "222222222222222222";
export const SELF = "900000000000000001";
export const OTHER = "900000000000000002";

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

/** 一般メンバー（本人） */
export const selfMember: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "0" }],
};

/** 特権（管理者権限）ユーザー */
export const privilegedMember: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "8" }],
};

/** ギルドオーナー */
export const ownerMember: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: true, permissions: "0" }],
};

/** 別ギルドのオーナー（対象レイドのギルドには所属していない） */
export const outsider: RaidEditSession = {
  userId: SELF,
  guildIds: [OTHER_GUILD_ID],
  guilds: [{ id: OTHER_GUILD_ID, name: "Other", owner: true, permissions: "8" }],
};

export type RecordedCall = { url: string; method: string; body: unknown };

export type BackendMock = {
  /** GET 以外（書き込み）の呼び出し記録 */
  writes: RecordedCall[];
  /** すべての呼び出し URL */
  fetchedUrls: string[];
  /** GET /api/raids/{id} が返すレイド。null なら 404 */
  setRaid(next: RecordedRaid): void;
  restore(): void;
};

type RecordedRaid = RaidSummaryDto | null;

/**
 * global.fetch を差し替える。GET は現在のレイドを返し、それ以外は呼び出しを記録する。
 * afterEach で必ず restore() を呼ぶこと。
 */
export function mockBackend(initialRaid: RecordedRaid = raid()): BackendMock {
  const original = global.fetch;
  let current = initialRaid;
  const writes: RecordedCall[] = [];
  const fetchedUrls: string[] = [];

  global.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
    const target = url instanceof Request ? url.url : url.toString();
    const method = (init?.method ?? (url instanceof Request ? url.method : "GET")).toUpperCase();
    fetchedUrls.push(target);
    if (method !== "GET") {
      writes.push({ url: target, method, body: init?.body ?? null });
      return { ok: true, status: 200, json: async () => ({}) } as Response;
    }
    if (!current) {
      return {
        ok: false,
        status: 404,
        json: async () => ({ error: "NOT_FOUND", message: "Raid not found" }),
      } as Response;
    }
    return {
      ok: true,
      status: 200,
      json: async () => ({ raid: current, participants: [], reports: [] }),
    } as Response;
  }) as typeof fetch;

  return {
    writes,
    fetchedUrls,
    setRaid(next) {
      current = next;
    },
    restore() {
      global.fetch = original;
    },
  };
}
