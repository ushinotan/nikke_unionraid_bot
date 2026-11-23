import { getIronSession, IronSession, SessionOptions } from "iron-session";
import { cookies } from "next/headers";
import { getConfiguredOrigin } from "@/lib/origin";

export interface GuildMetadata {
  id: string;
  name: string;
  icon?: string | null;
  /** ログイン時点でサーバーのオーナーかどうか（この項目追加前のセッションには無い） */
  owner?: boolean;
  /** ログイン時点の権限ビットフィールド（Discord API が返す10進数文字列） */
  permissions?: string;
}

export interface SessionData {
  userId?: string;
  guildIds?: string[];
  guilds?: GuildMetadata[];
  selectedGuildId?: string;
  state?: string;
  /**
   * guilds の owner / permissions を Discord から取得した時刻（UNIX エポックのミリ秒）。
   * ログイン時（OAuth callback）にだけサーバー側で入れる。
   * この値から PRIVILEGE_MAX_AGE_MS（lib/permissions.ts）を過ぎたら、特権ユーザーとして扱わない。
   * この項目追加前のセッションには無く、その場合も特権なしとして扱う。
   */
  loggedInAt?: number;
}

/**
 * ログイン（OAuth callback）で取得したユーザー・ギルド情報をセッションに入れる。
 * 権限情報（owner / permissions）を取得した時刻として loggedInAt に now を入れる（特権判定の有効期限に使う）。
 * 選択中のギルドに所属しなくなっていたら、選択を解除する。保存（session.save）は呼び出し側で行う。
 */
export function applyLogin(
  session: SessionData,
  login: { userId: string; guilds: GuildMetadata[] },
  now: number = Date.now()
): void {
  const guildIds = login.guilds.map((g) => g.id);
  session.userId = login.userId;
  session.guildIds = guildIds;
  session.guilds = login.guilds;
  session.loggedInAt = now;

  if (session.selectedGuildId && !guildIds.includes(session.selectedGuildId)) {
    delete session.selectedGuildId;
  }
}

export function getSessionOptions(): SessionOptions {
  const secret = process.env.SESSION_SECRET;
  if (!secret) {
    throw new Error("SESSION_SECRET environment variable is required");
  }
  
  const isProduction = process.env.NODE_ENV === "production";
  const isHttps = getConfiguredOrigin()?.startsWith("https://") ?? false;
  
  return {
    password: secret,
    cookieName: "nikke_raid_session",
    cookieOptions: {
      httpOnly: true,
      secure: isProduction && isHttps,
      sameSite: "lax" as const,
      maxAge: 60 * 60 * 24 * 7,
      path: "/",
    },
  };
}

export async function getSession(): Promise<IronSession<SessionData>> {
  const cookieStore = await cookies();
  return getIronSession<SessionData>(cookieStore, getSessionOptions());
}
