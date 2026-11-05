import type { SessionData } from "@/lib/session";

/** ADMINISTRATOR 権限 (1 << 3) */
export const ADMINISTRATOR = BigInt(0x8);
/** MANAGE_GUILD 権限 (1 << 5) */
export const MANAGE_GUILD = BigInt(0x20);

function hasPermission(permissions: string | undefined, flag: bigint): boolean {
  // 10 進数の非負整数以外（負の数・16進数・前後の空白など）は権限なしとして扱う
  if (!permissions || !/^\d+$/.test(permissions)) return false;
  return (BigInt(permissions) & flag) === flag;
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
