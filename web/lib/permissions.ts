import type { SessionData } from "@/lib/session";

/** ADMINISTRATOR 権限 (1 << 3) */
export const ADMINISTRATOR = BigInt(0x8);
/** MANAGE_GUILD 権限 (1 << 5) */
export const MANAGE_GUILD = BigInt(0x20);

function hasPermission(permissions: string | undefined, flag: bigint): boolean {
  if (!permissions) return false;
  try {
    return (BigInt(permissions) & flag) === flag;
  } catch {
    // 数値として解釈できない値は権限なしとして扱う
    return false;
  }
}

/**
 * ログインユーザーが指定ギルドの特権ユーザー（オーナー / ADMINISTRATOR / MANAGE_GUILD）かどうか。
 * 所属していないギルドは false。権限はログイン時点の情報で、再ログインするまで更新されない。
 */
export function isPrivileged(
  session: Pick<SessionData, "guilds">,
  guildId: string
): boolean {
  const guild = session.guilds?.find((g) => g.id === guildId);
  if (!guild) return false;
  if (guild.owner === true) return true;
  return (
    hasPermission(guild.permissions, ADMINISTRATOR) ||
    hasPermission(guild.permissions, MANAGE_GUILD)
  );
}
