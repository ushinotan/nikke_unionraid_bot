# Tech Lead 計画 — #109 書き込み系 API ルートでのサーバー側権限チェック

## エグゼクティブサマリー

| 項目 | 内容 |
|---|---|
| 種別 | story |
| 複雑度 | 未設定（実作業は S 相当） |
| ブランチ | `feature/109` |
| スコープ | frontend（`web/`）のみ。backend は対象外（下記） |
| 見積もり | 0.5〜1 日 |
| リスク | 低（既存の本番コードは変えない。テストとドキュメントの追加が中心） |
| ブロッカー | `depends_on` なし。ただし完了条件の一部は未マージの #96 / #104（#105）に依存（下記「アラート」） |

### 現状の調査結果（重要）
`origin/develop` ベースのこのブランチには、Issue の「対応内容」のうち共通部分が **すでに入っている**。

- `web/lib/raid-authorization.ts`: `authorizeRaidEdit` / `requireRaidEditPermission` / `RaidEditRequirement`（`privileged` / `participant`）。
  セッションの `userId` / `guildIds` / `guilds` と、**バックエンドから取り直したレイドの `guildId` / `finishedAt`** で判定し、401 / 400 / 403 / 404 を返す。
- `web/lib/permissions.ts`: `isPrivileged`、`canEditParticipant`（#96 で予定していたものも実装済み）。
- `web/__tests__/raid-authorization.test.ts`: 本人・特権・オーナー・一般・所属外ギルド、終了済みレイド、未ログイン、不正 raidId、404 を網羅。
- ドキュメント: `docs/codebase/overview.md`、`docs/codebase/frontend/routes-map.md`、`docs/conventions/frontend.md`（「書き込みを伴うルートは `requireRaidEditPermission` 必須」）、`web/README.md`。

一方で、**書き込みを行う API ルートはまだ 1 本も存在しない**。

- `web/app/api/**/route.ts` の POST は `api/auth/logout`（セッション破棄）と `api/session/selected-guild`（セッション内の選択ギルド変更、`guildIds` で検証済み）だけで、どちらもバックエンドへの書き込みを転送しない。
- backend（`kotlin/`）に `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` は無い（`docs/codebase/backend/routes-map.md`: 「書き込み系 API はまだ無い」）。

したがって、完了条件「権限の無いユーザーが編集 API を直接呼んでも 403」「API ルートのテスト（本人・特権・一般・所属外）」は、
**対象ルートが生まれる #96 / #104 の中でしか満たせない**。この Issue で今できるのは、
「後から追加される書き込みルートが必ずガードを通る」ことを機械的に保証する仕組みと、ルートテストを書くための共通部品の用意である。

---

## 1. Blueprint 要約（Issue 本文より）

- 書き込み系 API ルートは、バックエンドへ転送する前に `isPrivileged` / `canEditParticipant` で権限を確認し、無ければ 403。
- 判定にはクライアント由来の値ではなく、セッションの `userId` / `guilds` を使う。
- 画面のボタン非表示は補助。
- （任意）バックエンドでも操作者 ID を受けて検証する。

完了条件
1. 権限の無いユーザーが編集 API を直接呼んでも 403。
2. 本人・特権・一般・所属外ギルドのユーザーについて API ルートのテストを追加。

| 要件 | 現状 | この Issue での扱い |
|---|---|---|
| 転送前に権限確認・403 | 共通関数は実装済み。適用先ルートが無い | ガードテストで「書き込みルートは必ず呼ぶ」を強制（F1） |
| セッション値で判定 | `requireRaidEditPermission` が保証 | 変更なし |
| ボタン非表示は補助 | 規約・README に明記済み | 変更なし |
| バックエンドでも検証（任意） | 書き込み API が無い | 対象外。#105 / #96 に引き継ぎ（アラート） |
| 完了条件 1 | ルートが無い | #96 / #104 の完了条件として引き継ぐ（F1 が未ガードのルート追加を CI で落とす） |
| 完了条件 2 | 関数レベルのテストは済み | ルートテスト用の共通フィクスチャとパターンを用意（F2）。実ルートのテストは #96 / #104 で書く |

## 2. 影響マップ

### frontend（`web/`）
| 区分 | 名前 | 変更 |
|---|---|---|
| lib | `lib/raid-authorization.ts` | 変更なし（必要ならコメントに F1 / F2 への参照を 1 行追加する程度） |
| lib | `lib/permissions.ts` | 変更なし |
| API Route | `app/api/session/selected-guild/route.ts`、`app/api/auth/logout/route.ts` | 変更なし。F1 の許可リスト（セッションだけを書き換え、バックエンドに書き込まない）に登録 |
| proxy | `proxy.ts` | 変更なし。今後の書き込みルート（`/api/raids/{raidId}/...` 想定）は既存の `protectedApiPaths` で未ログイン 401 が効く |
| テスト | `__tests__/write-route-guard.test.ts` | 新規（F1） |
| テスト | `__tests__/helpers/raid-edit-fixtures.ts` | 新規（F2）。`npm test` の glob は `__tests__/**/*.test.ts` なので、`.test.ts` でないヘルパーはテストとして実行されない |
| テスト | `__tests__/raid-authorization.test.ts` | F2 のフィクスチャを使うようリファクタ（ケースは減らさない） |
| ドキュメント | `docs/codebase/frontend/routes-map.md`、`docs/codebase/frontend/services-map.md`（テスト一覧）、`docs/conventions/testing.md`、`docs/conventions/frontend.md` | F1 / F2 の存在と、ルートテストの書き方を追記（doc-sync） |

### backend（`kotlin/`）
変更なし。書き込みエンドポイント・エンティティ・マイグレーションは発生しない。

## 3. 技術方針とリスク

### F1: 書き込みルートのガードテスト（静的チェック）
- `node:fs` で `web/app/api/**/route.ts` を再帰的に列挙し、`export (async )?function (POST|PUT|PATCH|DELETE)` または
  `export const (POST|PUT|PATCH|DELETE)` を含むファイルを「書き込みルート」とする。
- 明示的な許可リスト（理由コメント付き）以外の書き込みルートは、ソースに `requireRaidEditPermission(` の呼び出しが含まれていなければ失敗。
  - 許可リスト: `app/api/auth/logout/route.ts`（セッション破棄のみ）、`app/api/session/selected-guild/route.ts`（セッション内の値のみ、`guildIds` で検証済み）。
  - 許可リストに載せたファイルが消えた／改名された場合も失敗させる（陳腐化防止）。
- 失敗メッセージに「`lib/raid-authorization.ts` の `requireRaidEditPermission` を通すか、バックエンドに書き込まない理由を許可リストに書く」と出す。
- パスは `import.meta.dirname` ではなく、`process.cwd()`（`npm test` は `web/` で実行）基準か、`fileURLToPath(new URL("../app/api", import.meta.url))` で解決する。tsx 実行で両方使えるが、既存テストに合わせて相対 URL 方式を推奨。
- 限界: 文字列の存在チェックなので「呼んだが結果を無視した」「呼ぶ前に転送した」は検出できない。そこは F2 のルートテストとコードレビューで担保する（チェックリストに明記）。

### F2: ルートテスト用の共通フィクスチャとパターン
- `__tests__/helpers/raid-edit-fixtures.ts` に、`raid-authorization.test.ts` 内のものを切り出す。
  - 定数: `GUILD_ID`、`OTHER_GUILD_ID`、`SELF`、`OTHER`
  - `raid(overrides)`（`RaidSummaryDto` を組み立てる）
  - 4 ペルソナ: `selfMember`（一般・本人）、`privilegedMember`（`permissions: "8"`）、`ownerMember`、`outsider`（別ギルドのオーナー）
  - `mockBackend(...)`: `global.fetch` を差し替え、`GET /api/raids/{id}` はレイドを返し、それ以外（書き込み）は呼ばれた URL・method・body を記録する。`restore()` を返す。
    「403 のときバックエンドへの書き込みが **呼ばれていない**」ことを検証できるようにするのが目的。
- ルートテストのパターン（#96 / #104 向けに `docs/conventions/testing.md` に記載）:
  - `getSession()` は `next/headers` の `cookies()` に依存し、`node:test` からルートハンドラーを直接呼ぶと動かない。ESM のモジュールモック（`--experimental-test-module-mocks`）は導入しない。
  - そのため、書き込みルートは「`route.ts` は `getSession()` と params / body の取り出しだけを行い、本体は `session: RaidEditSession` を引数に取る関数（`lib/` もしくは同じ機能の `lib/*-handlers.ts`）に委ねる」形にする。
    これは `docs/conventions/testing.md` の推奨（「Next.js の Request / Response に依存しない純粋関数として `lib/` に切り出す」）と同じ方針。
  - テストは 4 ペルソナ × 対象操作で「ステータス」と「書き込みがバックエンドに転送されたか」を両方確かめる。
- 型: 既存テストと同じく `RaidEditSession` / `RaidSummaryDto` を `import type`。`any` は使わない。

### 対象外とした項目
- backend での操作者検証（Issue で任意）: 書き込みエンドポイントが無いので今は設計できない。#105（順位・パーセンテージ API）/ #96（参加者・3T 報告 API）で、
  BFF から `actorUserId` を受け取るか等を決める（業務判断を含むため、ここでは決めない）。
- proxy の 401 レスポンスが `{ error: "Unauthorized" }`（規約の UPPER_SNAKE_CASE でない）点は本 Issue と無関係なので触らない（観察事項として記録）。

### セキュリティ観点
- 本番コードの挙動は変えないので回帰リスクは無い。
- F1 により「#96 / #104 で書き込みルートを追加したがガードを忘れた」ケースが CI（`npm test`）で落ちる。これが本 Issue の主目的（API 直叩きでの権限すり抜け防止）を将来にわたって担保する。
- パッケージ追加なし（`node:fs` / `node:path` / `node:url` のみ）。

## 4. サブタスク

| ID | 内容 | スタック | 作成・変更ファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| F1 | 書き込みルートのガードテスト | frontend | 新規 `web/__tests__/write-route-guard.test.ts` | なし | 2h |
| F2 | ルートテスト用フィクスチャの切り出しと既存テストの移行 | frontend | 新規 `web/__tests__/helpers/raid-edit-fixtures.ts`、変更 `web/__tests__/raid-authorization.test.ts` | なし | 2h |
| D1 | ドキュメント更新（doc-sync） | docs | `docs/codebase/frontend/routes-map.md`（「書き込み系を追加するとき」に F1 と許可リストの説明）、`docs/codebase/frontend/services-map.md`（テスト一覧に `write-route-guard`）、`docs/conventions/testing.md`（ルートテストのパターンとフィクスチャ）、`docs/conventions/frontend.md`（`route.ts` を薄くし本体を session 引数の関数に切り出す規約） | F1, F2 | 1h |

## 5. back / front の分担
- backend: **なし**
- frontend: F1、F2（並列可）
- D1 は F1 / F2 の後（`dev.doc_sync_order: after_self_test`）

## 6. 検証チェックリスト
- [ ] `cd web && npm test` が通る（既存 `raid-authorization.test.ts` のケース数が減っていない）
- [ ] `cd web && npm run lint && npm run build` が通る
- [ ] F1: 許可リストに無い書き込みルートを一時的に追加すると失敗する（手元で確認し、コミットはしない）
- [ ] F1: 許可リストのファイルが存在しない場合も失敗する
- [ ] F1: `GET` だけのルート（`guilds`、`guilds/[guildId]/raids`、`raids/[raidId]`）は対象にならない
- [ ] F2: ヘルパーは `.test.ts` で終わらず、`npm test` でテストとして実行されない
- [ ] F2: `mockBackend` は `afterEach` で `global.fetch` を必ず元に戻す
- [ ] 規約: `import type`、`any` 不使用、ダブルクォート・セミコロン、テスト名は日本語「条件なら結果」
- [ ] ドキュメント: 「書き込み系を追加するとき」に F1 の存在と、ルートテストで 4 ペルソナ + 「403 時に書き込みが転送されない」ことを確かめる旨がある

## 7. 引き継ぎ（#96 / #104 / #105 の完了条件に含めるべきこと）
- 各書き込みルートの入口で `requireRaidEditPermission` を呼び、`ok: false` ならその `response` を返す。転送時の raidId は `auth.raid.id`。
- レコード ID（participantId / reportId）で操作するルートは、バックエンドから取得したレコードの `userId` を `targetUserId` にする。
  そのためには backend の `RaidDetailResponse`（または個別取得 API）がレコードの持ち主を返す必要がある。
- ルートテスト: F2 のフィクスチャで 本人・特権・一般・所属外 の 4 パターン（参加者系は「一般が他人」「本人だが終了済み」も）を書き、403 のときバックエンドへの書き込みが呼ばれていないことを確かめる。
- backend 側で操作者 ID を受けて検証するかは #105 / #96 で決める。
