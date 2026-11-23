# QA 計画 — #93 [bot] 参加者・3T報告の登録・更新・削除APIを追加する

## タスク情報

| 項目 | 内容 |
|---|---|
| Issue | #93（親: #91 代理登録） |
| 種別 | story |
| ブランチ | `feature/93`（PR #137） |
| ベース | `origin/develop` |
| ブランチの位置 | +1 / -0（develop に対して遅れなし。リベース不要） |
| ステータス | `ready-for-qa` → `in-qa`（QA 開始時に遷移済み） |
| スコープ | **backend のみ**（`kotlin/`）。`web/` の変更なし、DB スキーマ（`init.sql`・`migrations/`）の変更なし |

## 変更ファイル（一覧のみ。中身は読んでいない）

22 ファイル、+1736 / -21。

| 区分 | 層 | ファイル |
|---|---|---|
| backend | controller | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt`、`controller/RaidWriteRequests.kt`（新規） |
| backend | dto | `dto/ApiResponses.kt`、`dto/DtoMappers.kt` |
| backend | service | `service/RaidDomainService.kt`、`service/RaidDomainError.kt`、`service/Difficulty.kt` |
| backend | repository | `repository/RaidParticipantRepository.kt` |
| backend | test | `test/.../controller/RaidApiControllerWriteTest.kt`（新規）、`test/.../controller/RaidWriteRequestsTest.kt`（新規）、`test/.../service/RaidDomainServiceTest.kt`、`test/.../service/RaidDomainServiceTransactionTest.kt`、`test/.../repository/RaidParticipantRepositoryIntegrationTest.kt`、`test/.../config/InternalApiTokenFilterIntegrationTest.kt`、`test/.../bot/UnionRaidEventListenerQaTest.kt` |
| ドキュメント | API / CodeBase | `kotlin/docs/API.md`、`docs/codebase/backend/{routes-map,services-map}.md` |
| factory 成果物 | — | `docs/initiatives/91/{tech-lead,code-review-*,self-test,doc-sync-report}-93.md` |
| frontend | — | **なし** |

---

## Backend — 結合テスト（実 HTTP + 実 DB、I-XX）

対象は blueprint の 5 エンドポイント。すべて `X-Internal-Api-Token` 付き、`Content-Type: application/json`。
ステータスは blueprint（400 / 404）と tech-lead 計画 §3.3・§3.4（201 / 409 / 415）による。`qa-backend` はコードで確かめること。

### POST `/api/raids/{raidId}/participants` — 参加登録

| ID | シナリオ | 期待ステータス | 期待 | 優先度 |
|----|---------|---------------|------|-------|
| I-01 | 未参加ユーザーを `userId`・`username`・`score` で登録 | 201 | 応答の参加者（`userId` は文字列）と DB の行が送った値どおり | 🔴 |
| I-02 | 既に参加者のユーザーを再登録 | 409 | 既存のスコア・ユーザー名は変わらない | 🔴 |
| I-03 | 存在しないレイド | 404 | 行は作られない | 🔴 |
| I-04 | 終了済みレイドに登録 | 201 | 登録できる（終了済みの判定は web 側） | 🔴 |
| I-05 | 3T 報告だけあり参加者の行が無いユーザーを登録 | 201 | 参加者が作られ、報告は残る | 🟡 |
| I-06 | 必須項目（`userId` / `username` / `score`）のどれかが無い | 400 | 行は作られない | 🔴 |
| I-07 | `score` が不正（`-1`、`1.5`、`"100"`、`2147483648`、`null`） | 400 | 行は作られない | 🔴 |
| I-08 | `score` が境界値（`0`、`2147483647`） | 201 | そのまま保存される | 🟡 |
| I-09 | `userId` が不正（数値型、`"abc"`、`"0"`、空文字） | 400 | — | 🟡 |
| I-10 | `username` が不正（空白だけ、上限超え、改行・タブ入り） | 400 | — | 🟡 |
| I-11 | 想定外のキーを含む（例: `difficulty`） | 400 | — | 🟢 |

### PATCH `/api/raids/{raidId}/participants/{userId}` — スコア更新

| ID | シナリオ | 期待ステータス | 期待 | 優先度 |
|----|---------|---------------|------|-------|
| I-12 | 参加者のスコアを更新 | 200 | 応答と DB のスコアが新しい値。`username`・参加時刻は変わらない | 🔴 |
| I-13 | 参加者でないユーザー | 404 | 行は作られない | 🔴 |
| I-14 | 存在しないレイド | 404 | — | 🟡 |
| I-15 | 終了済みレイドで更新 | 200 | 更新できる | 🟡 |
| I-16 | `score` が不正（I-07 と同じ値の代表例） | 400 | 既存スコアは変わらない | 🔴 |
| I-17 | 他のユーザー・他のレイドの同じユーザー | — | 対象以外のスコアは変わらない | 🟡 |

### DELETE `/api/raids/{raidId}/participants/{userId}` — 参加取り消し

| ID | シナリオ | 期待ステータス | 期待 | 優先度 |
|----|---------|---------------|------|-------|
| I-18 | 参加者かつ 3T 報告ありのユーザー | 200 | 参加者と全報告が消える。応答に削除の有無・件数 | 🔴 |
| I-19 | 参加者の行が無く報告だけあるユーザー | 200 | 報告が消える。応答は「参加者は消していない」 | 🟡 |
| I-20 | 参加者でも報告者でもないユーザー | 404 | — | 🔴 |
| I-21 | 存在しないレイド | 404 | — | 🟡 |
| I-22 | 終了済みレイドで取り消し | 200 | 取り消せる | 🟡 |
| I-23 | 他のユーザー・他のレイド | — | 対象以外の参加者・報告は残る | 🔴 |

### POST `/api/raids/{raidId}/reports` — 3T 報告

| ID | シナリオ | 期待ステータス | 期待 | 優先度 |
|----|---------|---------------|------|-------|
| I-24 | 未参加ユーザーが報告 | 200 | 報告が保存され、参加者が自動で作られる（#92 の連動。応答に「自動登録した」） | 🔴 |
| I-25 | 参加者（スコア入力済み）が報告 | 200 | 参加者の `username`・スコアは上書きされない | 🔴 |
| I-26 | 同じ難易度を再報告 | 200 | 報告は 1 行のまま。応答に「報告済みだった」 | 🟡 |
| I-27 | 終了済みレイドに報告 | 200 | 報告と自動参加ができる | 🔴 |
| I-28 | 存在しないレイド | 404 | 報告・参加者は作られない | 🔴 |
| I-29 | `difficulty` が不正（`"HARD"`、`"extreme"`、数値） | 400 | — | 🔴 |
| I-30 | 必須項目（`userId` / `username` / `difficulty`）のどれかが無い | 400 | — | 🟡 |

### DELETE `/api/raids/{raidId}/reports/{userId}/{difficulty}` — 3T 報告の取り消し

| ID | シナリオ | 期待ステータス | 期待 | 優先度 |
|----|---------|---------------|------|-------|
| I-31 | 唯一の報告を取り消す | 200 | 報告と参加者の両方が消える（応答に「参加者から外れた」） | 🔴 |
| I-32 | 2 難易度のうち 1 つを取り消す | 200 | 残りの報告と参加者は残る | 🔴 |
| I-33 | 報告していない難易度を取り消す | 404 | 何も消えない | 🔴 |
| I-34 | パスの `difficulty` が不正（`extreme`） | 400 | 500 にならない | 🔴 |
| I-35 | 終了済みレイドで取り消し | 200 | 取り消せる | 🔴 |
| I-36 | 存在しないレイド | 404 | — | 🟡 |

### 共通（入力形式・認証）

| ID | シナリオ | メソッド / エンドポイント | 期待ステータス | 優先度 |
|----|---------|------------------------|---------------|-------|
| I-37 | 壊れた JSON | POST participants / reports、PATCH | 400（500 にならない） | 🔴 |
| I-38 | ボディが空 | POST participants / reports、PATCH | 400（500 にならない） | 🔴 |
| I-39 | `Content-Type: text/plain` | POST participants など | 415 | 🟡 |
| I-40 | パスの `raidId=abc`、パスの `userId=abc` / `0` | 5 本の代表 | 400 | 🟡 |
| I-41 | トークン無し | POST reports、DELETE participants（書き込みの代表） | 401 | 🔴 |
| I-42 | 誤ったトークン | 書き込みの代表 1 本 | 401 | 🟡 |
| I-43 | 400 の応答に送った値（`username` など）が含まれない | 不正ボディの代表 | 400、メッセージは項目名だけ | 🟢 |

### サービス・リポジトリ（実 DB、トランザクション）

| ID | シナリオ | 期待 | 優先度 |
|----|---------|------|-------|
| I-44 | 外側のトランザクションなしで、プロキシ経由の参加登録・スコア更新 | 実際にコミットされる | 🔴 |
| I-45 | トランザクションの外から参加登録・スコア更新を直接呼ぶ | 書き込み前に止まり、行が増減しない | 🟡 |
| I-46 | 参加者の 1 件取得: あれば返る。他ユーザー・他レイドは返らない。無ければ null | 期待どおり | 🟡 |
| I-47 | スコア更新: 対象だけ変わり 1 件。参加者でなければ 0 件 | 期待どおり | 🟡 |

## Backend — 単体テスト（U-XX）

リクエストの検証（Spring・DB なしで切り出せる純粋なロジック）。HTTP 側の I-06〜I-11・I-29・I-30 は代表例にとどめ、網羅はここで行う。

| ID | シナリオ | 期待 | 優先度 |
|----|---------|------|-------|
| U-01 | 3 種類の正しいボディ（参加登録・スコア更新・3T 報告） | 読み取れる。`username` の前後の空白は除かれる | 🔴 |
| U-02 | ボディが null・配列・文字列、想定外のキー、必須項目の欠落 | 検証エラー（400 相当） | 🔴 |
| U-03 | `userId`: 数値型、空文字、`"abc"`、`"0"`、`"-1"`、21 桁 | 検証エラー | 🟡 |
| U-04 | `username`: 空白だけ、上限 +1 文字、改行入り、タブ入り、数値型 | 検証エラー。上限ちょうどは通る | 🟡 |
| U-05 | `score`: `-1`、`1.5`、`"100"`、`2147483648`、`null`、`true` は検証エラー。`0` と `2147483647` は通る | 期待どおり | 🔴 |
| U-06 | `difficulty`: `normal` / `hard` は通る。`"HARD"`、`"extreme"`、数値は検証エラー | 期待どおり | 🟡 |
| U-07 | 難易度の文字列変換（例外を投げない版）: 不明な値で null を返す | 期待どおり | 🟢 |

## Frontend — E2E（E-XX）

**対象外。** このタスクは `web/` を一切変更しておらず、blueprint にも画面・ルートの記述がない（「権限チェックは web 側の API Route で行う」とあるが、その API Route と画面は #91 の後続タスクで作る）。
→ **frontend テスト（Playwright E2E `web/e2e/*.spec.ts` / `web/__tests__`）は新規作成不要。** `qa-frontend` は起動しなくてよい。
E2E のモックバックエンド（`web/e2e/support/mock-backend.ts`）へのこの API の追加も、後続の web タスクで行う。

## 回帰（G-XX）

共有部分（`RaidDomainService` の 3T 報告・取り消しに終了済みを許すフラグを追加、`RaidApiController` の例外ハンドラー追加）に触れているため、隣の機能を確認する。

| ID | シナリオ | 期待 | 優先度 |
|----|---------|------|-------|
| G-01 | Discord ボタン経由の 3T 報告・取り消しは、終了済みレイドでは従来どおり失敗する | 終了済みエラー。報告・参加者は作られない | 🔴 |
| G-02 | 既存の読み取り API（`GET /api/raids/...`）のテスト | 形・ステータスが変わらない。書き込み後の `GET /api/raids/{raidId}` に結果が反映される | 🟡 |
| G-03 | 既存の認証フィルターのテスト（`/api/` 以外は素通し、読み取りは 401 など） | 変わらない | 🟡 |
| G-04 | Discord 経由の連動（#92 の QA テスト `UnionRaidEventListenerQaTest`） | 差分で変更されているため、通ること | 🟡 |
| G-05 | backend の既存テスト全体（`./gradlew test`） | 失敗 0。トランザクションテストの後にテスト用ギルド・レイドが DB に残らない | 🔴 |

## 手動確認（M-XX、自動化不可）

| ID | 確認内容 | 期待 | 優先度 |
|----|---------|------|-------|
| M-01 | compose の bot に `curl`（トークン付き）で 5 本を叩く | Discord の通知メッセージ（Embed）が更新されない（blueprint の要件。テスト環境では JDA が無いため自動化不可） | 🟡 |
| M-02 | M-01 の後、Discord のボタンで誰かが報告する | Embed が DB の内容から作り直され、Web からの変更も反映される（仕様上の副作用） | 🟢 |

---

## 受け入れ条件 → シナリオ（トレーサビリティ）

| 完了条件 | シナリオ |
|---|---|
| 1. 上記エンドポイントが実装されている | I-01、I-12、I-18、I-24、I-31（各エンドポイントの正常系）、I-02〜I-05、I-15、I-19、I-22、I-25〜I-27、I-32、I-35 |
| 2. 不正な入力は 400、存在しないリソースは 404 を返す | 400: I-06〜I-11、I-16、I-29、I-30、I-34、I-37、I-38、I-40、U-01〜U-07。404: I-03、I-13、I-14、I-20、I-21、I-28、I-33、I-36 |
| 3. Controller / Service のテストが追加されている | I-XX（controller・service・repository）と U-XX がテストとして存在し通ること（G-05） |

blueprint の「やること」との対応:
- スコアは 0 以上の整数（INTEGER の範囲内） → I-07、I-08、I-16、U-05
- `difficulty` は `normal` / `hard` → I-29、I-34、U-06、U-07
- 3T 報告で未参加なら自動参加 → I-24、I-25
- 参加取り消しで 3T 報告も削除 → I-18、I-19、I-23
- 3T 報告の取り消しで 0 件なら参加者も削除 → I-31、I-32
- 認証は `X-Internal-Api-Token` → I-41、I-42、G-03
- Web からの操作では Embed を更新しない → M-01（コード上はコントローラーが通知処理を依存に持たないことを code-review で確認済みの想定）
- 終了済みの判定は web 側（bot は終了済みでも書き込める） → I-04、I-15、I-22、I-27、I-35。Discord 側は変わらない → G-01

## 優先度別

| 優先度 | シナリオ |
|---|---|
| 🔴 | I-01〜I-04、I-06、I-07、I-12、I-13、I-16、I-18、I-20、I-23〜I-25、I-27〜I-29、I-31〜I-35、I-37、I-38、I-41、I-44、U-01、U-02、U-05、G-01、G-05 |
| 🟡 | I-05、I-08〜I-10、I-14、I-15、I-17、I-19、I-21、I-22、I-26、I-30、I-36、I-39、I-40、I-42、I-45〜I-47、U-03、U-04、U-06、G-02〜G-04、M-01 |
| 🟢 | I-11、I-43、U-07、M-02 |

## 作成・更新するテストファイル

DEV の段階で以下のテストが既に追加されている（差分の一覧より）。`qa-backend` はまずこれらを読んで各シナリオに対応づけ、**足りないものだけを追記する**。新しいファイルは原則作らない。

| シナリオ | ファイル（`tests.layout.backend` に従う） |
|---|---|
| I-01〜I-40、I-43、G-02 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/RaidApiControllerWriteTest.kt` |
| I-41、I-42、G-03 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/config/InternalApiTokenFilterIntegrationTest.kt` |
| I-44、I-45 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/RaidDomainServiceTransactionTest.kt` |
| サービス層の I-01〜I-05・I-12〜I-15・I-24〜I-27・I-31〜I-35 相当、G-01 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/RaidDomainServiceTest.kt` |
| I-46、I-47 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/repository/RaidParticipantRepositoryIntegrationTest.kt` |
| U-01〜U-07 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/RaidWriteRequestsTest.kt`（U-07 は実装を見て `service/` 側のテストでもよい） |
| G-04 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/bot/UnionRaidEventListenerQaTest.kt` |
| frontend | なし |

`qa-backend` への補足（tech-lead 計画 §4 のテスト指針と見比べて、抜けている可能性があるもの）:
- I-17、I-23: 「対象以外は変わらない」の確認がコントローラーテストに無ければ足す。
- I-42: 誤ったトークン（無しではなく値違い）の書き込みメソッドでの 401。
- I-43: 400 の応答・ログに送った値を出さないこと。応答本文だけ確認すればよい（ログの確認は任意）。
- I-08: `score` の境界値（0・2147483647）が HTTP 経由でも保存されること。
- I-26: 同じ難易度の再報告の HTTP 応答（`alreadyReported` 相当）。

実行コマンド: `set -a && . ./.env && set +a && cd kotlin && ./gradlew test`（PostgreSQL 起動・`init.sql` 適用が前提）。結果: `kotlin/build/test-results/test`。

## 合計

| 種別 | 件数 |
|---|---|
| Backend 結合（I） | 47 |
| Backend 単体（U） | 7 |
| Frontend E2E（E） | 0（対象外: `web/` の変更なし） |
| 回帰（G） | 5 |
| 手動（M） | 2 |
| **合計** | **61**（自動 59 / 手動 2） |

> **qa-backend / qa-frontend への注意:** シナリオは blueprint（と tech-lead 計画）から作った。実装するときは **実際のコードを読んで**、エンドポイント、パラメーター、ステータス、エラーコード、応答のフィールド名を確認すること。
