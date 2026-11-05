import { NextResponse } from "next/server";
import type { RaidDetailResponse, RaidSummaryDto } from "@/lib/api-types";
import {
  fetchFromBackend,
  handleBackendError,
  validateNumericId,
} from "@/lib/backend-client";
import { canEditParticipant, isPrivileged } from "@/lib/permissions";
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

export type RaidEditSession = Pick<SessionData, "userId" | "guildIds" | "guilds">;

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
 * セッションとレイドから、編集操作を許可するか判定する。
 * 許可するなら null、拒否するならそのまま返せるエラーレスポンスを返す。
 *
 * raid はバックエンドから取得したものを渡すこと。ギルド ID などクライアントから
 * 送られた値で判定してはいけない。
 */
export function authorizeRaidEdit(
  session: RaidEditSession,
  raid: Pick<RaidSummaryDto, "guildId" | "finishedAt">,
  requirement: RaidEditRequirement
): NextResponse | null {
  if (!session.userId) return unauthorized();

  if (!session.guildIds?.includes(raid.guildId)) {
    return forbidden("Access to this raid is not allowed");
  }

  if (requirement.kind === "privileged") {
    return isPrivileged(session, raid.guildId)
      ? null
      : forbidden("Privileged permission is required");
  }

  return canEditParticipant(session, raid, requirement.targetUserId)
    ? null
    : forbidden("Not allowed to edit this participant");
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
  requirement: RaidEditRequirement
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

  const denied = authorizeRaidEdit(session, raid, requirement);
  if (denied) return { ok: false, response: denied };

  return { ok: true, userId: session.userId, raid };
}
