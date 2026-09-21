# NIKKE ユニオンレイド Bot - Web フロントエンド

このプロジェクトは [Next.js](https://nextjs.org) で構築された NIKKE ユニオンレイド Bot の Web フロントエンドです。

## 推奨セットアップ（将来: Issue #41）

**Issue #41** で docker-compose に `web` サービスが追加される予定です。その際、以下の環境変数が docker-compose.yml から自動的に注入されるため、個別の環境変数ファイルは不要になります。

```yaml
# Issue #41 で追加予定の docker-compose 設定（例）
services:
  web:
    env_file:
      - .env  # リポジトリルートの .env を使用
    environment:
      - POSTGRES_HOST=db
      - POSTGRES_DB=${POSTGRES_DB:-nikke_unionraid}
      - POSTGRES_USER=${POSTGRES_USER:-postgres}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
```

**docker-compose 経由での実行が長期的な推奨構成です。**

## 現在の暫定セットアップ（ホスト側での npm run dev）

Issue #41 が実装されるまでの間、ホストマシン上で直接 `npm run dev` を実行する場合は、以下の手順で環境変数を設定してください。

### 必要な環境変数

**リポジトリルートの `env.example` を参照し、`.env` ファイルを作成してください。**

Web フロントエンドで必要な環境変数は以下の通りです（すべてルートの `env.example` に定義されています）:

```bash
# PostgreSQL 接続設定
POSTGRES_HOST=localhost          # ホスト側での開発時。docker-compose 使用時は 'db'
POSTGRES_HOST_PORT=5432          # ポート番号
POSTGRES_DB=nikke_unionraid      # データベース名
POSTGRES_USER=postgres           # ユーザー名
POSTGRES_PASSWORD=your_password  # パスワード
```

**注意**: 
- 環境変数は**リポジトリルートの `.env`** から読み込まれます（Next.js が自動的に親ディレクトリの `.env` を参照）。
- `web/` 配下に独自の `.env` ファイルを作成する必要はありません。
- ホスト側での開発時のみ、`POSTGRES_HOST=localhost` を `.env` に追加してください（`env.example` にコメントアウトで記載されています）。

### 設定例

```bash
# リポジトリルートで .env を作成（まだ存在しない場合）
cp env.example .env

# .env を編集して以下を設定:
# 1. POSTGRES_PASSWORD を実際の値に変更
# 2. POSTGRES_HOST=localhost の行をコメント解除（ホスト側での開発時のみ）
```

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
cd web
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
