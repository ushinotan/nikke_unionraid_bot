# frontend（`web/`）

Next.js 16（App Router）+ React 19 + TypeScript（strict）+ Tailwind CSS 4、セッションは iron-session。

- **必須**: Next.js 16 には破壊的変更がある。コードを書く前に `web/node_modules/next/dist/docs/` の該当ガイドを読む（`web/AGENTS.md`）。たとえば `middleware.ts` は `proxy.ts` になっている。

## ディレクトリ構成

| 場所 | 置くもの |
| --- | --- |
| `app/**/page.tsx` | 画面 |
| `app/api/**/route.ts` | API Route（BFF）。ブラウザからはここだけを呼ぶ |
| `app/_components/` | 画面で共有する UI とクライアント側のユーティリティ |
| `lib/` | サーバー側の処理（セッション、権限、バックエンド呼び出し、API の型） |
| `proxy.ts` | 未ログイン時のリダイレクトなど、リクエスト前段の処理 |
| `__tests__/` | `node:test` のテスト |

- **必須**: ブラウザから Kotlin のバックエンドを直接呼ばない。必ず API Route を通し、API Route から `fetchFromBackend` で呼ぶ。
- **必須**: `lib/` のサーバー専用モジュール（`session.ts`, `backend-client.ts` など）をクライアントコンポーネントから import しない。型だけなら `import type` で読んでよい。

## 命名と書式

- ファイル名は `kebab-case`（`backend-client.ts`, `use-now.ts`）。ただし Next.js の予約ファイル（`page.tsx`, `route.ts`, `layout.tsx`）はその名前に従う。
- コンポーネントは `PascalCase`、関数と変数は `camelCase`、モジュール定数は `UPPER_SNAKE_CASE`。フックは `use` で始める。
- 文字列はダブルクォート、文末にセミコロン、インデント 2 スペース。
- **必須**: `npm run lint`（eslint-config-next）を通す。
- **必須**: import は `@/` のパスエイリアスを使う。`app/` 内の近いファイル同士は相対パスでもよい。

## TypeScript

- **必須**: `any` を使わない。外部から来た値は `unknown` で受けて絞り込む。
- **必須**: 型だけの import は `import type` にする。
- **推奨**: 状態の分岐は判別共用体で表す（例: `{ ok: true; ... } | { ok: false; response: NextResponse }`）。
- **推奨**: 関数の引数には必要なフィールドだけを `Pick<>` で要求する（例: `Pick<SessionData, "userId" | "guildIds" | "guilds">`）。テストで組み立てやすくなるため。
- バックエンドのレスポンス型は `lib/api-types.ts` にまとめ、Kotlin の DTO（`kotlin/.../dto/ApiResponses.kt`）と同じ名前・同じ形にする。Kotlin 側を変えたら同じ PR でこちらも直す。

## API Route（BFF）

- **必須**: 先頭でパスパラメーターを検証する（`validateSnowflakeId` / `validateNumericId`）。不正なら 400 を返す。
- **必須**: バックエンド呼び出しは `try` で囲み、失敗は `handleBackendError(error)` で返す。
- **必須**: エラーレスポンスは `{ error: "UPPER_SNAKE_CASE", message: "..." }` の形にする（バックエンドと同じ）。
- **必須**: 書き込みを伴うルートは、バックエンドへ転送する前に `requireRaidEditPermission` を呼ぶ。`ok: false` ならその `response` をそのまま返す。
- **推奨**: テストしやすいよう、`route.ts` は薄くし、本体は session を引数に取る関数に切り出す。
- **必須**: 権限判定に使う値（ギルド ID、レコードの持ち主）はバックエンドから取り直したものを使う。リクエストの body やクエリの値をそのまま信用しない（詳しくは `lib/raid-authorization.ts` のコメント）。
- **必須**: 画面でボタンを隠すのは補助にすぎない。防御はサーバー側で行う。
- **必須**: リダイレクト先や Cookie の Secure 判定に使う自サイトの origin は `lib/origin.ts`（`getPublicOrigin` / `getConfiguredOrigin`）から取る。`Host` ヘッダーなどリクエストの値をそのまま使わない。

## React

- **推奨**: 既定はサーバーコンポーネント。状態やブラウザ API が要るときだけ `"use client"` を付ける。
- **必須**: `useEffect` 内の非同期処理は、アンマウント後に state を更新しないようにキャンセルを扱う（`let cancelled = false` とクリーンアップ関数）。
- **必須**: 読み込み中・エラー・データなしの 3 状態をそれぞれ表示する（`StateScreen`, `Spinner` を使う）。
- **推奨**: ボタンやカードは `app/_components/ui.tsx` の既存部品とクラス定数（`primaryButtonClass` など）を使い、同じ見た目を新しく書かない。

## スタイル

- **必須**: スタイルは Tailwind のユーティリティクラスで書く。色は `globals.css` で定義したテーマトークン（`bg-surface`, `text-ink`, `border-line`, `bg-primary` など）を使い、色コードを直接書かない。
- **推奨**: インライン `style` は計算が必要な値（サイズを props から決めるなど）に限る。
- **必須**: 装飾目的の要素には `aria-hidden` を付け、アイコンだけのボタンには `aria-label` を付ける。

## ログ

- サーバー側のログは `console.warn` / `console.error` に `[Backend]` や `[Discord OAuth]` のような接頭辞を付ける。
- **必須**: トークン、Cookie、セッションの中身をログに出さない。
