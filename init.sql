-- =============================================================================
-- init.sql — DBスキーマのカノニカルソース（Single Source of Truth）
--
-- このファイルがDBスキーマの唯一の真実ソースです。
-- PythonモデルやGoのsqlc生成物はすべてこのファイルに従属します。
-- スキーマを変更する場合は必ずこのファイルを先に更新してください。
-- =============================================================================

CREATE TABLE IF NOT EXISTS guilds (
    guild_id BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS union_raids (
    id SERIAL PRIMARY KEY,
    guild_id BIGINT REFERENCES guilds(guild_id) ON DELETE CASCADE,
    raid_name VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    notify_time TIMESTAMPTZ,
    channel_id BIGINT NOT NULL,
    ranking INTEGER,
    percentage NUMERIC(6,2),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS raid_participants (
    id SERIAL PRIMARY KEY,
    raid_id INTEGER REFERENCES union_raids(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    score INTEGER DEFAULT 0,
    joined_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(raid_id, user_id)
);

CREATE TABLE IF NOT EXISTS raid_reports (
    id SERIAL PRIMARY KEY,
    raid_id INTEGER REFERENCES union_raids(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    is_3t INTEGER DEFAULT 0,
    reported_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (raid_id, user_id, difficulty)
);

CREATE INDEX IF NOT EXISTS idx_guild_id ON union_raids(guild_id);
CREATE INDEX IF NOT EXISTS idx_raid_id ON raid_participants(raid_id);
CREATE INDEX IF NOT EXISTS idx_user_id ON raid_participants(user_id);
