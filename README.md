# Docker環境でのセットアップ

## 前提条件
- Docker
- Docker Compose V2

## セットアップ手順

### 1. 環境変数の設定
`env.example`をコピーして`.env`ファイルを作成:
```bash
cp env.example .env
```

`.env`ファイルを編集して、必要な値を設定:
- `DISCORD_TOKEN`: Discordボットのトークン
- `POSTGRES_PASSWORD`: PostgreSQLのパスワード
- `POSTGRES_HOST_PORT`: Postgresホストポート
- `DEFAULT_TIMEZONE_HOURS`: ユーザー向け表示に使うタイムゾーンのUTCオフセット（デフォルト: `9` = JST）
- `DISCORD_CLIENT_ID`: Discord OAuth用クライアントID
- `DISCORD_CLIENT_SECRET`: Discord OAuth用クライアントシークレット
- `SESSION_SECRET`: セッション暗号化キー（32文字以上のランダム文字列を推奨）


### 2. Dockerコンテナの起動
```bash
docker-compose up -d
```

起動後、以下のサービスが利用可能になります:
- **Web (Next.js)**: http://localhost:3000（メインのユーザー向けフロントエンド）
- **データベース (PostgreSQL)**: localhost:${POSTGRES_HOST_PORT}（デフォルト 5432）

**注意**: ボット（Spring Boot API）はポート 8080 で起動しますが、これは内部 API / デバッグ用です。通常は Web フロントエンド (http://localhost:3000) を使用してください。

### 3. ログの確認
```bash
# ボットのログ
docker-compose logs -f bot

# Webのログ
docker-compose logs -f web
```

### 4. データベースへの接続確認
```bash
docker-compose exec db psql -U postgres -d nikke_unionraid
```

## よく使うコマンド

### コンテナの停止
```bash
docker-compose down
```

### コンテナの再起動
```bash
docker-compose restart
```

### データベースのバックアップ（手動）
```bash
docker compose exec -T db pg_dump -U postgres nikke_unionraid | gzip > backup-$(date +%Y-%m-%d).sql.gz
```

### データベースのリストア
```bash
gunzip -c backup-YYYY-MM-DD.sql.gz | docker compose exec -T db psql -U postgres nikke_unionraid
```

### 月次自動バックアップの設定（おすすめ）
一ヶ月ごとにバックアップを自動取得するスクリプトを用意しています。

1. スクリプトに実行権限を付与
```bash
chmod +x scripts/backup-db.sh
```

2. ホストマシンの crontab に登録（毎月1日の午前4時に実行する例）
```bash
crontab -e
```

以下を追加:
```
0 4 1 * * /絶対パス/nikke_unionraid_bot/scripts/backup-db.sh >> /var/log/nikke-backup.log 2>&1
```

- バックアップは `backups/backup-YYYY-MM-DD.sql.gz` として保存されます
- 13ヶ月以上前のバックアップは自動削除されます
- ログは `/var/log/nikke-backup.log` に残ります（必要に応じてパス変更可）

> **注意**: Dev Container 利用時は、cron は**ホスト側**で設定してください。コンテナ内では永続化されません。

### ボットコンテナに入る
```bash
docker-compose exec bot /bin/bash
```

### データベースコンテナに入る
```bash
docker-compose exec db /bin/sh
```

### ログの確認
```bash
# すべてのログ
docker-compose logs -f

# ボットのログのみ
docker-compose logs -f bot

# Webのログのみ
docker-compose logs -f web

# データベースのログのみ
docker-compose logs -f db
```

## トラブルシューティング

### ボットが起動しない場合
1. `.env`ファイルの設定を確認
2. ログを確認: `docker-compose logs bot`
3. データベースの接続を確認

### データベースに接続できない場合
1. データベースコンテナが起動しているか確認: `docker-compose ps`
2. ヘルスチェックの状態を確認: `docker-compose ps db`
3. データベースのログを確認: `docker-compose logs db`

## 時刻ポリシー

このボットは以下の時刻ルールに従って動作します。

- **DB保存**: 全日時を **UTC** で保存する（PostgreSQL `TIMESTAMPTZ` 型）
- **ユーザー向け表示**: `DEFAULT_TIMEZONE_HOURS` で指定したオフセットのタイムゾーンで表示する（デフォルト: JST = UTC+9）
- **通知待機**: `notify_time`（UTC）と現在時刻（UTC）の差分で待機秒数を算出する。`notify_time` が過去の場合は即時通知する

## Web フロントエンドの開発（Next.js）

ユニオンレイドデータを表示する Web フロントエンドは `web/` ディレクトリにあります。

### Docker Compose で起動（本番相当の動作確認）

```bash
# ルートディレクトリで実行
docker-compose up -d
```

Web フロントエンドは http://localhost:3000 でアクセスできます。
この方法では `web/.env` ファイルは不要です。環境変数はルートの `.env` から自動的に注入されます。

**注意**: この方法は本番相当のイメージ（`NODE_ENV=production`）で起動します。
- ホットモジュールリロード（HMR）は動作しません
- セッションクッキーの `secure` 属性が有効になります
- フロントエンド開発時は下記の「ローカル開発サーバー」を使用してください

### ローカル開発サーバーの起動（フロントエンド開発時）

Docker を使わずにローカルで開発する場合（ホットリロード対応）:

```bash
cd web
npm install
npm run dev
```

**注意**: ローカル開発時は、バックエンドAPIが http://localhost:8080 で起動している必要があります。
`web/.env.local` ファイルを作成して、以下の環境変数を設定してください:
- `BACKEND_URL=http://localhost:8080`（ローカル npm dev 用）
- `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`, `SESSION_SECRET` など

詳細は `web/README.md` を参照してください。

### その他のコマンド

```bash
# ビルド
npm run build

# 本番モードで起動
npm run start

# Lint
npm run lint
```

## Dev Container での開発（Kotlin）

このリポジトリのメイン実装は Kotlin です。`.devcontainer/` 設定で Kotlin 開発に必要なツールが揃います。

### 1. VS Code でコンテナを起動
1. VS Code でリポジトリを開く
2. Command Palette から `Dev Containers: Reopen in Container` を実行する

### 2. 初回セットアップ
コンテナ起動時に `postCreateCommand` が実行され、以下を自動セットアップします。
- Kotlin/Gradle 動作確認 (`kotlin/build.gradle.kts` がある場合）

### 3. ツール確認
コンテナ内で以下が利用可能です。
- OpenJDK 21
- Kotlin compiler (`kotlinc`)
- Gradle

### 4. PostgreSQL について
Dev Container 起動時に `db` サービス（PostgreSQL 16）が同時起動します。
アプリ側接続先は `db:5432` です。