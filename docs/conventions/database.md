# データベース（`init.sql` / `migrations/`）

PostgreSQL 16。ORM は Komapper（`kotlin/`）。

## スキーマの正

- **必須**: スキーマの正は `init.sql`。スキーマを変えるときは最初に `init.sql` を直す。
- **必須**: 既存 DB にも適用が必要な変更は、`migrations/YYYYMMDD_<内容>.sql` を追加する（例: `20261012_add_notify_message_id_to_union_raids.sql`）。日付は PR を作った日。
- **必須**: `init.sql` は bot コンテナの起動のたびに流れる。何度流しても同じ結果になるように書く（`CREATE TABLE IF NOT EXISTS`、`ADD COLUMN IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`）。新しいテーブルに後からカラムを足すときは、`CREATE TABLE` に書いたうえで `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` も `init.sql` に残す。
- **必須**: マイグレーションも冪等にする。データを埋める `UPDATE` は `WHERE ... IS NULL` などで再実行しても壊れないようにする。
- マイグレーションファイルの先頭には、何のための変更か、新規環境は `init.sql` を見ることをコメントで書く。

## 命名と型

- テーブル名は複数形の `snake_case`（`union_raids`, `raid_participants`）。カラム名も `snake_case`。
- 主キーは `id SERIAL`（`guilds` だけは Discord のギルド ID を `guild_id BIGINT` の主キーにしている）。
- Discord の snowflake ID は `BIGINT`。
- 時刻は必ず `TIMESTAMPTZ`。作成日時は `created_at TIMESTAMPTZ DEFAULT now()`。
- 外部キーは `REFERENCES ... ON DELETE CASCADE`（ギルドを消したら配下も消える前提）。
- 「終わったかどうか」のような状態は、真偽値ではなく発生時刻のカラム（`finished_at` が NULL なら進行中）で持つ。

## 互換性

- **必須**: カラムの削除・名前変更・型変更は、動いている bot と食い違う。1 つの PR でやらず、追加 → コード切り替え → 削除の順に分けて出す。
- **必須**: 追加するカラムは NULL を許すか既定値を付ける。既存行がある状態で `NOT NULL` だけを足さない。

## Kotlin 側との対応

- **必須**: カラムを足したら、`entity/` のデータクラスにプロパティを足す（NULL 許容なら `T? = null`）。KSP が Komapper のメタモデルを作り直す。
- **必須**: SQL を文字列で組み立てない。クエリは Komapper の `QueryDsl` で書く。
- **推奨**: Komapper で書けない並び替え（`NULLS LAST` など）は、アプリ側で並べ替えてその理由をコメントに残す（`UnionRaidRepository.findUnionRaidsByGuildId` の例）。
- `docs/codebase/backend/entities.md` も同じ PR で直す。
