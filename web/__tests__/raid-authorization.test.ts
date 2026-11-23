import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { requireRaidEditPermission, type RaidEditSession } from "../lib/raid-authorization";
import {
  EXPIRED,
  GUILD_ID,
  NOW,
  OTHER,
  SELF,
  mockBackend,
  outsider,
  ownerMember,
  privilegedMember as privileged,
  raid,
  selfMember as general,
  type BackendMock,
} from "./helpers/raid-edit-fixtures";

describe("requireRaidEditPermission", () => {
  let backend: BackendMock;
  let fetchedUrls: string[];

  beforeEach(() => {
    backend = mockBackend();
    fetchedUrls = backend.fetchedUrls;
  });

  afterEach(() => {
    backend.restore();
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
      backend.setRaid(null);
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
      assert.strictEqual(await statusOf(ownerMember, req), 200);
    });

    it("特権ユーザーは終了済みレイドでも許可", async () => {
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
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
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
      assert.strictEqual(await statusOf(general, { kind: "participant", targetUserId: SELF }), 403);
    });

    it("一般メンバーが他人を操作すると 403", async () => {
      assert.strictEqual(await statusOf(general, { kind: "participant", targetUserId: OTHER }), 403);
    });

    it("特権ユーザーは他人も操作できる（終了済みも可）", async () => {
      assert.strictEqual(await statusOf(privileged, { kind: "participant", targetUserId: OTHER }), 200);
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
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

    it("期限切れの特権ユーザーが終了済みレイドの本人分を操作すると 403 で error が REAUTH_REQUIRED", async () => {
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
      assert.deepStrictEqual(
        await errorOf(privileged, { kind: "participant", targetUserId: SELF }, EXPIRED),
        { status: 403, error: "REAUTH_REQUIRED" }
      );
    });

    it("期限切れの一般メンバーが終了済みレイドの本人分を操作すると 403 で error が REAUTH_REQUIRED", async () => {
      // 期限切れの間は特権ユーザーかどうか分からないので、一般メンバーにも再ログインを求める
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
      assert.deepStrictEqual(
        await errorOf(general, { kind: "participant", targetUserId: SELF }, EXPIRED),
        { status: 403, error: "REAUTH_REQUIRED" }
      );
    });

    it("期限切れの一般メンバーが特権操作をすると 403 で error が REAUTH_REQUIRED", async () => {
      assert.deepStrictEqual(await errorOf(general, { kind: "privileged" }, EXPIRED), {
        status: 403,
        error: "REAUTH_REQUIRED",
      });
    });

    it("期限内の一般メンバーが終了済みレイドの本人分を操作すると 403 で error が FORBIDDEN", async () => {
      backend.setRaid(raid({ finishedAt: "2026-11-02T00:00:00Z" }));
      assert.deepStrictEqual(
        await errorOf(general, { kind: "participant", targetUserId: SELF }),
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
