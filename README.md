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


### 2. Dockerコンテナの起動
```bash
docker-compose up -d
```

### 3. ログの確認
```bash
docker-compose logs -f bot
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

### データベースのバックアップ
```bash
docker-compose exec db pg_dump -U postgres nikke_unionraid > backup.sql
```

### データベースのリストア
```bash
docker-compose exec -T db psql -U postgres nikke_unionraid < backup.sql
```

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

詳細な仕様は [go/docs/go-migration-definition-of-done.md](go/docs/go-migration-definition-of-done.md) を参照してください。

## Dev Container での開発（Python / Go / Kotlin）

このリポジトリは `.devcontainer/` 設定で、Python・Go・Kotlin を同じ開発コンテナで扱えます。

### 1. VS Code でコンテナを起動
1. VS Code でリポジトリを開く
2. Command Palette から `Dev Containers: Reopen in Container` を実行する

### 2. 初回セットアップ
コンテナ起動時に `postCreateCommand` が実行され、以下を自動セットアップします。
- Python 依存 (`requirements.txt`)
- Go モジュール (`go/go.mod`)
- Kotlin/Gradle 動作確認 (`kotlin/build.gradle.kts` がある場合)

### 3. ツール確認
コンテナ内で以下が利用可能です。
- Python 3
- Go
- OpenJDK 21
- Kotlin compiler (`kotlinc`)
- Gradle

### 4. PostgreSQL について
Dev Container 起動時に `db` サービス（PostgreSQL 16）が同時起動します。
アプリ側接続先は `db:5432` です。