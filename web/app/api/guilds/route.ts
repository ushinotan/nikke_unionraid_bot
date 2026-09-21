/**
 * ギルド一覧取得 API
 * 
 * GET /api/guilds
 * 
 * 登録されているすべてのギルド情報を取得します。
 */

import { NextResponse } from 'next/server';
import { query } from '@/lib/db';
import { createErrorResponse } from '@/lib/api-utils';
import { Guild } from '@/lib/types';

export async function GET() {
  try {
    // ギルド一覧を取得（作成日時の降順）
    const result = await query<Guild>(
      `SELECT 
        guild_id, 
        created_at 
      FROM guilds 
      ORDER BY created_at DESC`
    );

    return NextResponse.json({
      guilds: result.rows,
      count: result.rowCount || 0,
    });
  } catch (error) {
    console.error('Error fetching guilds:', error);
    return createErrorResponse(
      'ギルド情報の取得に失敗しました',
      500
    );
  }
}
