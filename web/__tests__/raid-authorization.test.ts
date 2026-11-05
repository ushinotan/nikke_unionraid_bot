import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { requireRaidEditPermission, type RaidEditSession } from "../lib/raid-authorization";
import type { RaidSummaryDto } from "../lib/api-types";

const GUILD_ID = "111111111111111111";
const OTHER_GUILD_ID = "222222222222222222";
const SELF = "900000000000000001";
const OTHER = "900000000000000002";

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
};
const privileged: RaidEditSession = {
  userId: SELF,
  guildIds: [GUILD_ID],
  guilds: [{ id: GUILD_ID, name: "Guild", owner: false, permissions: "8" }],
};
const outsider: RaidEditSession = {
  userId: SELF,
  guildIds: [OTHER_GUILD_ID],
  guilds: [{ id: OTHER_GUILD_ID, name: "Other", owner: true, permissions: "8" }],
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
    raidId = "1"
  ): Promise<number> {
    const result = await requireRaidEditPermission(session, raidId, requirement);
    return result.ok ? 200 : result.response.status;
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
      const result = await requireRaidEditPermission(privileged, "1", { kind: "privileged" });
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
});
