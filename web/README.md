# NIKKE Union Raid Bot - Web フロントエンド

このプロジェクトは [Next.js](https://nextjs.org) で構築された、NIKKE Union Raid Bot のウェブフロントエンドです。

## アーキテクチャ

```
ブラウザ → Next.js Route Handlers (BFF) → Spring Boot API → PostgreSQL
```

- **Next.js は薄い BFF (Backend For Frontend) として動作します**
- **Next.js は PostgreSQL に直接接続しません**
- すべてのデータアクセスは Spring Boot API 経由で行われます

## 環境変数の設定

### 必要な環境変数

Next.js プロジェクトでは、以下の環境変数が必要です：

```bash
# Spring Boot API のベース URL
BACKEND_URL=http://localhost:8080

# Discord OAuth 設定
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
DISCORD_REDIRECT_URI=http://localhost:3000/api/auth/discord/callback

# セッション暗号化キー（本番環境では必ず変更してください）
SESSION_SECRET=complex_password_at_least_32_characters_long
```

### Discord Developer Portal の設定

1. [Discord Developer Portal](https://discord.com/developers/applications) にアクセス
2. 新しいアプリケーションを作成（または既存のものを選択）
3. OAuth2 → General ページで以下を設定：
   - **Redirects** に `http://localhost:3000/api/auth/discord/callback` を追加
   - 本番環境では本番URLも追加（例: `https://your-domain.com/api/auth/discord/callback`）
4. OAuth2 → General ページから以下をコピー：
   - **Client ID** → `DISCORD_CLIENT_ID`
   - **Client Secret** → `DISCORD_CLIENT_SECRET`（Reset Secret で生成可能）

**重要**: Redirect URI は Discord Developer Portal に登録した値と完全に一致する必要があります。

### OAuth スコープ

このアプリケーションは以下の Discord OAuth スコープを使用します：

- `identify`: ユーザー情報の取得
- `guilds`: ユーザーが所属するサーバー（ギルド）一覧の取得

### ローカル開発

`web/.env.local` ファイルを作成して設定してください：

```bash
# web/.env.local
BACKEND_URL=http://localhost:8080
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
DISCORD_REDIRECT_URI=http://localhost:3000/api/auth/discord/callback
SESSION_SECRET=complex_password_at_least_32_characters_long
```

**注意**: Next.js は `web/.env.local` (`package.json` と同じディレクトリ) を読み込みます。プロジェクトルートの `.env` は Docker Compose での環境変数注入用です (#41)。

### Docker Compose での実行

Docker Compose でコンテナ間通信を行う場合は、サービス名を使用してください：

```bash
# コンテナ間通信の例
BACKEND_URL=http://bot:8080
```

**注意**: コンテナ内では `localhost` はコンテナ自身を指すため、他のコンテナにアクセスできません。Spring Boot API のサービス名（`bot`）を使用してください。

### 不要な環境変数

**以下の環境変数は Next.js では不要です（Spring Boot 側でのみ使用されます）：**

- `DATABASE_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- その他のデータベース接続情報

Next.js プロジェクトに PostgreSQL クライアント（`pg` など）をインストールする必要はありません。

## 開発サーバーの起動

```bash
npm install
npm run dev
```

開発サーバーは [http://localhost:3000](http://localhost:3000) で起動します。

## 画面

### レイド一覧ページ

[http://localhost:3000/raids](http://localhost:3000/raids) でレイド一覧を表示できます。

**機能:**
- ギルド選択（暫定: 認証前の画面確認用。#42 後に session の所属ギルドへ置き換え予定）
- レイド名、開始/終了日時、順位、パーセンテージ、ステータス（進行中/終了）を表示
- レイド詳細ページへのリンク（詳細ページは #40 で本実装予定）

## テスト

```bash
npm test
```

Node.js の組み込みテストランナーで BFF ロジックのユニットテストを実行します。

## API エンドポイント

Next.js BFF が提供する API エンドポイント：

### 認証エンドポイント

- `GET /api/auth/discord` - Discord OAuth ログインを開始
- `GET /api/auth/discord/callback` - Discord OAuth コールバック（認可コード交換）
- `POST /api/auth/logout` - ログアウト（セッション破棄）

### データエンドポイント

- `GET /api/guilds` - ギルド一覧を取得
- `GET /api/guilds/{guildId}/raids` - 指定ギルドのレイド一覧を取得
- `GET /api/raids/{raidId}` - レイド詳細を取得

データエンドポイントは Spring Boot API へのプロキシとして動作します。

## 認証フロー

1. ユーザーが保護されたページ（例: `/raids`）にアクセス
2. 未認証の場合、`/api/auth/discord` にリダイレクト
3. Discord の認可ページでユーザーが承認
4. `/api/auth/discord/callback` でコールバック受信
5. BFF が認可コードをアクセストークンに交換（サーバーサイドのみ）
6. Discord API から `/users/@me` と `/users/@me/guilds` を取得
7. Spring Boot API の `/api/guilds` と突き合わせて、登録済みギルドのみに絞り込み
8. httpOnly + SameSite の暗号化セッションクッキーに `userId` と `guildIds` を保存
9. `/raids` にリダイレクト

**セキュリティ対策:**

- CSRF 対策: `state` パラメータでリクエストを検証
- アクセストークンとクライアントシークレットはブラウザに送信されません（サーバーサイドのみ）
- セッションクッキーは httpOnly + SameSite で保護され、暗号化されています

### BFF の動作仕様

- **キャッシュなし**: すべてのリクエストは `cache: "no-store"` で Spring API から最新データを取得します
- **タイムアウト**: Spring API へのリクエストは 5 秒でタイムアウトします（504 Gateway Timeout）
- **エラーハンドリング**: ネットワークエラーやタイムアウトは固定メッセージで返され、内部エラー詳細はサーバーログのみに記録されます
- **バリデーション**: パスパラメータ（guildId, raidId）は数値のみ受け付けます（不正な形式は 400 Bad Request）

## Spring Boot API ドキュメント

バックエンド API の詳細な仕様については、以下を参照してください：

- [`kotlin/docs/API.md`](../kotlin/docs/API.md) - Spring Boot API の完全なドキュメント

## デプロイ

詳細は [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) を参照してください。
