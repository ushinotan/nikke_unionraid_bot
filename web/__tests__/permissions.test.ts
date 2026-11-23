import { describe, it } from "node:test";
import assert from "node:assert";
import {
  PRIVILEGE_MAX_AGE_MS,
  canEditParticipant,
  isPermissionStale,
  isPrivileged,
} from "../lib/permissions";
import type { GuildMetadata, SessionData } from "../lib/session";

const GUILD_ID = "111111111111111111";
/** テストの現在時刻（固定） */
const NOW = Date.UTC(2026, 10, 1);
/** 有効期限を過ぎた時刻（ログインから 1 時間ちょうど） */
const EXPIRED = NOW + PRIVILEGE_MAX_AGE_MS;

function sessionWith(guild: Partial<GuildMetadata>): Pick<SessionData, "guilds" | "loggedInAt"> {
  return { guilds: [{ id: GUILD_ID, name: "Test Guild", ...guild }], loggedInAt: NOW };
}

describe("isPermissionStale", () => {
  it("取得直後なら false", () => {
    assert.strictEqual(isPermissionStale({ loggedInAt: NOW }, NOW), false);
  });

  it("1 時間ちょうど経っていたら true", () => {
    assert.strictEqual(isPermissionStale({ loggedInAt: NOW }, NOW + PRIVILEGE_MAX_AGE_MS), true);
  });

  it("1 時間より 1 ミリ秒前なら false", () => {
    assert.strictEqual(isPermissionStale({ loggedInAt: NOW }, NOW + PRIVILEGE_MAX_AGE_MS - 1), false);
  });

  it("loggedInAt が無ければ true", () => {
    assert.strictEqual(isPermissionStale({}, NOW), true);
  });

  it("loggedInAt が数値でない（NaN）なら true", () => {
    assert.strictEqual(isPermissionStale({ loggedInAt: NaN }, NOW), true);
  });

  it("loggedInAt が未来なら false", () => {
    assert.strictEqual(isPermissionStale({ loggedInAt: NOW + 60 * 1000 }, NOW), false);
  });
});

describe("isPrivileged", () => {
  it("オーナーなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: true, permissions: "0" }), GUILD_ID, NOW), true);
  });

  it("ADMINISTRATOR 権限を持つなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "8" }), GUILD_ID, NOW), true);
  });

  it("MANAGE_GUILD 権限を持つなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "32" }), GUILD_ID, NOW), true);
  });

  it("他の権限と組み合わさっていても判定できる", () => {
    // VIEW_CHANNEL | SEND_MESSAGES | MANAGE_GUILD | 1 << 40
    const permissions = "1099511630880";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID, NOW), true);
  });

  it("一般メンバーなら false", () => {
    // VIEW_CHANNEL | SEND_MESSAGES | 1 << 40
    const permissions = "1099511630848";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID, NOW), false);
  });

  it("53bit を超える権限値でも判定できる", () => {
    // 1 << 60 | MANAGE_GUILD（Number では下位ビットが失われる値）
    const permissions = "1152921504606847008";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID, NOW), true);
  });

  it("53bit を超える権限値でも一般メンバーなら false", () => {
    // 1 << 60 | VIEW_CHANNEL
    const permissions = "1152921504606848000";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID, NOW), false);
  });

  it("所属していないギルドなら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: true, permissions: "8" }), "222222222222222222", NOW), false);
  });

  it("ギルド情報が無いセッションなら false", () => {
    assert.strictEqual(isPrivileged({}, GUILD_ID, NOW), false);
  });

  it("owner / permissions が無い古いセッションなら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({}), GUILD_ID, NOW), false);
  });

  it("permissions が数値として不正なら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "abc" }), GUILD_ID, NOW), false);
  });

  it("permissions が負の数なら false", () => {
    // BigInt("-8") & 8n は 8n になるため、事前に弾く必要がある
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "-8" }), GUILD_ID, NOW), false);
  });

  it("permissions が16進数や前後に空白を含む文字列なら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "0x20" }), GUILD_ID, NOW), false);
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: " 32 " }), GUILD_ID, NOW), false);
  });

  it("1 時間を過ぎたオーナーなら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: true, permissions: "0" }), GUILD_ID, EXPIRED), false);
  });

  it("1 時間を過ぎた ADMINISTRATOR / MANAGE_GUILD なら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "8" }), GUILD_ID, EXPIRED), false);
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "32" }), GUILD_ID, EXPIRED), false);
  });

  it("loggedInAt が無いオーナーなら false", () => {
    const session = { ...sessionWith({ owner: true, permissions: "8" }), loggedInAt: undefined };
    assert.strictEqual(isPrivileged(session, GUILD_ID, NOW), false);
  });
});

describe("canEditParticipant", () => {
  const SELF = "900000000000000001";
  const OTHER = "900000000000000002";
  const ongoing = { guildId: GUILD_ID, finishedAt: null };
  const finished = { guildId: GUILD_ID, finishedAt: "2026-11-01T00:00:00Z" };

  function member(guild: Partial<GuildMetadata>) {
    return { userId: SELF, guildIds: [GUILD_ID], ...sessionWith(guild) };
  }
  const general = member({ owner: false, permissions: "0" });
  const privileged = member({ owner: false, permissions: "32" });

  it("本人は進行中レイドを操作できる", () => {
    assert.strictEqual(canEditParticipant(general, ongoing, SELF, NOW), true);
  });

  it("本人でも終了済みレイドは操作できない", () => {
    assert.strictEqual(canEditParticipant(general, finished, SELF, NOW), false);
  });

  it("一般メンバーは他人を操作できない", () => {
    assert.strictEqual(canEditParticipant(general, ongoing, OTHER, NOW), false);
  });

  it("特権ユーザーは終了済みレイドの他人も操作できる", () => {
    assert.strictEqual(canEditParticipant(privileged, ongoing, OTHER, NOW), true);
    assert.strictEqual(canEditParticipant(privileged, finished, OTHER, NOW), true);
  });

  it("所属していないギルドのレイドは本人・特権ユーザーでも操作できない", () => {
    const outside = { guildId: "222222222222222222", finishedAt: null };
    assert.strictEqual(canEditParticipant(general, outside, SELF, NOW), false);
    assert.strictEqual(canEditParticipant(member({ owner: true }), outside, SELF, NOW), false);
  });

  it("guildIds に含まれなければ guilds に権限があっても操作できない", () => {
    const session = { ...privileged, guildIds: [] };
    assert.strictEqual(canEditParticipant(session, ongoing, OTHER, NOW), false);
  });

  it("未ログインなら操作できない", () => {
    const session = { ...general, userId: undefined };
    assert.strictEqual(canEditParticipant(session, ongoing, SELF, NOW), false);
  });

  it("期限切れの特権ユーザーは他人を操作できない", () => {
    assert.strictEqual(canEditParticipant(privileged, ongoing, OTHER, EXPIRED), false);
    assert.strictEqual(canEditParticipant(privileged, finished, OTHER, EXPIRED), false);
  });

  it("期限切れでも本人は進行中レイドを操作できる", () => {
    assert.strictEqual(canEditParticipant(privileged, ongoing, SELF, EXPIRED), true);
    assert.strictEqual(canEditParticipant(general, ongoing, SELF, EXPIRED), true);
  });
});
