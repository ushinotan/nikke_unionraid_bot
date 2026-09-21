/**
 * レイド一覧取得 API
 * 
 * GET /api/raids?guild_id=<guild_id>
 * 
 * 指定されたギルドのレイド一覧を取得します。
 * guild_id パラメータは必須です。
 */

import { NextRequest, NextResponse } from 'next/server';
import { query } from '@/lib/db';
import { createErrorResponse, getBigIntParam } from '@/lib/api-utils';
import { UnionRaid } from '@/lib/types';

export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const guildId = getBigIntParam(searchParams, 'guild_id');

    // guild_id パラメータのバリデーション
    if (!guildId) {
      return createErrorResponse(
        'guild_id パラメータが必須です',
        400
      );
    }

    // レイド一覧を取得（開始日時の降順）
    const result = await query<UnionRaid>(
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
      WHERE guild_id = $1
      ORDER BY start_time DESC`,
      [guildId]
    );

    return NextResponse.json({
      raids: result.rows,
      count: result.rowCount || 0,
      guild_id: guildId,
    });
  } catch (error) {
    console.error('Error fetching raids:', error);
    return createErrorResponse(
      'レイド情報の取得に失敗しました',
      500
    );
  }
}
