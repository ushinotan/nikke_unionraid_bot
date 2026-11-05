import { describe, it } from "node:test";
import assert from "node:assert";
import { isPrivileged } from "../lib/permissions";
import type { GuildMetadata, SessionData } from "../lib/session";

const GUILD_ID = "111111111111111111";

function sessionWith(guild: Partial<GuildMetadata>): Pick<SessionData, "guilds"> {
  return { guilds: [{ id: GUILD_ID, name: "Test Guild", ...guild }] };
}

describe("isPrivileged", () => {
  it("オーナーなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: true, permissions: "0" }), GUILD_ID), true);
  });

  it("ADMINISTRATOR 権限を持つなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "8" }), GUILD_ID), true);
  });

  it("MANAGE_GUILD 権限を持つなら true", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "32" }), GUILD_ID), true);
  });

  it("他の権限と組み合わさっていても判定できる", () => {
    // VIEW_CHANNEL | SEND_MESSAGES | MANAGE_GUILD | 1 << 40
    const permissions = "1099511630880";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID), true);
  });

  it("一般メンバーなら false", () => {
    // VIEW_CHANNEL | SEND_MESSAGES | 1 << 40（53bit を超える値も BigInt で扱える）
    const permissions = "1099511630848";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID), false);
  });

  it("所属していないギルドなら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: true, permissions: "8" }), "222222222222222222"), false);
  });

  it("ギルド情報が無いセッションなら false", () => {
    assert.strictEqual(isPrivileged({}, GUILD_ID), false);
  });

  it("owner / permissions が無い古いセッションなら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({}), GUILD_ID), false);
  });

  it("permissions が数値として不正なら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "abc" }), GUILD_ID), false);
  });
});
