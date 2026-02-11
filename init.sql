-- Initialize database schema
CREATE TABLE IF NOT EXISTS guilds (
    guild_id BIGINT PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS union_raids (
    id SERIAL PRIMARY KEY,
    guild_id BIGINT REFERENCES guilds(guild_id) ON DELETE CASCADE,
    raid_name VARCHAR(255) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS raid_participants (
    id SERIAL PRIMARY KEY,
    raid_id INTEGER REFERENCES union_raids(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    score INTEGER DEFAULT 0,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(raid_id, user_id)
);

CREATE INDEX idx_guild_id ON union_raids(guild_id);
CREATE INDEX idx_raid_id ON raid_participants(raid_id);
CREATE INDEX idx_user_id ON raid_participants(user_id);