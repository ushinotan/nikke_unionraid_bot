import { afterEach, beforeEach, describe, it } from "node:test";
import assert from "node:assert";
import { requireRaidEditPermission, type RaidEditSession } from "../lib/raid-authorization";
import {
  GUILD_ID,
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
      backend.setRaid(null);
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
});
