import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { requireRaidEditPermission, type RaidEditSession } from "../lib/raid-authorization";
import { PRIVILEGE_MAX_AGE_MS } from "../lib/permissions";
import type { RaidSummaryDto } from "../lib/api-types";

const GUILD_ID = "111111111111111111";
const OTHER_GUILD_ID = "222222222222222222";
const SELF = "900000000000000001";
const OTHER = "900000000000000002";
/** テストの現在時刻（固定）。セッションの loggedInAt もこの時刻にする */
const NOW = Date.UTC(2026, 10, 1);
/** 有効期限を過ぎた時刻（ログインから 1 時間ちょうど） */
const EXPIRED = NOW + PRIVILEGE_MAX_AGE_MS;

function raid(overrides: Partial<RaidSummaryDto> = {}): RaidSummaryDto {
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

const general: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "0" }],
  loggedInAt: NOW,
};
const privileged: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "8" }],
  loggedInAt: NOW,
};
const outsider: RaidEditSession = {
  userId: SELF,
  guildIds: [OTHER_GUILD_ID],
  guilds: [{ id: OTHER_GUILD_ID, name: "Other", owner: true, permissions: "8" }],
  loggedInAt: NOW,
};

describe("requireRaidEditPermission", () => {
  const originalFetch = global.fetch;
  let backendRaid: RaidSummaryDto | null;
  let fetchedUrls: string[];

  beforeEach(() => {
    backendRaid = raid();
    fetchedUrls = [];
    global.fetch = (async (url: string | URL | Request) => {
      fetchedUrls.push(url.toString());
      if (!backendRaid) {
        return {
          ok: false,
          status: 404,
          json: async () => ({ error: "NOT_FOUND", message: "Raid not found" }),
        } as Response;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ raid: backendRaid, participants: [], reports: [] }),
      } as Response;
    }) as typeof fetch;
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  async function statusOf(
    session: RaidEditSession,
    requirement: Parameters<typeof requireRaidEditPermission>[2],
    raidId = "1",
    now = NOW
  ): Promise<number> {
    const result = await requireRaidEditPermission(session, raidId, requirement, now);
    return result.ok ? 200 : result.response.status;
  }

  /** 拒否されたときのステータスと error コード（許可なら 200 と null） */
  async function errorOf(
    session: RaidEditSession,
    requirement: Parameters<typeof requireRaidEditPermission>[2],
    now = NOW
  ): Promise<{ status: number; error: string | null }> {
    const result = await requireRaidEditPermission(session, "1", requirement, now);
    if (result.ok) return { status: 200, error: null };
    const body = (await result.response.json()) as { error: string };
    return { status: result.response.status, error: body.error };
  }

  describe("共通", () => {
    it("未ログインなら 401 で、バックエンドを呼ばない", async () => {
      assert.strictEqual(await statusOf({}, { kind: "privileged" }), 401);
      assert.strictEqual(fetchedUrls.length, 0);
    });

    it("raidId が不正なら 400 で、バックエンドを呼ばない", async () => {
      assert.strictEqual(await statusOf(privileged, { kind: "privileged" }, "../1"), 400);
      assert.strictEqual(fetchedUrls.length, 0);
    });

    it("レイドが存在しなければバックエンドの 404 を返す", async () => {
      backendRaid = null;
      assert.strictEqual(await statusOf(privileged, { kind: "privileged" }), 404);
    });

    it("ギルド ID はバックエンドから取得したレイドのものを使う", async () => {
      // 所属外ギルドのオーナーでも、レイドが別ギルドなら拒否される
      assert.strictEqual(await statusOf(outsider, { kind: "privileged" }), 403);
      assert.ok(fetchedUrls[0].endsWith("/api/raids/1"));
    });

    it("許可されたらセッションの userId とレイドを返す", async () => {
      const result = await requireRaidEditPermission(privileged, "1", { kind: "privileged" }, NOW);
      assert.ok(result.ok);
      assert.strictEqual(result.userId, SELF);
      assert.strictEqual(result.raid.guildId, GUILD_ID);
    });
  });

  describe("特権ユーザー限定の操作（順位・パーセンテージ）", () => {
    const req = { kind: "privileged" } as const;

    it("特権ユーザーは許可", async () => {
      assert.strictEqual(await statusOf(privileged, req), 200);
    });

    it("オーナーは許可", async () => {
      const owner: RaidEditSession = {
        ...general,
        guilds: [{ id: GUILD_ID, name: "Guild", owner: true, permissions: "0" }],
      };
      assert.strictEqual(await statusOf(owner, req), 200);
    });

    it("特権ユーザーは終了済みレイドでも許可", async () => {
      backendRaid = raid({ finishedAt: "2026-11-02T00:00:00Z" });
      assert.strictEqual(await statusOf(privileged, req), 200);
    });

    it("一般メンバーは 403", async () => {
      assert.strictEqual(await statusOf(general, req), 403);
    });

    it("所属していないギルドのユーザーは 403", async () => {
      assert.strictEqual(await statusOf(outsider, req), 403);
    });
  });

  describe("参加者・3T 報告の操作", () => {
    it("本人は許可", async () => {
      assert.strictEqual(await statusOf(general, { kind: "participant", targetUserId: SELF }), 200);
    });

    it("本人でも終了済みレイドは 403", async () => {
      backendRaid = raid({ finishedAt: "2026-11-02T00:00:00Z" });
      assert.strictEqual(await statusOf(general, { kind: "participant", targetUserId: SELF }), 403);
    });

    it("一般メンバーが他人を操作すると 403", async () => {
      assert.strictEqual(await statusOf(general, { kind: "participant", targetUserId: OTHER }), 403);
    });

    it("特権ユーザーは他人も操作できる（終了済みも可）", async () => {
      assert.strictEqual(await statusOf(privileged, { kind: "participant", targetUserId: OTHER }), 200);
      backendRaid = raid({ finishedAt: "2026-11-02T00:00:00Z" });
      assert.strictEqual(await statusOf(privileged, { kind: "participant", targetUserId: OTHER }), 200);
    });

    it("所属していないギルドのユーザーは本人分でも 403", async () => {
      assert.strictEqual(await statusOf(outsider, { kind: "participant", targetUserId: SELF }), 403);
    });
  });

  describe("権限情報の有効期限", () => {
    it("期限切れの特権ユーザーが特権操作をすると 403 で error が REAUTH_REQUIRED", async () => {
      assert.deepStrictEqual(await errorOf(privileged, { kind: "privileged" }, EXPIRED), {
        status: 403,
        error: "REAUTH_REQUIRED",
      });
    });

    it("期限切れの特権ユーザーが他人の参加者を操作すると 403 で error が REAUTH_REQUIRED", async () => {
      assert.deepStrictEqual(
        await errorOf(privileged, { kind: "participant", targetUserId: OTHER }, EXPIRED),
        { status: 403, error: "REAUTH_REQUIRED" }
      );
    });

    it("loggedInAt が無い特権ユーザーが特権操作をすると 403 で error が REAUTH_REQUIRED", async () => {
      const legacy: RaidEditSession = { ...privileged, loggedInAt: undefined };
      assert.deepStrictEqual(await errorOf(legacy, { kind: "privileged" }), {
        status: 403,
        error: "REAUTH_REQUIRED",
      });
    });

    it("期限切れでも本人の進行中レイドの操作は 200", async () => {
      assert.strictEqual(
        await statusOf(general, { kind: "participant", targetUserId: SELF }, "1", EXPIRED),
        200
      );
    });

    it("期限内の一般メンバーが特権操作をすると 403 で error が FORBIDDEN", async () => {
      assert.deepStrictEqual(await errorOf(general, { kind: "privileged" }), {
        status: 403,
        error: "FORBIDDEN",
      });
    });

    it("期限切れの一般メンバーが終了済みレイドの本人分を操作すると 403 で error が FORBIDDEN", async () => {
      backendRaid = raid({ finishedAt: "2026-11-02T00:00:00Z" });
      assert.deepStrictEqual(
        await errorOf(general, { kind: "participant", targetUserId: SELF }, EXPIRED),
        { status: 403, error: "FORBIDDEN" }
      );
    });

    it("期限切れでも所属していないギルドのレイドは 403 で error が FORBIDDEN", async () => {
      assert.deepStrictEqual(await errorOf(outsider, { kind: "privileged" }, EXPIRED), {
        status: 403,
        error: "FORBIDDEN",
      });
    });
  });
});
