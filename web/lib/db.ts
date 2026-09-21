/**
 * PostgreSQL データベース接続ヘルパー
 * 
 * DB接続用のPoolインスタンスとクエリユーティリティを提供します。
 */

import { Pool, PoolConfig, QueryResult, QueryResultRow } from 'pg';

/**
 * 環境変数から取得するDB接続設定
 */
const dbConfig: PoolConfig = {
  host: process.env.POSTGRES_HOST || 'localhost',
  port: parseInt(process.env.POSTGRES_PORT || '5432', 10),
  database: process.env.POSTGRES_DB || 'nikke_unionraid',
  user: process.env.POSTGRES_USER || 'postgres',
  password: process.env.POSTGRES_PASSWORD || '',
  // 接続プール設定
  max: 20, // 最大接続数
  idleTimeoutMillis: 30000, // アイドルタイムアウト
  connectionTimeoutMillis: 2000, // 接続タイムアウト
};

/**
 * グローバルな接続プールインスタンス
 * Next.js の Hot Reload 時に接続が増殖しないよう、グローバルに保持します。
 */
const globalForDb = global as unknown as { pool: Pool | undefined };

export const pool = globalForDb.pool ?? new Pool(dbConfig);

if (process.env.NODE_ENV !== 'production') {
  globalForDb.pool = pool;
}

/**
 * クエリ実行ヘルパー
 * @param text SQL クエリ文字列
 * @param params クエリパラメータ
 * @returns クエリ結果
 */
export async function query<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params?: unknown[]
): Promise<QueryResult<T>> {
  const start = Date.now();
  try {
    const result = await pool.query<T>(text, params);
    const duration = Date.now() - start;
    
    // 開発環境ではクエリログを出力
    if (process.env.NODE_ENV === 'development') {
      console.log('Executed query', { text, duration, rows: result.rowCount });
    }
    
    return result;
  } catch (error) {
    console.error('Database query error:', error);
    throw error;
  }
}

/**
 * DB接続テスト用ヘルパー
 * @returns 接続が成功すれば true
 */
export async function testConnection(): Promise<boolean> {
  try {
    const result = await query('SELECT NOW() as now');
    return result.rows.length > 0;
  } catch (error) {
    console.error('Database connection test failed:', error);
    return false;
  }
}
