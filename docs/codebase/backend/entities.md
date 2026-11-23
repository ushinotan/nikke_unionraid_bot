# エンティティ

エンティティは `kotlin/src/main/kotlin/gg/nikke/raid/bot/entity/`、メタモデルの入口は `repository/KomapperMeta.kt`。
テーブル定義の正は `init.sql`。

| エンティティ / テーブル | フィールド | 備考 |
|---|---|---|
| `Guild` / `guilds` | `guildId` (BIGINT, PK)、`createdAt` | ボットが参加したギルド。参加・退出イベントで追加・削除 |
| `UnionRaid` / `union_raids` | `id` (SERIAL)、`guildId` → guilds、`raidName`、`startTime`、`endTime`、`notifyTime?`、`channelId`、`ranking?`、`percentage?` (NUMERIC(6,2))、`finishedAt?`、`notifyMessageId?`、`createdAt` | `finishedAt` が null かつ期間内なら進行中。`ranking` と `percentage` はどちらか一方だけ。`notifyMessageId` は報告ボタン付き通知メッセージの ID。index `idx_guild_id` |
| `RaidParticipant` / `raid_participants` | `id`、`raidId` → union_raids、`userId`、`username`、`score`、`joinedAt` | UNIQUE `(raid_id, user_id)`。3凸報告で自動参加（`ON CONFLICT DO NOTHING`、score 0）し、`is_3t=1` の報告が 0 件になると自動削除される。書き込みは `RaidDomainService` 経由のみ（`services-map.md` のロック手順を参照）。index `idx_raid_id`、`idx_user_id` |
| `RaidReport` / `raid_reports` | `id`、`raidId` → union_raids、`userId`、`username`、`difficulty` (`normal` / `hard`)、`is3t` (INTEGER 0/1、列名 `is_3t`)、`reportedAt` | 3凸報告。UNIQUE `(raid_id, user_id, difficulty)` |

外部キーはすべて `ON DELETE CASCADE`。

## 値オブジェクト
- `service/Difficulty.kt`: `NORMAL("normal", "ノーマル")`、`HARD("hard", "ハード")`。DB には `value` を保存。
- `service/RaidAggregation.kt`: レイド終了時の集計結果。
