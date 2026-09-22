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

`.env.local` ファイルを作成して設定してください：

```bash
# .env.local
BACKEND_URL=http://localhost:8080
```

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

## API エンドポイント

Next.js BFF が提供する API エンドポイント：

- `GET /api/guilds` - ギルド一覧を取得
- `GET /api/guilds/{guildId}/raids` - 指定ギルドのレイド一覧を取得
- `GET /api/raids/{raidId}` - レイド詳細を取得

これらのエンドポイントは Spring Boot API へのプロキシとして動作します。

## Spring Boot API ドキュメント

バックエンド API の詳細な仕様については、以下を参照してください：

- [`kotlin/docs/API.md`](../kotlin/docs/API.md) - Spring Boot API の完全なドキュメント

## デプロイ

詳細は [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) を参照してください。
