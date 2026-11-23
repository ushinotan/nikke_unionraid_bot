# QA 計画 — #94 [bot] GUILD_MEMBERS Intent を有効化しメンバー一覧 API を追加する

## タスク情報

| 項目 | 内容 |
|---|---|
| Issue | #94（親: #91 代理登録） |
| 種別 | story |
| ブランチ | `feature/94`（PR #131） |
| ベース | `origin/develop` |
| ブランチの位置 | +1 / -0（develop に対して遅れなし。リベース不要） |
| ステータス | `ready-for-qa` → `in-qa`（QA 開始時に遷移済み） |
| スコープ | **backend のみ**（`kotlin/`）+ 運用ドキュメント。`web/` の変更なし |

## 変更ファイル（一覧のみ。中身は読んでいない）

24 ファイル、+1774 / -4。

| 区分 | 層 | ファイル |
|---|---|---|
| backend | bot | `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/DiscordBot.kt` |
| backend | bot | `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/JdaGuildMemberSource.kt`（新規） |
| backend | controller | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/GuildMemberApiController.kt`（新規） |
| backend | dto | `kotlin/src/main/kotlin/gg/nikke/raid/bot/dto/ApiResponses.kt`、`dto/DtoMappers.kt` |
| backend | service | `service/GuildMember.kt`、`service/GuildMemberService.kt`、`service/GuildMemberSource.kt`（新規）、`service/RaidDomainError.kt` |
| backend | test | `test/.../bot/JdaGuildMemberSourceTest.kt`、`test/.../config/InternalApiTokenFilterIntegrationTest.kt`、`test/.../controller/GuildMemberApiControllerTest.kt`、`test/.../service/GuildMemberServiceTest.kt` |
| ドキュメント | 運用 | `DEPLOYMENT.md`、`README.md`、`kotlin/docs/API.md` |
| ドキュメント | CodeBase | `docs/codebase/backend/routes-map.md`、`docs/codebase/backend/services-map.md` |
| factory 成果物 | — | `docs/initiatives/91/{tech-lead,code-review-*,self-test,doc-sync-report}-94.md` |
| frontend | — | **なし** |

DB（`init.sql`・`migrations/`）の変更もなし。

---

## Backend — 結合テスト（実 HTTP、I-XX）

対象: `GET /api/guilds/{guildId}/members?query=&limit=`（`X-Internal-Api-Token` 必須）。
テストでは `discord.bot.enabled=false` で JDA が無いため、メンバーの供給元は差し替え（フェイク）で再現する。

| ID | シナリオ | メソッド | エンドポイント | 期待ステータス | 優先度 |
|----|---------|---------|---------------|---------------|-------|
| I-01 | 正常系: bot が参加しているギルドのメンバーを返す（`userId` は文字列、`displayName`・`avatarUrl` あり、`truncated` あり） | GET | /api/guilds/{guildId}/members | 200 | 🔴 |
| I-02 | bot ユーザー（自分自身の bot・system ユーザー含む）が結果に含まれない | GET | /api/guilds/{guildId}/members | 200 | 🔴 |
| I-03 | 表示名がサーバーニックネーム → グローバル名 → ユーザー名の順で決まる | GET | /api/guilds/{guildId}/members | 200 | 🔴 |
| I-04 | `?query=` で名前の部分一致に絞られる（大文字小文字を無視） | GET | /api/guilds/{guildId}/members?query=… | 200 | 🟡 |
| I-05 | `?query=` に一致するメンバーがいない → 空配列 | GET | /api/guilds/{guildId}/members?query=… | 200 | 🟡 |
| I-06 | 件数上限を超えると先頭だけ返り `truncated = true` | GET | /api/guilds/{guildId}/members?limit=… | 200 | 🟡 |
| I-07 | bot が参加していないギルド | GET | /api/guilds/999…/members | 404 | 🟡 |
| I-08 | bot 未起動・メンバー取得不能 | GET | /api/guilds/{guildId}/members | 503 | 🟡 |
| I-09 | 入力不正（`guildId` が数値でない、`limit` が範囲外、`query` が長すぎる） | GET | /api/guilds/abc/members ほか | 400（`BAD_REQUEST`） | 🟡 |
| I-10 | トークン無し・不一致 | GET | /api/guilds/{guildId}/members | 401 | 🔴 |

> ステータスとエラーコード（404 / 503 / 400 の条件、`limit` の範囲、`query` の上限）は tech-lead 計画 §3.4 の記載に基づく。
> blueprint には明記がないので、`qa-backend` が実装（`GuildMemberApiController.kt`、`kotlin/docs/API.md`）で確認すること。
> **認可の範囲**: backend にはユーザー文脈がなく、トークンがあればどのギルドのメンバーも取れる。「自分のギルドだけ見える」の確認は BFF 側の責務で、#91 の後続（web）タスクで扱う。本タスクではシナリオ化しない。

## Backend — 単体テスト（U-XX）

blueprint に純粋なロジック（除外・表示名の決定・部分一致）があるため、DB を使わない単体テストでも押さえる。

| ID | シナリオ | 対象 | 期待 | 優先度 |
|----|---------|------|------|-------|
| U-01 | 表示名の決定: ニックネーム → グローバル名 → ユーザー名（空・空白だけの値は次へ回す） | メンバー一覧のサービス | 期待どおりの `displayName` | 🔴 |
| U-02 | bot / system ユーザーの除外 | メンバー一覧のサービス | 結果に含まれない | 🔴 |
| U-03 | 部分一致検索: ニックネーム・グローバル名・ユーザー名のいずれかに一致、大文字小文字を無視 | メンバー一覧のサービス | 一致したものだけ返る | 🟡 |
| U-04 | 並び順が安定している（表示名順、同名は userId 順） | メンバー一覧のサービス | 順序が決まっている | 🟢 |
| U-05 | 上限ちょうどは `truncated = false`、超えたら `true` | メンバー一覧のサービス | 境界値どおり | 🟡 |
| U-06 | ギルドなし → 「ギルドが見つからない」エラー、取得不能 → 「取得できない」エラー | メンバー一覧のサービス | `Result` の失敗と種別 | 🟡 |
| U-07 | JDA 実装: 未接続（READY 前）は失敗、ギルドなしは null、メンバー未ロード時だけロードし 2 回目はロードしない、ロードのタイムアウトは失敗 | JDA のメンバー供給元 | 期待どおり | 🟡 |

## Frontend — E2E（E-XX）

**対象外。** このタスクは `web/` を一切変更しておらず、blueprint にも画面・ルートの記述がない。
BFF の API Route、`lib/api-types.ts`、E2E モック（`web/e2e/support/mock-backend.ts`）への追加は、#91 の後続（代理登録 UI）タスクで行う前提。
→ **frontend テスト（Playwright E2E / `web/__tests__`）は新規作成不要。** `qa-frontend` は起動しなくてよい。

## 回帰（G-XX）

共有部分（`DiscordBot` の起動設定、`/api` 配下を守るトークンフィルター）に触れているため、隣の機能が壊れていないことを確認する。

| ID | シナリオ | 期待 | 優先度 |
|----|---------|------|-------|
| G-01 | 既存の `/api` エンドポイント（例: `/api/guilds`、`/api/raids`）がトークンありで従来どおり 200、トークン無しで 401 | 既存挙動が変わらない | 🔴 |
| G-02 | `discord.bot.enabled=false`（テスト設定）でアプリケーションコンテキストが起動する | 既存テストスイート全体が通る | 🔴 |
| G-03 | backend の既存テスト全体（`./gradlew test`）が通る | 失敗 0 | 🔴 |

## 手動確認（M-XX、自動化不可）

本物の Discord と Developer Portal の設定が要るため自動化できない。人間が行う。

| ID | 確認内容 | 期待 | 優先度 |
|----|---------|------|-------|
| M-01 | Portal で Server Members Intent を ON にした状態で bot を起動する | 正常に起動する（完了条件 1） | 🔴 |
| M-02 | 起動した bot に対し、実ギルドで `GET /api/guilds/{実ギルドID}/members`（トークン付き）を呼ぶ | bot 以外のメンバーが表示名・アバター付きで返る | 🔴 |
| M-03 | （任意）Intent を OFF にして起動する | Portal 設定のヒント付きのエラーで起動が止まる | 🟢 |
| M-04 | `DEPLOYMENT.md` / `README.md` の Discord 設定手順を読む | Server Members Intent を ON にする手順と、100 サーバー超で審査が要る注意がある（完了条件 3） | 🟡 |

---

## 受け入れ条件 → シナリオ（トレーサビリティ）

| 完了条件 | シナリオ |
|---|---|
| 1. `GUILD_MEMBERS` Intent を有効にして bot が起動する | M-01、M-03（自動テストでは G-02 で起動設定がコンテキストを壊さないことのみ） |
| 2. メンバー一覧 API がサーバーのメンバーを返す | I-01〜I-10、U-01〜U-07、M-02 |
| 3. デプロイ手順のドキュメントが更新されている | M-04 |
| 4. テストが追加されている | I-XX / U-XX が backend テストとして存在し通ること（G-03） |

blueprint の「やること」との対応: 表示名の優先順位 → I-03 / U-01、bot 除外 → I-02 / U-02、`?query=` → I-04 / I-05 / U-03。

## 優先度別

| 優先度 | シナリオ |
|---|---|
| 🔴 | I-01、I-02、I-03、I-10、U-01、U-02、G-01、G-02、G-03、M-01、M-02 |
| 🟡 | I-04、I-05、I-06、I-07、I-08、I-09、U-03、U-05、U-06、U-07、M-04 |
| 🟢 | U-04、M-03 |

## 作成・更新するテストファイル

DEV の段階で以下のテストが既に追加されている（差分の一覧より）。`qa-backend` はまずこれらを読み、上記シナリオで足りないものだけを追記する。新しいファイルは原則作らない。

| シナリオ | ファイル（`tests.layout.backend` に従う） |
|---|---|
| I-01〜I-09 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/GuildMemberApiControllerTest.kt` |
| I-10、G-01 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/config/InternalApiTokenFilterIntegrationTest.kt` |
| U-01〜U-06 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/GuildMemberServiceTest.kt` |
| U-07 | `kotlin/src/test/kotlin/gg/nikke/raid/bot/bot/JdaGuildMemberSourceTest.kt` |
| G-02、G-03 | 既存スイート全体（新規ファイルなし） |
| frontend | なし |

実行コマンド: `set -a && . ./.env && set +a && cd kotlin && ./gradlew test --no-daemon`（PostgreSQL 起動が前提）。結果: `kotlin/build/test-results/test`。

## 合計

| 種別 | 件数 |
|---|---|
| Backend 結合（I） | 10 |
| Backend 単体（U） | 7 |
| Frontend E2E（E） | 0（対象外） |
| 回帰（G） | 3 |
| 手動（M） | 4 |
| **合計** | **24**（自動 20 / 手動 4） |

> **qa-backend / qa-frontend への注意:** シナリオは blueprint（と tech-lead 計画の API 仕様）から作った。実装するときは **実際のコードを読んで**、エンドポイント・パラメーター・ステータスコード・エラーコードを確認すること。
