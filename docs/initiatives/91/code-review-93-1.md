## Code Review — 93 — attempt 1 — 2026-11-23

### Verdict: ⚠️ APPROVED WITH NOTES

ブロックする指摘はない。計画（`tech-lead-93.md`）の 5 本のエンドポイント、`allowFinished`、厳密なリクエスト検証、例外ハンドラー、テストはすべて計画どおりに入っている。プローブ P1〜P6 は全部、想定どおりテストを落とした。下の 🟡 は次に触るとき直せばよい。

---

### 🔴 Critical

なし。

確かめた項目:
- 層の向き: コントローラーの書き込みはすべて `RaidDomainService` 経由（`RaidApiController.kt` のハンドラー 5 本）。リポジトリの書き込みメソッドは呼んでいない。コンストラクタに `RaidNotificationScheduler` も JDA も無いので、Embed は更新しない。
- トランザクションとロック: `registerParticipant`・`updateParticipantScore` に `@Transactional(rollbackFor = [Exception::class])` があり、最初に `lockRaid` を通る。`Result.failure` を返す分岐は、行を書き換える前にある（`insertParticipantIfAbsent` が 0 のときと `update` が 0 のときは何も書いていない）。`updateParticipantScore`（repository）は `requireActiveTransaction` でガードしている。
- `allowFinished`: コントローラーは名前付き引数 `allowFinished = true` で渡している。既定値は `false`。listener（`UnionRaidEventListener.kt`）は変えていない（diff はテストの匿名オーバーライドだけ）。
- 入力検証: `tools.jackson.databind.JsonNode?` で受けている。`score` は `isIntegralNumber && canConvertToInt() && >= 0`。`userId`（ボディ）は文字列だけで、`^[0-9]{1,20}$` かつ 1 以上の Long。`username` は制御文字を確かめてから trim し、1〜100 文字。`difficulty` は `fromValueOrNull`。未知のキーは 400。パスの `userId` は `requirePositiveUserId`、`difficulty` は `requireDifficulty`。
- 例外: `HttpMessageNotReadableException` → 400、`InvalidRequestException` → 400、`HttpMediaTypeNotSupportedException` → 415。ログはクラス名か固定メッセージだけで、ボディの値は出していない。ドメインエラーは `domainErrorResponse` で 404/409 に写し、想定外のエラーは `logger.error` + 汎用メッセージの 500。
- 認証: パスはすべて `/api/` の下。`InternalApiTokenFilterIntegrationTest` に、トークン無しの POST と DELETE が 401 になるテストを足してある。
- `init.sql`・`migrations/` は変えていない。秘密情報のハードコードは無い。コミットもまだしていない。

### 🟡 Important（ブロックしない）

1. **新しく足した DTO・マッパーの複数行プロパティに末尾カンマが無い**（`docs/conventions/backend.md` の「必須: 複数行に分けるときは末尾カンマ」）
   - `kotlin/src/main/kotlin/gg/nikke/raid/bot/dto/ApiResponses.kt:108`、`:116`、`:124`
   - `kotlin/src/main/kotlin/gg/nikke/raid/bot/dto/DtoMappers.kt:65`、`:73`、`:81`
   - `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainService.kt:303`（`insertParticipantIfAbsent(RaidParticipant(...))` の外側の閉じ括弧の前）
   - 同じファイルの既存コードも末尾カンマ無しなので、それに合わせたのだと思う。それでも新しく書く行は規約どおりにしてほしい。
2. **`username` が Unicode の改行・書式文字を通す**（`kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidWriteRequests.kt:103`）
   - `Char.isISOControl()` が止めるのは C0/C1 だけ。U+2028/U+2029（行区切り・段落区切り）、U+202E などの双方向制御、U+200B などのゼロ幅文字（カテゴリ Zl/Zp/Cf）は通る。ユーザー名は `RaidNotificationScheduler.kt:234-235`・`:332-335` で `"\n"` でつないで Embed の欄に入るので、計画にある「偽の行の差し込み」を、行区切り文字や見た目の入れ替えで一部すり抜けられるかもしれない。
   - 計画（§3.2）は `isISOControl` と決めているので、計画どおりではある。BFF は #94 のメンバー一覧の名前を使うことになっているから危険度は低い。直すなら `Character.getType(c)` が `LINE_SEPARATOR`・`PARAGRAPH_SEPARATOR`・`FORMAT` のものも拒否し、`RaidWriteRequestsTest` に `"a b"` と `"a‮b"` を足す。

### 🟢 Suggestions

- テスト名と中身が合っていない: `RaidApiControllerWriteTest.kt:219` の「`ボディの不正は400でありレイドが存在しなくても検証が先に効く`」は存在するレイド（`createRaid`）で試しているので、「レイドが存在しなくても」は確かめていない。名前を直すか、`Int.MAX_VALUE` のレイドに不正なボディを送って 400（404 ではない）になるケースを 1 つ足す。
- `RaidApiController.kt:279` の `logger.error("Unexpected failure: operation=$operation, raidId=$raidId", error)` は文字列テンプレートになっている。SLF4J のプレースホルダー（`{}`）にそろえるとよい。
- 新しいサービスメソッドの KDoc（`RaidDomainService.kt` の `registerParticipant`・`updateParticipantScore`）は「〜します。」になっている。規約で推奨されているのは「〜する。」（推奨なので任意）。
- `InvalidRequestException` が public なのは、public なハンドラーの引数型にするため（開発者メモの逸脱 (1)）。仕方ないし、問題はない。

### 📄 Documentation

doc-sync（D1）はまだ動いていない（`after_self_test` の順番どおり）。`kotlin/docs/API.md`、`docs/codebase/backend/routes-map.md`、`services-map.md`、`docs/conventions/backend.md` は、通常の doc-sync で計画 §2「ドキュメント」のとおりに更新すること。追加で再実行を頼む必要はない。

---

### プローブの結果（code-reviewer が再実行）

手順は `docs/conventions/testing.md` の「プローブの回し方」のとおり。作業ツリーをその場で書き換えてから元に戻した。`git stash` も、作業ツリーのコピーも使っていない。Gradle daemon を使った。

| ID | 一時的な変更 | 回したテスト | 結果 | 時間 |
|---|---|---|---|---|
| P1 | `@ExceptionHandler(HttpMessageNotReadableException::class)` を削除 | `RaidApiControllerWriteTest` | ✅ 落ちた: `壊れたJSONは400()`（15 件中 1 件失敗） | 12s |
| P2 | `score` の検査を `isNumber` に緩めた（`isIntegralNumber`・`canConvertToInt` を外した） | `RaidWriteRequestsTest` | ✅ 落ちた: `1.5`・`2147483648`・`1e3` の 3 件（23 件中 3 件失敗） | 5s |
| P3 | `registerParticipant` の `@Transactional` を削除 | `RaidDomainServiceTransactionTest` | ✅ 落ちた: `外側のトランザクションなし_プロキシ経由の参加登録とスコア更新がコミットされる()`（9 件中 1 件失敗） | 11s |
| P4 | コントローラーの `allowFinished = true` を 2 か所とも削除 | `RaidApiControllerWriteTest` | ✅ 落ちた: `終了済みレイドでも5本とも成功する()`（15 件中 1 件失敗） | 11s |
| P5 | `username` の制御文字の検査を無効にした | `RaidWriteRequestsTest` | ✅ 落ちた: `usernameに改行やタブなどの制御文字があれば400()`（23 件中 1 件失敗） | 5s |
| P6 | `JsonNode` の import を `com.fasterxml.jackson.databind.JsonNode` に変更（コントローラーと `RaidWriteRequests.kt` の両方） | `RaidApiControllerWriteTest` | ✅ コンパイルエラー（`propertyNames`・`isString`・`asString` が解決できない）。Jackson 2 版を間違えて使うと、ビルドの時点で止まる | 2s |

通るはずの確認:
- 基準（変更なし、`./gradlew test --rerun` で全件）: 258 件、失敗 0、スキップ 1。BUILD SUCCESSFUL。**21s**
- プローブのあとに `controller.*`・`RaidDomainService*`・`InternalApiTokenFilterIntegrationTest` を回し直して成功した（18s）。元に戻したあとのビルドが通ることも、これで確かめた。
- 既存の Discord 経由のテスト（`reportThreeTurn` の終了済みで `RaidAlreadyFinished`）は、基準の全件実行に含まれていて通った。

元に戻したことの確認: `git status --porcelain` は変更前と同じ。`git diff | sha256sum` は前後とも `97452cc2f17a1b8b12938e935e9d88bb90ad5b656d90f5d29288e0c1e4ae4207`。プローブ用に足したファイルは無い。

プローブにかかった時間: P1〜P6 の合計 **46s**（基準 21s と、戻したあとの確認 18s は含まない）。

---

### Summary

- **品質**: 計画への従い方がとてもよい。`JsonNode` で受けて 1 項目ずつ確かめる方式で、Jackson 3 の型変換が勝手に値を通してしまう抜け道（`1.5`・`"100"`・`1e3`）を確実に止めている。制御文字を trim の前の値で確かめるのは、計画にあった穴（前後の改行が trim で消えて素通りする）を開発者が見つけて直したもの。
- **計画との一致**: B1〜B5 はすべてそろっている。チェックリストの必須項目（層、トランザクションとロック、`allowFinished` の渡し方、`!!` 無し、ワイルドカード import の解消、テスト ID `70_093_xxx`、日本語のテスト名）は満たしている。外れているのは 🟡1 の末尾カンマだけ。
- **良い点**: 404 にするかどうか（参加者の行も報告も無い、取り消せた難易度が空）をコントローラーで判断していて、サービスは冪等のまま（#92 の決定どおり）。トランザクションのテストは、プロキシを通らない直接呼び出しで `IllegalStateException` になることと、行が変わらないこと（`score` が 0 のまま）まで確かめている。テストの検証がすべてサービス呼び出しより前に効くので、共有開発 DB に依存しない（#135 の教訓どおり）。
- **後続の reviewer へ**: この差分は API エンドポイントと入力検証を扱うので、`kotlin-reviewer` に加えて `security-reviewer` も回すこと。見てほしい点は、認可を BFF に任せる境界と、🟡2 の Unicode の書式文字。

## スタックレビュアーの結果（オーケストレーターが統合）

kotlin-reviewer・security-reviewer・database-reviewer を並列で実行した。critical / high の指摘はなく、判定は APPROVED_WITH_NOTES のまま。medium / low の指摘は 🟡 として最終レポートに載せる。

| # | ラベル | 重要度 | 出典 | 内容 |
|---|---|---|---|---|
| S1 | [SHOULD] | medium | kotlin | `registerParticipant` の同時実行（二重送信、3T 報告との競合）を、実 DB の並行テストで確かめていない（`RaidDomainServiceTransactionTest.kt`） |
| S2 | [SHOULD] | low〜medium | security（上の 🟡2 と同じ） | username の検査が Cf と U+2028/2029 を通すので、embed の表示を偽装できる（`RaidWriteRequests.kt:103`） |
| S3 | [SHOULD] | medium | kotlin | 「400 の検証は 404 の判定より先」という順序を API の仕様として書いていない（`RaidApiController.kt:106-126`）→ doc-sync で API.md に書く |
| S4 | [SHOULD] | medium | security | BFF 側の認可が前提になっていることを書き残す。コントローラーの KDoc（36-38 行目）には既に書いてあるので、API.md への記載を doc-sync で担保する |
| S5 | [IMO] | low | security | `handleTypeMismatch` がパス変数の値をそのままログに出している（`RaidApiController.kt:296`。既存のコード） |
| S6 | [IMO] | low | security | username が 100 文字で参加者が多いと、embed のフィールドが 1024 文字の上限を超えうる |
| S7 | [IMO] | low | kotlin | PATCH は後勝ち（last-write-wins）で、KDoc にその記載がない |
| S8 | [IMO] | low | kotlin | 最後の報告を取り消すと、スコアを入力済みの参加者も消える → BFF のフォローアップで扱う |
| S9 | [IMO] | low | kotlin | username の上限が API（100）・サービスの KDoc（255）・DB（255）で食い違っている |
| S10 | [IMO] | low | database | `score` に DB 側の CHECK (>= 0) 制約がない |
| S11 | [IMO] | low | database | 挿入・更新のあとに別クエリで読み直しているので、DB とのやり取りが 2 回になる |
| S12 | [IMO] | low | database | `insertOrUpdateParticipant` の KDoc に「読み取り専用 API では使用されません」という古い記述が残っている |
