import { sealData } from "iron-session";
import type { SessionData } from "../../lib/session";
import {
  E2E_SESSION_SECRET,
  GUILD_A,
  GUILD_ERROR,
  USER_ID,
} from "./constants";

export type Persona = "anonymous" | "member" | "errorMember";

function sessionFor(
  guild: { id: string; name: string },
  permissions: string
): SessionData {
  return {
    userId: USER_ID,
    guildIds: [guild.id],
    guilds: [{ id: guild.id, name: guild.name, owner: false, permissions }],
    // 権限情報の有効期限（PRIVILEGE_MAX_AGE_MS）を過ぎないよう、固定値ではなくクッキー作成時刻にする
    loggedInAt: Date.now(),
  };
}

/** ペルソナに対応するセッションクッキーの値を作る。anonymous は null（クッキーなし） */
export async function createSessionCookieValue(
  persona: Persona,
  secret: string = E2E_SESSION_SECRET
): Promise<string | null> {
  switch (persona) {
    case "anonymous":
      return null;
    case "member":
      return sealData(sessionFor(GUILD_A, "0"), { password: secret });
    case "errorMember":
      return sealData(sessionFor(GUILD_ERROR, "0"), { password: secret });
  }
}
