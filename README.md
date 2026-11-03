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
- `INTERNAL_API_TOKEN`: Web(BFF) → ボット(Backend API) 間の内部API認証トークン（必須。`openssl rand -hex 32` などで生成）


### 2. Dockerコンテナの起動
```bash
docker-compose up -d
```

起動後、以下のサービスが利用可能になります:
- **Web (Next.js)**: http://localhost:3000（メインのユーザー向けフロントエンド）
- **データベース (PostgreSQL)**: localhost:${POSTGRES_HOST_PORT}（デフォルト 5432）

**注意**: ボット（Spring Boot API）はポート 8080 で起動しますが、これは内部 API / デバッグ用です。通常は Web フロントエンド (http://localhost:3000) を使用してください。

- `docker-compose.yml` では 8080 を `127.0.0.1` のみに公開しています（ホスト外部からは直接アクセスできません）。
- `/api` 配下は `X-Internal-Api-Token` ヘッダーが `INTERNAL_API_TOKEN` と一致しない場合 401 を返します（`/health` は認証不要）。
- Railway などにデプロイする場合は、ボットのサービスに公開ドメインを付与せず、Private Networking（例: `BACKEND_URL=http://<bot-service>.railway.internal:8080`）経由でのみ Web から接続してください。bot / web の両サービスに同じ `INTERNAL_API_TOKEN` を設定します。

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

## Discord Bot の必要権限

Bot をサーバーへ招待する際は、スコープに `bot` と `applications.commands` を指定し、以下のチャンネル権限を付与してください。

| 権限 | 用途 |
|---|---|
| チャンネルを見る (`View Channel`) | 通知チャンネルの参照 |
| メッセージを送信 (`Send Messages`) | レイド通知・終了報告の送信 |
| 埋め込みリンク (`Embed Links`) | 通知/終了報告の Embed 表示 |
| メッセージ履歴を読む (`Read Message History`) | 通知メッセージを再取得し、報告の反映・ボタン無効化を行う（Bot 再起動後も動作させるため必須） |
| @everyone、@here、全てのロールにメンションする (`Mention Everyone`) | 開始通知の `@everyone`（任意。無い場合はメンションなしで通知） |

`メッセージ履歴を読む` が無い場合、報告後の Embed 更新やレイド終了時のボタン無効化が行われません（ログに warn が出力されます）。

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
- `INTERNAL_API_TOKEN`（Backend 側と同じ値。Backend 起動時の環境変数にも設定が必要）
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

### 依存関係の脆弱性対策

> **対象範囲**: 現在の自動検知の対象は **web（`web/`）と GitHub Actions のみ**です。bot 側（ルートの `Dockerfile` の `eclipse-temurin:21-*`、`kotlin/` の Gradle 依存）と `docker-compose.yml` の `postgres:16-alpine` は対象外のため、手動で更新を確認してください。

- **CI**: `.github/workflows/pr-test.yml` で PR ごとに以下を実行します（develop 向け・master 向けの両方）。
  - `web` ジョブ: `npm ci` → `lint` → `test` → `build` → `npm audit --omit=dev --audit-level=high`（本番依存に high 以上の脆弱性があると失敗）。Node.js は `web/Dockerfile` の `base` ステージと同じバージョンを使います。
  - `web-docker` ジョブ: 本番と同じ `web/Dockerfile` でイメージをビルドし、イメージ内の Node.js バージョンとコンテナの起動を確認します。
- **Dependabot**: `.github/dependabot.yml` で以下の更新 PR を毎週（月曜）**develop 向けに**作成します。
  - `npm`（`/web`）: `next` / `eslint-config-next`、React 系、開発依存のマイナー/パッチ更新をそれぞれ 1 つの PR にグループ化
  - `docker`（`/web`）: `web/Dockerfile` のベースイメージ `node:22.x.y-alpine`
  - `github-actions`: マイナー/パッチ更新を 1 つの PR にグループ化（メジャー更新は個別 PR）
- `node` イメージと `@types/node` のメジャー更新は自動 PR の対象外です。実行環境の LTS を切り替える際に手動で更新してください。それ以外の npm パッケージ（`next`、`react` など）はメジャー更新も PR が届きます。
- リポジトリの Settings → Code security で **Dependabot alerts / security updates** を有効にしておくと、週次スケジュールを待たずに脆弱性修正の PR が届きます。

#### ブランチと環境の対応

| ブランチ | 環境 | Railway サービス |
|---|---|---|
| `develop` | 開発環境 | `dev_web` / `dev_bot` |
| `master` | 本番環境 | `web` / `bot` |

各サービスは対象ブランチへの push で自動デプロイされます。Dependabot の PR は develop 向けのため、**マージしただけでは本番（master）には反映されません**。

#### Node.js / 依存関係のセキュリティ更新を本番へ反映する手順

`web/Dockerfile` のベースイメージはパッチバージョンまで固定しているため、Node.js 本体の修正はイメージを更新・再ビルドしない限り取り込まれません。[Node.js のセキュリティリリース](https://nodejs.org/en/blog/vulnerability) や依存パッケージの high 以上の脆弱性が出たら、以下を行ってください。

1. **develop へ反映**
   - Dependabot の更新 PR を確認する。届いていなければ、`web/Dockerfile` の `base` ステージ（`FROM node:22.x.y-alpine AS base`、1 箇所のみ）を手動で更新して develop 向けの PR を作成する
   - CI の `web` / `web-docker` ジョブが成功したことを確認してマージする
   - `dev_web` が自動で再デプロイされるので、開発環境で動作を確認する
2. **master（本番）へ反映**: 次のどちらかで行います。
   - **リリース**: develop に未リリースの変更があっても一緒に出してよい場合は、通常どおり develop → master のリリース PR（`Release YYYY.MM.DD`）を作成してマージする
   - **ホットフィックス**: develop の未リリース変更を本番に出したくない場合は、master から作業ブランチを作り、develop に入った修正コミットだけを取り込んで master 向けの PR を作成する
     ```bash
     git fetch origin
     git switch -c hotfix/node-<version> origin/master
     # Squash merge した場合: develop 上の squash されたコミット 1 つを指定する
     git cherry-pick <squash コミット>
     # Merge commit でマージした場合: マージコミットを -m 1 付きで指定する
     #（グループ化された PR などでコミットが複数あっても、PR の変更をまとめて取り込める）
     git cherry-pick -m 1 <マージコミット>
     git push -u origin hotfix/node-<version>
     ```
     どちらの方式でマージしたかは、develop の履歴（`git log --oneline --first-parent origin/develop`）で確認できます（`Merge pull request #...` ならマージコミット）。
     修正はすでに develop に入っているため、develop へのバックマージは不要です。
   - master へのマージで Railway の `web` が自動で再ビルド・再デプロイされます。
3. **本番で反映を確認**
   - Railway: `web` サービスの最新デプロイのビルドログで、ベースイメージ（`node:22.x.y-alpine`）が更新後のバージョンになっていることを確認する
   - Docker Compose で運用している場合: `docker-compose build --pull web && docker-compose up -d web` で再ビルドし、`docker-compose exec web node -v` でバージョンを確認する

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