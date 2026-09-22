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

Next.js プロジェクトでは、以下の環境変数のみが必要です：

```bash
# Spring Boot API のベース URL
BACKEND_URL=http://localhost:8080
```

### ローカル開発

`.env.local` ファイルを作成して設定してください：

```bash
# .env.local
BACKEND_URL=http://localhost:8080
```

### Docker Compose での実行

Docker Compose でコンテナ間通信を行う場合は、サービス名を使用してください：

```bash
# コンテナ間通信の例
BACKEND_URL=http://api:8080
```

**注意**: コンテナ内では `localhost` はコンテナ自身を指すため、他のコンテナにアクセスできません。Spring Boot API のサービス名（例: `api`）を使用してください。

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

- `GET /api/guilds` - ギルド一覧を取得
- `GET /api/guilds/{guildId}/raids` - 指定ギルドのレイド一覧を取得
- `GET /api/raids/{raidId}` - レイド詳細を取得

これらのエンドポイントは Spring Boot API へのプロキシとして動作します。

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
