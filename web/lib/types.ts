/**
 * データベーススキーマに対応する型定義
 * 
 * init.sql のスキーマと完全に対応しています。
 */

/**
 * ギルド情報
 */
export interface Guild {
  guild_id: string; // BIGINT は文字列として扱う（JavaScript の Number は安全でない）
  created_at: string; // TIMESTAMPTZ は ISO 8601 文字列
}

/**
 * ユニオンレイド情報
 */
export interface UnionRaid {
  id: number;
  guild_id: string; // BIGINT
  raid_name: string;
  start_time: string; // TIMESTAMPTZ
  end_time: string; // TIMESTAMPTZ
  notify_time: string | null; // TIMESTAMPTZ (nullable)
  channel_id: string; // BIGINT
  ranking: number | null;
  percentage: string | null; // NUMERIC は文字列として扱う
  finished_at: string | null; // TIMESTAMPTZ (nullable)
  created_at: string; // TIMESTAMPTZ
}

/**
 * レイド参加者情報
 */
export interface RaidParticipant {
  id: number;
  raid_id: number;
  user_id: string; // BIGINT
  username: string;
  score: number;
  joined_at: string; // TIMESTAMPTZ
}

/**
 * レイド報告情報
 */
export interface RaidReport {
  id: number;
  raid_id: number;
  user_id: string; // BIGINT
  username: string;
  difficulty: string;
  is_3t: number; // INTEGER (0 or 1)
  reported_at: string; // TIMESTAMPTZ
}

/**
 * レイド詳細情報（参加者と報告を含む）
 */
export interface RaidDetail extends UnionRaid {
  participants: RaidParticipant[];
  reports: RaidReport[];
}

/**
 * API エラーレスポンス
 */
export interface ApiError {
  error: string;
  message: string;
  statusCode: number;
}
