# NIKKE ユニオンレイド Bot - Web フロントエンド

このプロジェクトは [Next.js](https://nextjs.org) で構築された NIKKE ユニオンレイド Bot の Web フロントエンドです。

## 必要な環境変数

以下の環境変数を設定してください。ローカル開発では `web/.env.local` ファイルに記載します。

```bash
# PostgreSQL 接続設定（プロジェクトルートの .env と同じ値を使用）
POSTGRES_HOST=localhost          # または 'db'（docker-compose 使用時）
POSTGRES_PORT=5432
POSTGRES_DB=nikke_unionraid
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password_here
```

### 設定例

```bash
# .env.local ファイルを作成
cd web
cat > .env.local << 'EOF'
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=nikke_unionraid
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password_here
EOF
```

## セットアップ

### 1. 依存パッケージのインストール

```bash
npm install
```

### 2. データベースの準備

プロジェクトルートで docker-compose を起動してデータベースを用意します。

```bash
cd ..
docker-compose up -d db
```

### 3. 開発サーバーの起動

```bash
npm run dev
```

ブラウザで [http://localhost:3000](http://localhost:3000) を開いてください。

## API エンドポイント

以下の読み取り専用 API エンドポイントが利用可能です。

### ギルド一覧取得

```
GET /api/guilds
```

**レスポンス例:**

```json
{
  "guilds": [
    {
      "guild_id": "123456789012345678",
      "created_at": "2024-01-01T00:00:00.000Z"
    }
  ],
  "count": 1
}
```

### レイド一覧取得（ギルドごと）

```
GET /api/raids?guild_id=<guild_id>
```

**パラメータ:**

- `guild_id` (必須): ギルドID

**レスポンス例:**

```json
{
  "raids": [
    {
      "id": 1,
      "guild_id": "123456789012345678",
      "raid_name": "サンプルレイド",
      "start_time": "2024-01-01T00:00:00.000Z",
      "end_time": "2024-01-07T23:59:59.000Z",
      "notify_time": "2024-01-01T00:00:00.000Z",
      "channel_id": "987654321098765432",
      "ranking": 10,
      "percentage": "5.50",
      "finished_at": null,
      "created_at": "2024-01-01T00:00:00.000Z"
    }
  ],
  "count": 1,
  "guild_id": "123456789012345678"
}
```

### レイド詳細取得（参加者・レポート含む）

```
GET /api/raids/[id]
```

**パラメータ:**

- `id`: レイドID（URL パス）

**レスポンス例:**

```json
{
  "id": 1,
  "guild_id": "123456789012345678",
  "raid_name": "サンプルレイド",
  "start_time": "2024-01-01T00:00:00.000Z",
  "end_time": "2024-01-07T23:59:59.000Z",
  "notify_time": "2024-01-01T00:00:00.000Z",
  "channel_id": "987654321098765432",
  "ranking": 10,
  "percentage": "5.50",
  "finished_at": null,
  "created_at": "2024-01-01T00:00:00.000Z",
  "participants": [
    {
      "id": 1,
      "raid_id": 1,
      "user_id": "111222333444555666",
      "username": "プレイヤー1",
      "score": 50000000,
      "joined_at": "2024-01-01T01:00:00.000Z"
    }
  ],
  "reports": [
    {
      "id": 1,
      "raid_id": 1,
      "user_id": "111222333444555666",
      "username": "プレイヤー1",
      "difficulty": "hard",
      "is_3t": 1,
      "reported_at": "2024-01-01T02:00:00.000Z"
    }
  ]
}
```

### エラーレスポンス

すべてのエラーは以下の形式で返されます。

```json
{
  "error": "Not Found",
  "message": "指定されたレイドが見つかりません",
  "statusCode": 404
}
```

## ローカルでのテスト方法

### curl でのテスト

```bash
# ギルド一覧
curl http://localhost:3000/api/guilds

# レイド一覧（guild_id を指定）
curl "http://localhost:3000/api/raids?guild_id=123456789012345678"

# レイド詳細（id を指定）
curl http://localhost:3000/api/raids/1
```

## その他のコマンド

```bash
# ビルド
npm run build

# 本番モードで起動
npm run start

# Lint
npm run lint
```

## 時刻について

- データベースにはすべて UTC（TIMESTAMPTZ 型）で保存されます
- API レスポンスも UTC の ISO 8601 形式（例: `2024-01-01T00:00:00.000Z`）で返されます
- フロントエンドで表示する際は、`DEFAULT_TIMEZONE_HOURS` 環境変数（デフォルト: 9 = JST）を参照して変換してください

## 次のステップ

- Issue #42: 認証とギルド切り替え機能の追加
- Issue #39/#40: レイド一覧・詳細ページの UI 実装
