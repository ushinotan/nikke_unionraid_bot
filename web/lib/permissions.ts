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
 * セッションの権限情報（owner / permissions）を信用する時間（1 日）。
 * loggedInAt からこの時間を過ぎたら、特権ユーザーとして扱わない（再ログインで取り直す）。
 */
export const PRIVILEGE_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/**
 * loggedInAt が now より未来でも許容する幅（5 分）。サーバー間の時計のずれを吸収するためのもの。
 * これを超えて未来の値は、ログイン時に時計が進んでいた可能性があるので古い扱いにする
 * （許容しないと、時計が直ったあとも Cookie の maxAge の間ずっと失効しない）。
 */
export const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

/**
 * セッションの権限情報が古い（または取得時刻が無い）なら true。
 * - loggedInAt が無い・有限の数値でない → 古い扱い（安全側に倒す）
 * - now - loggedInAt >= PRIVILEGE_MAX_AGE_MS → 古い扱い
 * - loggedInAt - now > MAX_CLOCK_SKEW_MS（許容幅を超えて未来） → 古い扱い
 */
export function isPermissionStale(
  session: Pick<SessionData, "loggedInAt">,
  now: number = Date.now()
): boolean {
  const { loggedInAt } = session;
  if (typeof loggedInAt !== "number" || !Number.isFinite(loggedInAt)) return true;
  if (loggedInAt - now > MAX_CLOCK_SKEW_MS) return true;
  return now - loggedInAt >= PRIVILEGE_MAX_AGE_MS;
}

/**
 * 権限判定の結果。
 * - allowed: 許可
 * - stale: 権限情報が古いため拒否（再ログインで許可される可能性がある）
 * - denied: 拒否（再ログインしても変わらない）
 */
export type PermissionCheck = "allowed" | "stale" | "denied";

/**
 * ログインユーザーが指定ギルドの特権ユーザー（オーナー / ADMINISTRATOR / MANAGE_GUILD）かどうかを、拒否の理由付きで返す。
 * 権限はログイン時点の情報で、権限情報が古い（isPermissionStale）なら、ギルドや権限の値に関係なく stale。
 * 所属していないギルドは denied。
 */
export function checkPrivilege(
  session: Pick<SessionData, "guilds" | "loggedInAt">,
  guildId: string,
  now: number = Date.now()
): PermissionCheck {
  if (isPermissionStale(session, now)) return "stale";
  const guild = session.guilds?.find((g) => g.id === guildId);
  if (!guild) return "denied";
  if (guild.owner === true) return "allowed";
  return hasPermission(guild.permissions, ADMINISTRATOR) ||
    hasPermission(guild.permissions, MANAGE_GUILD)
    ? "allowed"
    : "denied";
}

/**
 * ログインユーザーが指定ギルドの特権ユーザーかどうか（checkPrivilege が allowed なら true）。
 * loggedInAt から PRIVILEGE_MAX_AGE_MS を過ぎると特権なし（false）になる。
 */
export function isPrivileged(
  session: Pick<SessionData, "guilds" | "loggedInAt">,
  guildId: string,
  now: number = Date.now()
): boolean {
  return checkPrivilege(session, guildId, now) === "allowed";
}

/**
 * 参加者・3T 報告の操作可否を、拒否の理由付きで返す。
 * - 未ログイン・所属していないギルドのレイドは denied
 * - 特権ユーザーなら、終了済みレイドも含めて全員分を操作できる
 * - 一般メンバーは本人分のみ、かつ終了済みレイドは不可
 * - 上の条件で拒否され、権限情報が古いなら stale（再ログインで特権が確認できれば許可される可能性がある）。
 *   本人分の終了済みレイドでの拒否も、特権ユーザーなら再ログインで通るので stale にする
 *
 * session は必ずサーバー側のセッションを渡すこと（クライアントから送られた値で判定しない）。
 */
export function checkParticipantEdit(
  session: Pick<SessionData, "userId" | "guildIds" | "guilds" | "loggedInAt">,
  raid: Pick<RaidSummaryDto, "guildId" | "finishedAt">,
  targetUserId: string,
  now: number = Date.now()
): PermissionCheck {
  if (!session.userId) return "denied";
  if (!session.guildIds?.includes(raid.guildId)) return "denied";
  const privilege = checkPrivilege(session, raid.guildId, now);
  if (privilege === "allowed") return "allowed";
  if (session.userId === targetUserId && raid.finishedAt === null) return "allowed";
  return privilege;
}

/**
 * 参加者・3T 報告の操作可否（checkParticipantEdit が allowed なら true）。
 * 権限情報の期限切れなら一般メンバーと同じ扱いになる。
 */
export function canEditParticipant(
  session: Pick<SessionData, "userId" | "guildIds" | "guilds" | "loggedInAt">,
  raid: Pick<RaidSummaryDto, "guildId" | "finishedAt">,
  targetUserId: string,
  now: number = Date.now()
): boolean {
  return checkParticipantEdit(session, raid, targetUserId, now) === "allowed";
}
