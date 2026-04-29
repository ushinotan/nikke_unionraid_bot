# Go移行 完成定義（Definition of Done）

> **対象Issue**: [#8 \[GO移行\] Go移行の設計凍結と受け入れ条件の確定](https://github.com/ushinotan/nikke_unionraid_bot/issues/8)  
> **ステータス**: 凍結済み（このドキュメントへの変更はPRレビューが必要）

---

## 1. 現行機能の一覧（保存すべき機能）

Go移行では以下の機能を**すべて保持**することを必須とする。

| 機能 | 説明 | Python実装参照 |
|------|------|----------------|
| レイド作成 | `/レイド作成` Slashコマンド。開始時刻モーダル→UTC保存→通知時刻セット | [union_raid.py L171-L209](../../src/cogs/union_raid.py#L171-L209) |
| レイド終了 | `/レイド終了` Slashコマンド。3凸集計→終了処理→タスク/メモリ掃除 | [union_raid.py L351-L433](../../src/cogs/union_raid.py#L351-L433) |
| 3凸（3ヒット）報告 | ボタンUI中心の報告フロー。difficulty（normal/hard）を分けて記録 | [union_raid.py L1-L94](../../src/cogs/union_raid.py#L1-L94) |
| 通知 | notify_time に基づく予約通知。待機計算→チャンネル送信→notify_timeクリア | [union_raid.py L304-L344](../../src/cogs/union_raid.py#L304-L344) |
| 再起動復元 | 起動時にDBをスキャンしnotify_time状態から通知タスクを再スケジュール | [union_raid.py L108-L140](../../src/cogs/union_raid.py#L108-L140) |

---

## 2. 互換性ポリシー

### 2.1 互換の「必須」範囲（差分NG）

以下は**一字一句変更不可**の契約とする。

- **Slashコマンド名**: `レイド作成` / `レイド終了`（日本語のまま維持）
- **コマンド引数の意味**: 期間時間・順位・パーセンテージ等の型・意味・デフォルト値を踏襲する
- **集計ロジック**:
  - 3凸の判定: `is_3t = 1` かつ `difficulty` ごとに分類（`normal` / `hard`）
  - `percentage` は小数第2位・四捨五入（`NUMERIC(6,2)` に準拠）
  - 参照実装: [union_raid.py L22-L36](../../src/cogs/union_raid.py#L22-L36)
- **UTC保存**: DBへ書き込む全日時は UTC (`TIMESTAMPTZ`)
- **DBスキーマ**: `init.sql`（後述のセクション3参照）に定義されたテーブル・カラム・制約に従う

### 2.2 軽微な差分として「許容」する範囲（差分OK）

以下は実装上の都合による差異を許容するが、**情報量は同等であること**を条件とする。

- **Embedの見た目**: フィールド順・改行・絵文字・装飾の細部
- **エラーメッセージ文言**: 完全一致は不要だが「ユーザーが何をすべきか」が明確であること
- **discordgo都合のUI差**: ボタン/セレクトの表示差・モーダルの制約差
  - ただし操作フロー（作成→報告→集計）が成立すること

### 2.3 差分が発生した場合のルール

1. 差分を発見したら、当該Issue内の **「互換性差分一覧（Compatibility Notes）」** セクションに追記する
2. 軽微かどうか判断が難しい場合は **非軽微扱い（差分NG）** として事前にレビューを依頼する
3. 差分が確定した場合は、後続Issueの受け入れ条件に明示する

---

## 3. DBスキーマの真実ソース

### 3.1 方針

- **リポジトリ直下の `init.sql` がDBスキーマの唯一の真実ソース（Single Source of Truth）**
- Goアプリ（sqlc等）・Pythonアプリ（SQLAlchemy）のモデルはすべて `init.sql` に従属する
- スキーマ変更の順序:
  1. `init.sql` を更新する
  2. sqlcクエリ・モデル等を追随させる
  3. 必要に応じてマイグレーションスクリプトを作成する

### 3.2 現行テーブル定義と制約一覧

```sql
-- guilds: サーバー（ギルド）管理
guilds (
    guild_id BIGINT PRIMARY KEY,
    created_at TIMESTAMPTZ DEFAULT now()
)

-- union_raids: レイドセッション
union_raids (
    id SERIAL PRIMARY KEY,
    guild_id BIGINT REFERENCES guilds(guild_id) ON DELETE CASCADE,
    raid_name VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    notify_time TIMESTAMPTZ,           -- NULLは「通知済みまたは不要」を意味する
    channel_id BIGINT NOT NULL,
    ranking INTEGER,
    percentage NUMERIC(6,2),           -- 小数第2位固定
    created_at TIMESTAMPTZ DEFAULT now()
)

-- raid_participants: 参加者
raid_participants (
    id SERIAL PRIMARY KEY,
    raid_id INTEGER REFERENCES union_raids(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    score INTEGER DEFAULT 0,
    joined_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(raid_id, user_id)
)

-- raid_reports: 3凸報告
raid_reports (
    id SERIAL PRIMARY KEY,
    raid_id INTEGER REFERENCES union_raids(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,   -- 'normal' または 'hard'
    is_3t INTEGER DEFAULT 0,           -- 1=3凸完了
    reported_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(raid_id, user_id, difficulty) -- 同一ユーザーがnormal/hardを別々に報告可能
)
```

### 3.3 インデックスと不変条件

| インデックス | 対象 | 用途 |
|---|---|---|
| `idx_guild_id` | `union_raids(guild_id)` | ギルド別レイド検索 |
| `idx_raid_id` | `raid_participants(raid_id)` | 参加者一覧取得 |
| `idx_user_id` | `raid_participants(user_id)` | ユーザー別参加履歴 |

**不変条件**:
- `percentage` は `NUMERIC(6,2)` → アプリ側で小数第2位に丸めてから保存する
- `raid_reports.UNIQUE(raid_id, user_id, difficulty)` → 同一難易度での二重報告はDB制約で防ぐ
- `notify_time` が `NULL` のレイドは再起動復元対象外とする

---

## 4. 時刻仕様

### 4.1 保存ルール

| 対象 | ルール |
|------|--------|
| DB保存 | 全日時を **UTC (`TIMESTAMPTZ`)** で保存する |
| 内部処理 | `timezone.utc` を付与したaware datetimeを基本とする |
| naive datetime | 入力境界（モーダル入力等）でのみ発生し得る。受け取り次第 UTC aware に変換する |

### 4.2 naive datetime の扱い（`ensure_utc_aware` セマンティクス）

`ensure_utc_aware(dt)` の動作:

- `dt.tzinfo is None`（naive）→ UTC として解釈し `tzinfo=timezone.utc` を付与する
- `dt.tzinfo` が存在する（aware）→ `astimezone(timezone.utc)` でUTCに変換する

> **注意**: naive datetimeを受け取った場合は「それがUTCで入力された」と見なす。ローカル時刻として解釈しない。

### 4.3 表示ルール

- ユーザー向け表示は **JST固定（UTC+9）**
- 変換には `DEFAULT_TIMEZONE` を使用する（`DEFAULT_TIMEZONE_HOURS=9` で設定）
- 環境変数 `DEFAULT_TIMEZONE_HOURS` でオフセットを変更できるが、**運用想定値は 9**

```
# .env / env.example
DEFAULT_TIMEZONE_HOURS=9  # JST (UTC+9)
```

### 4.4 通知待機計算ルール

```
wait = notify_time(UTC timestamp) - now(UTC timestamp)
```

| wait の値 | 動作 |
|-----------|------|
| `wait > 0` | `wait` 秒後に通知を送信 |
| `wait <= 0` | **即時通知**（再起動復元で過去のnotify_timeが来たケースを救済） |
| `notify_time is NULL` | 通知タスクを起動しない |

### 4.5 実装参照（Python）

- `ensure_utc_aware` / `utcnow_aware` / `localnow_aware`: [src/utils.py L7-L19](../../src/utils.py#L7-L19)
- 通知待機計算: [src/cogs/union_raid.py L304-L320](../../src/cogs/union_raid.py#L304-L320)
- 再起動復元: [src/cogs/union_raid.py L108-L140](../../src/cogs/union_raid.py#L108-L140)

---

## 5. 後続Issue（#9〜#17）の受け入れ条件テンプレート

各Issueの本文末尾に以下のテンプレートを貼り付けて使用すること。

---

```markdown
## 受け入れ条件（[#8 完成定義](https://github.com/ushinotan/nikke_unionraid_bot/blob/main/go/docs/go-migration-definition-of-done.md) 準拠）

### 互換性チェック
- [ ] Slashコマンド名が `レイド作成` / `レイド終了` のまま動作する（該当する場合）
- [ ] 集計ロジックが Python実装と一致する（3凸の判定、normal/hard分類、percentage丸め）
- [ ] 全日時をUTCで保存し、表示はJST（UTC+9）変換している
- [ ] `init.sql` の定義（テーブル・カラム・制約・インデックス）に差異がない

### 機能チェック
- [ ] （このIssue固有の機能）が動作する
- [ ] エラーケースでユーザーに適切なメッセージが返る

### テスト
- [ ] ユニットテストが追加・更新されている
- [ ] 既存テストがすべてパスする

### 互換性差分一覧（Compatibility Notes）
> 軽微な差分が発生した場合、以下に記録する。

| 項目 | Python挙動 | Go挙動 | 判定 |
|------|-----------|--------|------|
| （なし） | - | - | - |
```

---

## 6. チェックリスト（#8 完了基準）

- [x] 現行機能の一覧化（レイド作成・終了・3凸報告・通知・再起動復元）
- [x] 互換性ポリシーの決定（Slashコマンド名・表示文言・集計ロジック・軽微差分許容範囲）
- [x] DBスキーマの真実ソースを `init.sql` に統一（方針・テーブル・制約・インデックス明記）
- [x] 時刻仕様（UTC保存・JST表示・ensure_utc_aware・通知待機計算）の移行ルール確定
- [x] `env.example` に `DEFAULT_TIMEZONE_HOURS=9` を追記
- [x] `README.md` に時刻ポリシーと環境変数の説明を追記
- [x] `init.sql` ヘッダーにカノニカルスキーマソースであることを明記
- [x] 後続Issue (#9〜#17) 用の受け入れ条件テンプレートを提供
