import { NextResponse } from "next/server";
import type { RaidDetailResponse, RaidSummaryDto } from "@/lib/api-types";
import {
  fetchFromBackend,
  handleBackendError,
  validateNumericId,
} from "@/lib/backend-client";
import {
  canEditParticipant,
  isPermissionStale,
  isPrivileged,
} from "@/lib/permissions";
import type { SessionData } from "@/lib/session";

/**
 * 書き込み系 API ルートで必要な権限。
 * - privileged: 特権ユーザーのみ（順位・パーセンテージの編集など）
 * - participant: 対象ユーザーの参加者・3T 報告を操作できること（本人 or 特権ユーザー）
 *
 * targetUserId は「実際に書き込むレコードの持ち主」から取ること。
 * body のユーザー ID をそのまま使い、別の participantId / reportId をバックエンドに転送すると、
 * 権限チェックは通っても他人のレコードを書き換えられてしまう。
 * - 自分の分を作成・更新するルート: targetUserId もバックエンドに送るユーザー ID も、auth.userId を使う
 * - レコード ID で操作するルート: バックエンドから取得したそのレコードの userId を targetUserId にする
 * - バックエンドへの転送では、raidId に auth.raid.id を使う
 */
export type RaidEditRequirement =
  | { kind: "privileged" }
  | { kind: "participant"; targetUserId: string };

export type RaidEditSession = Pick<
  SessionData,
  "userId" | "guildIds" | "guilds" | "loggedInAt"
>;

function unauthorized(): NextResponse {
  return NextResponse.json(
    { error: "UNAUTHORIZED", message: "Authentication required" },
    { status: 401 }
  );
}

function forbidden(message: string): NextResponse {
  return NextResponse.json({ error: "FORBIDDEN", message }, { status: 403 });
}

/**
 * 権限情報（owner / permissions）が古いため特権として扱えないときの 403。
 * ログイン自体は有効なので 401 ではなく 403 にし、error で再ログインが必要なことを区別する。
 */
function reauthRequired(): NextResponse {
  return NextResponse.json(
    {
      error: "REAUTH_REQUIRED",
      message: "Permission information is outdated. Please log in again",
    },
    { status: 403 }
  );
}

/**
 * セッションとレイドから、編集操作を許可するか判定する。
 * 許可するなら null、拒否するならそのまま返せるエラーレスポンスを返す。
 *
 * raid はバックエンドから取得したものを渡すこと。ギルド ID などクライアントから
 * 送られた値で判定してはいけない。
 *
 * 特権が要る判定で拒否し、権限情報が古い（isPermissionStale）ときは、
 * 再ログインで解決する可能性があるので REAUTH_REQUIRED を返す。
 * 本人分の操作が終了済みレイドで拒否された場合は、再ログインしても変わらないので FORBIDDEN のまま。
 */
export function authorizeRaidEdit(
  session: RaidEditSession,
  raid: Pick<RaidSummaryDto, "guildId" | "finishedAt">,
  requirement: RaidEditRequirement,
  now: number = Date.now()
): NextResponse | null {
  if (!session.userId) return unauthorized();

  if (!session.guildIds?.includes(raid.guildId)) {
    return forbidden("Access to this raid is not allowed");
  }

  if (requirement.kind === "privileged") {
    if (isPrivileged(session, raid.guildId, now)) return null;
    return isPermissionStale(session, now)
      ? reauthRequired()
      : forbidden("Privileged permission is required");
  }

  if (canEditParticipant(session, raid, requirement.targetUserId, now)) {
    return null;
  }
  if (
    requirement.targetUserId !== session.userId &&
    isPermissionStale(session, now)
  ) {
    return reauthRequired();
  }
  return forbidden("Not allowed to edit this participant");
}

export type RaidEditAuthorizationResult =
  | { ok: true; userId: string; raid: RaidSummaryDto }
  | { ok: false; response: NextResponse };

/**
 * 書き込み系 API ルートの入口で必ず呼ぶ権限チェック。
 * バックエンドへ書き込みを転送する前に呼び、ok: false ならその response を返すこと。
 *
 * 画面でボタンを隠すのは補助であり、防御はこの関数で行う。
 *
 * 1. 未ログインなら 401
 * 2. raidId の形式が不正なら 400
 * 3. バックエンドからレイドを取得し、そのギルド ID でメンバーシップ・権限を判定（不可なら 403）
 *    権限情報が古く特権として扱えないときは、403 で error が REAUTH_REQUIRED
 *
 * @example
 * const session = await getSession();
 * const auth = await requireRaidEditPermission(session, raidId, { kind: "privileged" });
 * if (!auth.ok) return auth.response;
 * // ここで初めてバックエンドに書き込みを転送する
 */
export async function requireRaidEditPermission(
  session: RaidEditSession,
  raidId: string,
  requirement: RaidEditRequirement,
  now: number = Date.now()
): Promise<RaidEditAuthorizationResult> {
  if (!session.userId) return { ok: false, response: unauthorized() };

  const validationError = validateNumericId(raidId, "raidId");
  if (validationError) {
    return {
      ok: false,
      response: NextResponse.json(
        { error: "BAD_REQUEST", message: validationError },
        { status: 400 }
      ),
    };
  }

  let raid: RaidSummaryDto;
  try {
    const data = await fetchFromBackend<RaidDetailResponse>(
      `/api/raids/${encodeURIComponent(raidId)}`
    );
    raid = data.raid;
  } catch (error) {
    return { ok: false, response: handleBackendError(error) };
  }

  const denied = authorizeRaidEdit(session, raid, requirement, now);
  if (denied) return { ok: false, response: denied };

  return { ok: true, userId: session.userId, raid };
}
