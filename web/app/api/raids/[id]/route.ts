/**
 * レイド詳細取得 API
 * 
 * GET /api/raids/[id]
 * 
 * 指定されたIDのレイド詳細情報を取得します。
 * 参加者（raid_participants）とレポート（raid_reports）も含みます。
 */

import { NextRequest, NextResponse } from 'next/server';
import { query } from '@/lib/db';
import { createErrorResponse } from '@/lib/api-utils';
import { UnionRaid, RaidParticipant, RaidReport, RaidDetail } from '@/lib/types';

interface RouteParams {
  params: Promise<{
    id: string;
  }>;
}

export async function GET(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;
    const raidId = parseInt(id, 10);

    // ID のバリデーション
    if (isNaN(raidId)) {
      return createErrorResponse(
        '無効なレイドIDです',
        400
      );
    }

    // レイド基本情報を取得
    const raidResult = await query<UnionRaid>(
      `SELECT 
        id,
        guild_id,
        raid_name,
        start_time,
        end_time,
        notify_time,
        channel_id,
        ranking,
        percentage,
        finished_at,
        created_at
      FROM union_raids
      WHERE id = $1`,
      [raidId]
    );

    // レイドが見つからない場合
    if (raidResult.rows.length === 0) {
      return createErrorResponse(
        '指定されたレイドが見つかりません',
        404
      );
    }

    const raid = raidResult.rows[0];

    // 参加者情報を取得（スコアの降順）
    const participantsResult = await query<RaidParticipant>(
      `SELECT 
        id,
        raid_id,
        user_id,
        username,
        score,
        joined_at
      FROM raid_participants
      WHERE raid_id = $1
      ORDER BY score DESC, joined_at ASC`,
      [raidId]
    );

    // レポート情報を取得（報告日時の降順）
    const reportsResult = await query<RaidReport>(
      `SELECT 
        id,
        raid_id,
        user_id,
        username,
        difficulty,
        is_3t,
        reported_at
      FROM raid_reports
      WHERE raid_id = $1
      ORDER BY reported_at DESC`,
      [raidId]
    );

    // レイド詳細情報を構築
    const raidDetail: RaidDetail = {
      ...raid,
      participants: participantsResult.rows,
      reports: reportsResult.rows,
    };

    return NextResponse.json(raidDetail);
  } catch (error) {
    console.error('Error fetching raid detail:', error);
    return createErrorResponse(
      'レイド詳細情報の取得に失敗しました',
      500
    );
  }
}
