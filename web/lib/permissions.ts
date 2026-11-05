import type { SessionData } from "@/lib/session";
import type { RaidSummaryDto } from "@/lib/api-types";

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

/**
 * 参加者・3T 報告の操作可否。
 * - 所属していないギルドのレイドは不可
 * - 特権ユーザーなら、終了済みレイドも含めて全員分を操作できる
 * - 一般メンバーは本人分のみ、かつ終了済みレイドは不可
 *
 * session は必ずサーバー側のセッションを渡すこと（クライアントから送られた値で判定しない）。
 */
export function canEditParticipant(
  session: Pick<SessionData, "userId" | "guildIds" | "guilds">,
  raid: Pick<RaidSummaryDto, "guildId" | "finishedAt">,
  targetUserId: string
): boolean {
  if (!session.userId) return false;
  if (!session.guildIds?.includes(raid.guildId)) return false;
  if (isPrivileged(session, raid.guildId)) return true;
  if (session.userId !== targetUserId) return false;
  return raid.finishedAt === null;
}
