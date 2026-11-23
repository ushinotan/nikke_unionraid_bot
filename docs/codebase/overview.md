# 概要

## スタック
- backend（`kotlin/`）: Kotlin 2.3 + Spring Boot 4、JDA 6（Discord ボット）、Komapper 6（JDBC、KSP でメタモデル生成）、PostgreSQL 16。テストは JUnit 5。
- frontend（`web/`）: Next.js 16（App Router）+ React 19 + Tailwind CSS 4、iron-session。テストは `node:test`（`web/__tests__/`）。
- 実行環境: `docker-compose.yml` で `bot`（8080）、`web`（3000）、`db`（5432）。本番は Railway（`DEPLOYMENT.md`）。

## 構成
```
Discord ──(スラッシュコマンド/ボタン)──> bot (JDA リスナー) ──> RaidDomainService ──> Repository ──> PostgreSQL
ブラウザ ──> web (Next.js) ──API Route──> bot の /api/** (X-Internal-Api-Token) ──> Repository
```
- 書き込み（レイド作成・終了・3凸報告）は今のところ Discord 経由だけ。REST API（`/api/**`）は読み取り専用。
- web はバックエンドを直接公開せず、API Route（BFF）がセッションを確認してから `fetchFromBackend` で bot を呼ぶ。

## 共通の約束事
- 時刻は DB に UTC で保存し、表示だけ `DEFAULT_TIMEZONE_HOURS`（既定 9 = JST）でずらす（`util/TimeUtils.kt`）。
- スキーマの正は `init.sql`。既存 DB 向けの変更は `migrations/YYYYMMDD_*.sql` に冪等な SQL で追加し、`init.sql` にも反映する。bot コンテナが起動時に `init.sql` を流す。
- ドメインのエラーは `RaidDomainError`（sealed class）で返し、メッセージは日本語。サービスは `Result<T>` を返す。
- 1 ギルドにつき進行中のレイドは 1 件まで。
- `/api/**` は `X-Internal-Api-Token` が `INTERNAL_API_TOKEN` と一致しないと 401（未設定なら全拒否）。`/health` は認証なし。
- web の書き込み系 API Route は `requireRaidEditPermission` でサーバー側の権限チェックを必ず通す（`lib/raid-authorization.ts` のコメント参照）。
- `web/` の Next.js 16 は破壊的変更があるため、コードを書く前に `web/node_modules/next/dist/docs/` を読む（`web/AGENTS.md`）。`middleware.ts` は `proxy.ts`。
