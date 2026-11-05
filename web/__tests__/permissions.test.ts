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
    // VIEW_CHANNEL | SEND_MESSAGES | 1 << 40
    const permissions = "1099511630848";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID), false);
  });

  it("53bit を超える権限値でも判定できる", () => {
    // 1 << 60 | MANAGE_GUILD（Number では下位ビットが失われる値）
    const permissions = "1152921504606847008";
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions }), GUILD_ID), true);
  });

  it("53bit を超える権限値でも一般メンバーなら false", () => {
    // 1 << 60 | VIEW_CHANNEL
    const permissions = "1152921504606848000";
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

  it("permissions が負の数なら false", () => {
    // BigInt("-8") & 8n は 8n になるため、事前に弾く必要がある
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "-8" }), GUILD_ID), false);
  });

  it("permissions が16進数や前後に空白を含む文字列なら false", () => {
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: "0x20" }), GUILD_ID), false);
    assert.strictEqual(isPrivileged(sessionWith({ owner: false, permissions: " 32 " }), GUILD_ID), false);
  });
});
