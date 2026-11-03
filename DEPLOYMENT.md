# デプロイガイド

本番環境へのデプロイと運用手順をまとめたドキュメントです。
ローカル開発環境のセットアップは [README.md](README.md) を参照してください。

- [1. 構成概要](#1-構成概要)
- [2. 事前準備（Discord Developer Portal）](#2-事前準備discord-developer-portal)
- [3. 環境変数一覧](#3-環境変数一覧)
- [4. Railway へのデプロイ](#4-railway-へのデプロイ)
- [5. VPS などで docker compose を使ってデプロイする場合](#5-vps-などで-docker-compose-を使ってデプロイする場合)
- [6. DB スキーマとマイグレーション](#6-db-スキーマとマイグレーション)
- [7. デプロイ後の動作確認](#7-デプロイ後の動作確認)
- [8. アップデートとロールバック](#8-アップデートとロールバック)
- [9. バックアップとリストア](#9-バックアップとリストア)
- [10. トラブルシューティング](#10-トラブルシューティング)

---

## 1. 構成概要

```
            (HTTPS)
ユーザー ───────────▶ web (Next.js / :3000)  ※公開するのはこのサービスだけ
                         │  X-Internal-Api-Token ヘッダー付きで呼び出す
                         ▼  （プライベートネットワーク）
                      bot (Spring Boot + JDA / :8080)  ◀──▶ Discord Gateway / API
                         │
                         ▼
                      db (PostgreSQL 16)
```

| サービス | ビルド元 | ポート | 役割 | 外部公開 |
|---|---|---|---|---|
| bot | ルートの `Dockerfile` | 8080 | Discord Bot 本体、内部 API（`/api/**`）、`/health` | **しない** |
| web | `web/Dockerfile` | 3000 | ユーザー向け Web UI、Discord OAuth ログイン、BFF | する |
| db | `postgres:16-alpine`（またはマネージド PostgreSQL） | 5432 | データ永続化 | **しない** |

セキュリティ上の前提:

- bot の `/api/**` は `X-Internal-Api-Token` ヘッダーが `INTERNAL_API_TOKEN` と一致しない場合 `401` を返します。`INTERNAL_API_TOKEN` 自体が未設定の場合はすべて拒否します（fail-closed）。
- この認証があっても、bot と db はインターネットに直接公開しないでください。web からプライベートネットワーク経由でのみ接続させます。
- **bot は 1 インスタンスだけで動かしてください。** 複数のレプリカで動かすと、Discord イベントの処理や通知スケジュールが重複し、同じ通知が二重に送られます。

## 2. 事前準備（Discord Developer Portal）

[Discord Developer Portal](https://discord.com/developers/applications) でアプリケーションを作成し、次の設定を行います。

1. **Bot トークン**: `Bot` タブでトークンを発行し、`DISCORD_TOKEN` に設定します。
2. **OAuth2 クライアント**: `OAuth2` タブで `Client ID` / `Client Secret` を取得し、`DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` に設定します。
3. **Redirect URI の登録**: `OAuth2` → `Redirects` に本番の URL を追加します。
   ```
   https://<公開ドメイン>/api/auth/discord/callback
   ```
   この値は `DISCORD_REDIRECT_URI` と**完全に一致**している必要があります（スキーム、ホスト、末尾のスラッシュの有無まで）。
   なお、Web ログイン時に要求するスコープは `identify guilds` です。
4. **Bot の招待**: スコープに `bot` と `applications.commands` を指定して、サーバーに招待します。必要な権限は README の「[Discord Bot の必要権限](README.md#discord-bot-の必要権限)」を参照してください。

> スラッシュコマンドはグローバル登録されるため、Discord 側に反映されるまで最大 1 時間かかることがあります。
> `DEV_GUILD_ID` を設定すると、そのギルドにだけ即時登録されます。これは開発用の設定なので、本番では設定しないでください。

## 3. 環境変数一覧

### bot

| 変数 | 必須 | 説明 |
|---|---|---|
| `DISCORD_TOKEN` | ✅ | Discord Bot のトークン |
| `INTERNAL_API_TOKEN` | ✅ | web → bot の内部 API 認証トークン。**web と同じ値**にします。生成例: `openssl rand -hex 32` |
| `DATABASE_URL` | ✅ | JDBC 形式の接続 URL。例: `jdbc:postgresql://<host>:5432/nikke_unionraid`（ユーザー名とパスワードは含めない） |
| `POSTGRES_USER` | ✅ | DB ユーザー名。Spring Boot と起動時のスキーマ適用の両方で使います（デフォルト `postgres`） |
| `POSTGRES_PASSWORD` | ✅ | DB パスワード |
| `POSTGRES_HOST` | ✅ | 起動時のスキーマ適用（`psql`）に使うホスト。デフォルトの `db` は compose 用なので、それ以外の環境では必ず設定します |
| `POSTGRES_PORT` | – | スキーマ適用に使うポート（デフォルト `5432`） |
| `POSTGRES_DB` | – | スキーマ適用に使う DB 名（デフォルト `nikke_unionraid`） |
| `DEFAULT_TIMEZONE_HOURS` | – | 表示用タイムゾーンの UTC オフセット（デフォルト `9` = JST） |
| `DEV_GUILD_ID` | – | 開発用。本番では未設定にします |

> `DATABASE_URL`（Spring Boot 用）と `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB`（起動時に `init.sql` を適用する `psql` 用）は**別々に参照されます**。両方が同じ DB を指すように設定してください。

### web

| 変数 | 必須 | 説明 |
|---|---|---|
| `BACKEND_URL` | ✅ | bot のプライベート URL。例: `http://<bot のプライベートホスト>:8080` |
| `INTERNAL_API_TOKEN` | ✅ | bot と同じ値 |
| `DISCORD_CLIENT_ID` | ✅ | OAuth2 の Client ID |
| `DISCORD_CLIENT_SECRET` | ✅ | OAuth2 の Client Secret |
| `DISCORD_REDIRECT_URI` | ✅ | `https://<公開ドメイン>/api/auth/discord/callback`。Developer Portal に登録した値と一致させます |
| `SESSION_SECRET` | ✅ | セッション Cookie の暗号化キー（32 文字以上）。生成例: `openssl rand -hex 32` |
| `APP_BASE_URL` | 推奨 | 公開 URL（例: `https://<公開ドメイン>`）。リダイレクト先の決定と Secure Cookie の判定に使います。未設定の場合は、`x-forwarded-*` ヘッダー → `DISCORD_REDIRECT_URI` の origin の順に自動検出します |
| `NODE_ENV` | – | `web/Dockerfile` で `production` に設定済み |

> セッション Cookie の `Secure` 属性は、次の 2 つを満たす場合に付与されます。本番では `https://` の URL を設定してください。
> - `NODE_ENV=production` である
> - `APP_BASE_URL`（未設定なら `DISCORD_REDIRECT_URI`）が `https://` で始まる

## 4. Railway へのデプロイ

Railway では、1 つのプロジェクトに **PostgreSQL・bot・web の 3 サービス**を作成します。

### 4.1 PostgreSQL

1. プロジェクトに PostgreSQL を追加します（以下、サービス名は `Postgres` とします）。
2. TCP Proxy（公開接続）は、バックアップ取得などで必要なときだけ有効にしてください。

### 4.2 bot サービス

1. GitHub リポジトリからサービスを作成します。
   - **Root Directory**: 指定しません（リポジトリルート）。ルートの `Dockerfile` が使われます。
2. **Variables** を設定します（`${{ ... }}` は Railway の参照変数です）。
   ```
   DISCORD_TOKEN=<Bot トークン>
   INTERNAL_API_TOKEN=<openssl rand -hex 32 で生成した値>
   DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   POSTGRES_HOST=${{Postgres.PGHOST}}
   POSTGRES_PORT=${{Postgres.PGPORT}}
   POSTGRES_DB=${{Postgres.PGDATABASE}}
   POSTGRES_USER=${{Postgres.PGUSER}}
   POSTGRES_PASSWORD=${{Postgres.PGPASSWORD}}
   DEFAULT_TIMEZONE_HOURS=9
   ```
3. **Networking**: **公開ドメインは付与しません。** Private Networking のホスト名（例: `bot.railway.internal`）を控えておきます。
4. **Health Check**: Healthcheck Path に `/health` を設定します（認証不要で `{"status":"ok"}` を返します）。
5. **Replicas は 1** のままにします（[構成概要](#1-構成概要)を参照）。

### 4.3 web サービス

1. 同じリポジトリから 2 つ目のサービスを作成します。
   - **Root Directory**: `web`（`web/Dockerfile` が使われます）
2. **Variables** を設定します。
   ```
   BACKEND_URL=http://${{bot.RAILWAY_PRIVATE_DOMAIN}}:8080
   INTERNAL_API_TOKEN=${{bot.INTERNAL_API_TOKEN}}
   DISCORD_CLIENT_ID=<Client ID>
   DISCORD_CLIENT_SECRET=<Client Secret>
   DISCORD_REDIRECT_URI=https://<公開ドメイン>/api/auth/discord/callback
   APP_BASE_URL=https://<公開ドメイン>
   SESSION_SECRET=<openssl rand -hex 32 で生成した値>
   PORT=3000
   ```
   - `bot` の部分は、実際の bot サービス名に置き換えてください。
   - `INTERNAL_API_TOKEN` は bot の値を参照させておくと、2 か所で管理せずに済みます。
3. **Networking**: 公開ドメインを付与します（ターゲットポートは `3000`）。カスタムドメインを使う場合は、`DISCORD_REDIRECT_URI`・`APP_BASE_URL`・Developer Portal の Redirect URI の 3 つをそのドメインに合わせます。

### 4.4 初回デプロイ

1. bot をデプロイします。ログに `Schema ensure completed successfully` が出て、Discord 上で Bot がオンラインになることを確認します。
2. web をデプロイします。公開ドメインにアクセスし、ログインできることを確認します（[7. デプロイ後の動作確認](#7-デプロイ後の動作確認)）。

## 5. VPS などで docker compose を使ってデプロイする場合

`docker-compose.yml` をそのまま本番で使う場合の注意点をまとめます。基本的な起動手順は README と同じです。

```bash
cp env.example .env   # 値を本番用に編集
```

```bash
docker compose up -d --build
```

- **HTTPS 化**: web（:3000）の前段にリバースプロキシ（Caddy、nginx など）を置いて TLS を終端し、`X-Forwarded-Proto` と `X-Forwarded-Host` を転送します。`APP_BASE_URL` と `DISCORD_REDIRECT_URI` は `https://` の公開 URL にします。
- **db のポート**: `docker-compose.yml` は db の `${POSTGRES_HOST_PORT}:5432` を**全インターフェースで公開**しています。本番ではファイアウォールで閉じるか、`127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432` に変更してください。
- **bot のポート**: 8080 は `127.0.0.1` にだけ公開されているため、追加の対応は不要です。
- **web のポート**: リバースプロキシを同じホストで動かす場合は、3000 もファイアウォールで閉じ、プロキシ経由でのみアクセスさせることをおすすめします。
- **自動起動**: 各サービスには `restart: unless-stopped` が設定されています。Docker デーモンの自動起動を有効にしておけば（例: `sudo systemctl enable docker`）、ホストを再起動してもサービスが復帰します。

## 6. DB スキーマとマイグレーション

### 自動適用（init.sql）

bot コンテナは起動のたびに、Java アプリより先に `psql` で `init.sql` を適用します（`Dockerfile` の `CMD`）。

- `init.sql` は `CREATE TABLE IF NOT EXISTS` などの冪等な SQL で書かれているため、毎回実行しても問題ありません。
- DB に接続できるまで最大 30 秒リトライします。適用に失敗した場合、コンテナは起動しません。スキーマが不完全なまま動き出すのを防ぐためです。
- **新規環境では、手動でマイグレーションを実行する必要はありません。**

### 既存環境（migrations/）

`migrations/` の SQL は、**既存の DB を新しいスキーマに追従させるためのもの**です。対応する冪等な `ALTER` が `init.sql` に含まれていないものは、手動で適用する必要があります。

| ファイル | 内容 | 手動適用 |
|---|---|---|
| `20260720_add_finished_at_to_union_raids.sql` | `union_raids.finished_at` の追加と、終了済みレイドの値の埋め戻し | **必要**（`init.sql` に `ALTER` がないため） |
| `20261012_add_notify_message_id_to_union_raids.sql` | `union_raids.notify_message_id` の追加 | 不要（`init.sql` で自動適用） |

適用手順:

1. 適用前に[バックアップ](#9-バックアップとリストア)を取得します。
2. ファイル名（日付）の古い順に実行します。どのファイルも `IF NOT EXISTS` などで冪等にしてありますが、実行前に内容を確認してください。
   ```bash
   # docker compose の場合
   docker compose exec -T db psql -U postgres -d nikke_unionraid -v ON_ERROR_STOP=1 < migrations/20260720_add_finished_at_to_union_raids.sql
   ```
   ```bash
   # Railway など外部の DB の場合（接続 URL は Postgres サービスの DATABASE_PUBLIC_URL など）
   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f migrations/20260720_add_finished_at_to_union_raids.sql
   ```
3. マイグレーションが必要なリリースでは、**マイグレーションを適用してから**新しいバージョンの bot をデプロイします。

## 7. デプロイ後の動作確認

- [ ] bot のログに `Schema ensure completed successfully` が出ている
- [ ] bot のログに起動エラーが出ていない（Discord の READY を 30 秒以内に受信できないと、bot は起動を中止します）
- [ ] bot のログに `INTERNAL_API_TOKEN が未設定のため…` という警告が出ていない
- [ ] `/health` が `{"status":"ok"}` を返す（bot は外部に公開しないため、ホストやプライベートネットワーク内から確認します）
  ```bash
  # docker compose の場合（ホストから）
  curl -s http://127.0.0.1:8080/health
  ```
- [ ] Discord 上で Bot がオンラインになっており、スラッシュコマンドが表示される
- [ ] web の公開 URL から Discord ログインができ、ギルドとレイドの情報が表示される
- [ ] bot の 8080 と db の 5432 がインターネットから到達できない

## 8. アップデートとロールバック

### アップデート

1. リリースに含まれる変更を確認します。PR の「マイグレーション/リリース手順」と「影響範囲」の欄を見てください。
2. DB の変更がある場合は、バックアップを取得してから[マイグレーション](#既存環境migrations)を適用します。
3. 環境変数の追加・変更がある場合は、デプロイ前に設定しておきます。
4. デプロイします。
   - **Railway**: 接続しているブランチ（`main` など）にマージすると、自動でデプロイされます。
   - **docker compose**:
     ```bash
     git pull
     ```
     ```bash
     docker compose up -d --build
     ```
5. [デプロイ後の動作確認](#7-デプロイ後の動作確認)を行います。

> bot を再起動しても、進行中のレイドの通知スケジュールと通知メッセージ（報告ボタン・Embed）は DB から復元されます。

### ロールバック

- **Railway**: Deployments 画面から以前のデプロイを選び、`Redeploy` / `Rollback` を実行します。
- **docker compose**:
  ```bash
  git checkout <戻したいコミット>
  ```
  ```bash
  docker compose up -d --build
  ```
- マイグレーションはカラムの追加など後方互換性のある変更が中心なので、通常はアプリを戻すだけで動作します。スキーマごと戻す必要がある場合は、バックアップからリストアします。

## 9. バックアップとリストア

### docker compose 環境

README の「[データベースのバックアップ（手動）](README.md#データベースのバックアップ手動)」と「[月次自動バックアップの設定](README.md#月次自動バックアップの設定おすすめ)」（`scripts/backup-db.sh` + cron）を参照してください。
バックアップファイルは、別のホストやオブジェクトストレージにもコピーしておくことをおすすめします。

### Railway 環境

`scripts/backup-db.sh` は docker compose を前提にしているため、Railway では使えません。次のいずれかの方法でバックアップを取得します。

- **Railway のバックアップ機能**: PostgreSQL サービスのボリュームでバックアップ機能を使える場合は、スケジュールを設定します。
- **手動で pg_dump する**: Postgres サービスの TCP Proxy を一時的に有効にし、ローカルから接続して取得します。
  ```bash
  pg_dump "<DATABASE_PUBLIC_URL>" | gzip > backup-$(date +%Y-%m-%d).sql.gz
  ```
  ローカルの `pg_dump` は、サーバーと同じかそれより新しいメジャーバージョン（16 以上）を使ってください。取得が終わったら、TCP Proxy を無効に戻します。

### リストア

```bash
# docker compose
gunzip -c backup-YYYY-MM-DD.sql.gz | docker compose exec -T db psql -U postgres nikke_unionraid
```

```bash
# 外部の DB
gunzip -c backup-YYYY-MM-DD.sql.gz | psql "<接続 URL>"
```

リストア中は bot を停止しておき、完了してから起動してください。

## 10. トラブルシューティング

| 症状 | 原因と対処 |
|---|---|
| bot が `Waiting for database... (30/30)` のあと終了する | `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` が間違っています。デフォルトの `db` は compose 専用なので、Railway では参照変数で設定します |
| bot は起動したが、DB エラーで停止する | `DATABASE_URL` が JDBC 形式（`jdbc:postgresql://...`）になっているか、`psql` 用の変数と同じ DB を指しているかを確認します |
| bot が Discord の READY を待つ途中でタイムアウトし、起動を中止する | `DISCORD_TOKEN` が正しいか、Discord に障害が起きていないかを確認します |
| web で API が 401 になる | bot と web の `INTERNAL_API_TOKEN` が一致していません（値の前後の空白や改行にも注意） |
| web で API が 5xx やタイムアウトになる | `BACKEND_URL` のホスト・ポート（8080）が間違っているか、bot が停止しています。Railway では Private Networking のホスト名を使います |
| Discord ログインで `Invalid OAuth2 redirect_uri` が出る | `DISCORD_REDIRECT_URI` と Developer Portal の Redirect URI が一致していません |
| ログインしたあと、ログイン画面に戻される | HTTP でアクセスしているのに Secure Cookie が付いているか、`APP_BASE_URL` のドメインが実際のアクセス先と異なっている可能性があります。`APP_BASE_URL` を `https://` の公開 URL に揃えます |
| リダイレクト先が `localhost` などになる | `APP_BASE_URL` を設定します |
| スラッシュコマンドが表示されない | グローバル登録の反映には最大 1 時間かかります。Bot の招待時に `applications.commands` スコープを付けたかも確認します |
| 報告後に Embed が更新されない、またはボタンが無効化されない | Bot に `メッセージ履歴を読む` 権限がありません（README の権限表を参照） |
| 通知が二重に届く | bot が複数のインスタンスで動いています。レプリカ数を 1 にします |
