# Tech Lead 計画 — #93 Web から参加者・3T 報告を操作する内部 API

## エグゼクティブサマリー

| 項目 | 内容 |
|---|---|
| 種別 | story（親: #91 代理登録） |
| 複雑度 | 指定なし（見立て: M） |
| ブランチ | `feature/93` |
| スコープ | **backend のみ**（`kotlin/`）。web（BFF の API Route・画面）は #91 の後続タスクで作る |
| 影響 | `controller/RaidApiController.kt`（書き込みエンドポイント 5 本と 400/404/409/415 のハンドラー）、新規 `controller/RaidWriteRequests.kt`（リクエストの厳密な検証）、`service/RaidDomainService.kt`（`registerParticipant`・`updateParticipantScore` を追加し、`reportThreeTurn`・`cancelThreeTurnReports` に `allowFinished` を追加）、`service/RaidDomainError.kt`、`service/Difficulty.kt`、`repository/RaidParticipantRepository.kt`、`dto/ApiResponses.kt` |
| スキーマ変更 | **なし**（`init.sql`・`migrations/` は触らない） |
| 見積もり | 実装 0.5 日、テスト 0.75 日、ドキュメント（doc-sync）0.25 日 |
| リスク | 中。(1) Spring Boot 4 の MVC は **Jackson 3（`tools.jackson`）** で動いているのに、classpath には Jackson 2 の `jackson-module-kotlin` しかない。リクエストボディを Kotlin の data class で受けると、変換の挙動が読めない（§3.2）。(2) コントローラーの `@ExceptionHandler(Exception::class)` が JSON の読み取りエラーを 500 にしてしまう（§3.3）。(3) 終了済みレイドを Web から操作できるように、ドメインの終了判定を Web 経由のときだけ外す必要がある（§3.1） |
| ブロッカー | なし（`depends_on` の #92 は develop にマージ済み） |
| 受け入れ条件の絞り込み | **しない**（3 項目ともこのタスクで満たす） |

---

## 1. Blueprint の要約（PO の要求）

Web から参加者と 3T 報告を操作できるように、bot に内部 API を追加する。権限チェック（本人か、特権ユーザーか、終了済みレイドか）は web の API Route が行う。bot は、操作対象のデータが正しいかどうかの検証と、#92 で作った連動処理を受け持つ。

| メソッド | パス | 内容 |
|---|---|---|
| POST | `/api/raids/{raidId}/participants` | 参加登録（`userId`、`username`、`score`） |
| PATCH | `/api/raids/{raidId}/participants/{userId}` | スコアの更新 |
| DELETE | `/api/raids/{raidId}/participants/{userId}` | 参加の取り消し（3T 報告も削除する） |
| POST | `/api/raids/{raidId}/reports` | 3T 報告（`userId`、`username`、`difficulty`）。参加していなければ自動で参加者にする |
| DELETE | `/api/raids/{raidId}/reports/{userId}/{difficulty}` | 3T 報告の取り消し。報告が 0 件になったら参加者も削除する |

- バリデーション: スコアは 0 以上の整数で INTEGER の範囲内。`difficulty` は `normal` か `hard`。存在しないレイド・参加者は 404。
- 認証は既存の `X-Internal-Api-Token`（`InternalApiTokenFilter`）。
- Web からの操作では、Discord の通知メッセージ（Embed）を更新しない。

完了条件: (1) 上記エンドポイントの実装、(2) 不正な入力は 400、存在しないリソースは 404、(3) Controller と Service のテスト。

親 #91 の前提: `web/lib/permissions.ts` の `checkParticipantEdit` は、**特権ユーザーなら終了済みのレイドでも操作できる**（本人は未終了のレイドだけ）。したがって bot の API は、終了済みのレイドでも書き込めなければならない（§3.1）。

---

## 2. 影響マップ（CodeBase 由来の実名）

パッケージのルートは `kotlin/src/main/kotlin/gg/nikke/raid/bot/`。

### backend

| 層 | ファイル | 変更 |
|---|---|---|
| controller | `controller/RaidApiController.kt` | コンストラクタに `RaidDomainService` を追加。エンドポイント 5 本（§3.4）。例外ハンドラー `HttpMessageNotReadableException` → 400、`HttpMediaTypeNotSupportedException` → 415、`InvalidRequestException` → 400 を追加（§3.3）。クラスの KDoc から「読み取り専用」を外す。`import gg.nikke.raid.bot.dto.*` は触るついでに個別の import に直す（規約: ワイルドカード禁止） |
| controller | **新規** `controller/RaidWriteRequests.kt` | `JsonNode` からリクエストを厳密に読み取る関数と、読み取り後の値の型（`RegisterParticipantRequest` など）、`internal class InvalidRequestException`（§3.2）。Spring に依存しないので単体テストできる |
| service | `service/RaidDomainService.kt` | **新規** `registerParticipant`、`updateParticipantScore`（どちらも `@Transactional(rollbackFor = [Exception::class])` で、`lockRaid` を通す）。`reportThreeTurn` と `cancelThreeTurnReports` に `allowFinished: Boolean = false` を追加（§3.1） |
| service | `service/RaidDomainError.kt` | `ParticipantNotFound`、`ParticipantAlreadyExists`、`ThreeTurnReportNotFound` を追加 |
| service | `service/Difficulty.kt` | `fromValueOrNull(value: String): Difficulty?` を追加（既存の `fromValue` は一致しないと `NoSuchElementException` になり 500 になるため） |
| repository | `repository/RaidParticipantRepository.kt` | **追加** `findParticipantByRaidIdAndUserId(raidId, userId): RaidParticipant?`、`updateParticipantScore(raidId, userId, score): Long`（`requireActiveTransaction` でガードする。戻り値は更新した行数） |
| dto | `dto/ApiResponses.kt` | `ThreeTurnReportResponse`、`ThreeTurnCancelResponse`、`ParticipationCancelResponse`（§3.4）。参加者は既存の `ParticipantDto` を返す |
| dto | `dto/DtoMappers.kt` | 結果型 → DTO の変換（`ThreeTurnReportResult.toDto()` など） |
| config | `config/InternalApiTokenFilter.kt` | **変更なし**（`/api/` 配下なので保護される） |
| bot | `bot/UnionRaidEventListener.kt` | **変更なし**（`allowFinished` は既定値の `false` のまま。Discord からは今までどおり終了済みレイドを操作できない） |
| 永続化 | `init.sql`、`migrations/` | **変更なし**。`raid_participants.score` は `INTEGER`、`username` は `VARCHAR(255)`。一意制約 `UNIQUE(raid_id, user_id)` は既存 |

依存の向きは `docs/conventions/backend.md` のとおり（`controller → service → repository`）。**書き込みは必ず `RaidDomainService` を通す**。コントローラーからリポジトリの書き込みメソッドを呼ばない（#92 で決めたロックの約束: 参加者・報告への書き込みは、`RaidDomainService` でレイド行を `FOR UPDATE` でロックしてから行う）。読み取り（`GET`）は今までどおりリポジトリを直接呼んでよい。

### frontend
変更なし。BFF の API Route（`checkParticipantEdit` で権限を確かめてから、この API を呼ぶ）、`lib/api-types.ts` の型、E2E のモック（`web/e2e/support/mock-backend.ts`）は、#91 の後続タスクで追加する（alerts に記載）。

### ドキュメント（doc-sync が担当）

| ファイル | 内容 |
|---|---|
| `kotlin/docs/API.md` | 見出しを「レイド読み取り API」から内部 API 全体に変える。エンドポイント 5〜9 を追加（§3.4 の仕様をそのまま写す: パス、ボディ、レスポンス例、エラー 400/404/409/415）。「`userId` はリクエストでも文字列」と、「この API は権限を確かめない（BFF が確かめる）」「終了済みのレイドも操作できる」「Discord の Embed は更新しない」を書く |
| `docs/codebase/backend/routes-map.md` | ルート表に 5 行追加。「書き込み系 API はまだ無い」の行を直す |
| `docs/codebase/backend/services-map.md` | `registerParticipant`・`updateParticipantScore`・`allowFinished`、新しいエラー、リポジトリの新メソッドを追記。「#91 Web 側への引き継ぎ」に、BFF が確かめること（§3.6）を追記 |
| `docs/conventions/backend.md` | 「読み取りだけのコントローラーはリポジトリを直接呼んでよい（`RaidApiController` の現状）」を「読み取りのハンドラーは…。書き込みは `service` 経由」に直す |

---

## 3. 技術方針

### 3.1 終了済みレイドの扱い（`allowFinished`）

- 今の `reportThreeTurn` と `cancelThreeTurnReports` は `lockActiveRaid` を通すので、終了済みのレイドでは `RaidAlreadyFinished` になる。Discord のボタンはこれで正しい。
- Web からは、特権ユーザーが終了済みのレイドを直せる必要がある（`checkParticipantEdit`）。issue でも「終了済みかどうかのチェックは web 側」と決まっている。
- そこで 2 つのメソッドに `allowFinished: Boolean = false` を足す。`true` なら `lockRaid`（終了判定なし）、`false` なら今までどおり `lockActiveRaid`。
  ```kotlin
  val lock = if (allowFinished) lockRaid(raidId) else lockActiveRaid(raidId)
  lock.onFailure { return Result.failure(it) }
  ```
- **コントローラーからは必ず `allowFinished = true` を名前付き引数で渡す**。listener は渡さない（既定の `false`）。KDoc に「`true` にするのは、終了済みレイドを操作してよいかを呼び出し側（BFF の権限判定）が確かめた場合だけ」と書く。
- 新しく作る `registerParticipant` と `updateParticipantScore` は、既存の `cancelParticipation` と同じく終了判定をしない（`lockRaid`）。Discord から呼ぶ予定は無いので、フラグは付けない。
- 退けた案: 「Web 用のメソッドを別に作る」→ 連動処理が 2 か所に分かれ、#92 の「同じ処理を通す」に反する。「`lockActiveRaid` をやめる」→ Discord の挙動が変わる。

### 3.2 リクエストボディの読み取り（Jackson 3 と厳密な検証）

**前提として確かめたこと**: Spring Boot 4.0.6 の MVC は Jackson 3（`tools.jackson.core:jackson-databind:3.1.2`）でメッセージを変換している。`build.gradle.kts` にある `com.fasterxml.jackson.module:jackson-module-kotlin` は Jackson 2 用で、MVC からは使われない（今まで本体にリクエストボディを受けるコードが無かったので、問題にならなかった）。Jackson 3 用の Kotlin モジュール（`tools.jackson.module:jackson-module-kotlin`）は入っていない。

Kotlin の data class でボディを受けると、次のことがはっきりしない・危ない:
- Kotlin モジュールが無いので、コンストラクタ引数の名前を解決できるか（`-java-parameters` が要るか）が分からない。必須項目が無いときに Kotlin の null チェックが `NullPointerException` を投げ、どんなエラーになるかも読めない。
- `score: Int` で受けると、Jackson の既定の型変換で `1.5` が `1` に、`"100"` が `100` になって通ってしまう（「整数」の検証をすり抜ける）。

**採用: `@RequestBody(required = false) body: tools.jackson.databind.JsonNode?` で受け、`RaidWriteRequests.kt` の関数で 1 項目ずつ型を確かめる。**
- 依存の追加も、`spring.jackson.*` の全体設定の変更も要らない。型の検査は自分で書くので、すり抜けない（fail-closed）。
- **import は必ず `tools.jackson.databind.JsonNode`**。`com.fasterxml.jackson.databind.JsonNode`（Jackson 2。`jackson-module-kotlin` 経由で classpath にある）を間違って使うと、Jackson 3 のコンバーターが値を作れず、正しいリクエストまで 415 / 400 になる（プローブ P6 で確かめる）。
- Jackson 3 では一部のメソッド名が Jackson 2 から変わっている（例: `isTextual()` → `isString()`、`asText()` → `asString()`、`fieldNames()` → `propertyNames()`）。名前はコンパイルで確かめること。
- 退けた案: `tools.jackson.module:jackson-module-kotlin` を足して data class で受ける → 依存が増え、それでも `1.5` / `"100"` の型変換を全体設定で止める必要がある。

検証のルール（すべて満たさなければ `InvalidRequestException(message)` を投げて 400）:

| 項目 | ルール | 理由 |
|---|---|---|
| ボディ全体 | `null` でなく、JSON オブジェクトであること。決まった項目以外のキーがあれば 400 | クライアントの取り違え（参加登録に `difficulty` を送るなど）を黙って通さない |
| `userId`（ボディ） | **JSON の文字列**で、`^[0-9]{1,20}$` に一致し、`toLongOrNull()` が 1 以上 | Snowflake は文字列で受け渡す規約（`kotlin/docs/API.md`「Snowflake ID の取り扱い」）。数値だと JavaScript で精度が落ちる |
| `userId`（パス） | `Long` で受ける。型が合わなければ既存のハンドラーで 400。1 以上でなければ 400 | |
| `username` | JSON の文字列。前後の空白を除いたうえで空でなく、100 文字以内。**制御文字（`Char.isISOControl()`、改行を含む）を含んだら 400**。保存するのは空白を除いた値 | DB は `VARCHAR(255)`（超えると SQL エラーで 500 になる）。Discord の名前は 32 文字まで。改行を許すと、通知 Embed の「ノーマル 3凸」欄（名前を改行でつないでいる）に偽の行を差し込める |
| `score` | JSON の整数（`isIntegralNumber`）で、`canConvertToInt()` が true、かつ 0 以上。`1.5`・`"100"`・`-1`・`2147483648`・`null` は 400 | DB は `INTEGER`。issue のルールどおり |
| `difficulty`（ボディ・パス） | `Difficulty.fromValueOrNull(value)` が null でないこと（`normal` / `hard`。大文字小文字は区別する） | `fromValue` は一致しないと `NoSuchElementException` で 500 になる |
| `raidId`（パス） | `Int`。型が合わなければ既存のハンドラーで 400。存在しなければサービスが `RaidNotFound` → 404 | |

- 項目ごとの必須: 参加登録は `userId`・`username`・`score`。スコア更新は `score` だけ。3T 報告は `userId`・`username`・`difficulty`。
- エラーメッセージは英語の固定文（例: `"score must be an integer between 0 and 2147483647"`）。**ボディの値（名前など）はメッセージにもログにも入れない**（項目名だけ）。
- 定数は `RaidWriteRequests.kt` の `private const val`（`MAX_USERNAME_LENGTH = 100` など）か、`object` の中にまとめる。

### 3.3 例外ハンドラー（今のままだと読み取りエラーが 500 になる）

`RaidApiController` には `@ExceptionHandler(Exception::class)`（500）がある。コントローラーの `@ExceptionHandler` は、引数の解決中（`@RequestBody` の読み取り）に出た例外も受けるので、何もしないと**壊れた JSON・空のボディ・Content-Type の違いがすべて 500** になる。次を足す:

| 例外 | ステータス | `error` |
|---|---|---|
| `HttpMessageNotReadableException`（壊れた JSON など） | 400 | `BAD_REQUEST` |
| `InvalidRequestException`（§3.2） | 400 | `BAD_REQUEST` |
| `HttpMediaTypeNotSupportedException`（`Content-Type` が JSON でない） | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| `MethodArgumentTypeMismatchException`（既存） | 400 | `BAD_REQUEST` |

- ログは `logger.warn` で、例外のクラス名と項目名だけを出す。`e.message` はボディの一部を含むことがあるので出さない。
- `RaidDomainError` は `Result` で返ってくるので、ハンドラーではなく各ハンドラーメソッドの `fold` / `when` でステータスに写す（`GuildMemberApiController` と同じ形）。網羅しない分岐（`RaidAlreadyFinished` など、起きないはずのもの）は `logger.error` を出して 500。
- `ErrorResponse` を作る private ヘルパー（`errorResponse(status, error, message)`）は `GuildMemberApiController` と同じ形でよい。共通化はこのタスクではしない。

### 3.4 API 仕様

すべて `X-Internal-Api-Token` が必要。リクエストは `Content-Type: application/json`。

**POST `/api/raids/{raidId}/participants`** — 参加登録
- ボディ: `{ "userId": "300000001", "username": "ニック", "score": 12345 }`
- 201: `ParticipantDto`（`id`、`userId`（文字列）、`username`、`score`、`joinedAt`）
- 404: レイドが無い（`RaidNotFound`）。409 `CONFLICT`: 既に参加者（`ParticipantAlreadyExists`）
- サービス: `registerParticipant(raidId, userId, username, score, now)`

**PATCH `/api/raids/{raidId}/participants/{userId}`** — スコア更新
- ボディ: `{ "score": 23456 }`
- 200: 更新後の `ParticipantDto`
- 404: レイドが無い、または参加者でない（`ParticipantNotFound`）
- サービス: `updateParticipantScore(raidId, userId, score)`。`username` と `joined_at` は変えない

**DELETE `/api/raids/{raidId}/participants/{userId}`** — 参加の取り消し
- 200: `{ "participantRemoved": true, "removedReportCount": 2 }`（`ParticipationCancelResponse`）
- 404: レイドが無い、または**参加者の行も報告も無い**（`participantRemoved == false && removedReportCount == 0L` のとき `ParticipantNotFound`）
- 参加者の行が無く報告だけある（#92 より前のデータ）場合は、報告を消して 200（`participantRemoved: false`）
- サービス: 既存の `cancelParticipation(raidId, userId)`。404 の判定はコントローラーで結果を見て行う（サービスは冪等のまま。#92 の決定どおり）

**POST `/api/raids/{raidId}/reports`** — 3T 報告
- ボディ: `{ "userId": "300000001", "username": "ニック", "difficulty": "hard" }`
- 200: `{ "alreadyReported": false, "participantRegistered": true }`（`ThreeTurnReportResponse`）
- 404: レイドが無い
- サービス: `reportThreeTurn(raidId, userId, username, difficulty, allowFinished = true)`。既に参加者なら `username`・`score` は変えない（#92 の仕様）。同じ難易度の再報告は報告時刻の更新で 200（`alreadyReported: true`）

**DELETE `/api/raids/{raidId}/reports/{userId}/{difficulty}`** — 3T 報告の取り消し
- 200: `{ "cancelledDifficulties": ["hard"], "participantRemoved": true }`（`ThreeTurnCancelResponse`。難易度は `value` の文字列）
- 404: レイドが無い、またはその難易度の報告が無い（結果の `cancelled` が空なら `ThreeTurnReportNotFound`）
- サービス: `cancelThreeTurnReports(raidId, userId, setOf(difficulty), allowFinished = true)`

共通:
- 400 は §3.2 のルール。401 は既存のフィルター。415 は §3.3。500 は想定外の例外（汎用メッセージ）。
- **Discord の Embed は更新しない**: コントローラーは `RaidNotificationScheduler` や JDA を注入しない・呼ばない。Embed の更新は listener の `runAfterCommit` の中にしかないので、サービスを呼ぶだけで要件を満たす。なお、その後に Discord のボタンで誰かが報告すると、`updateRaidMessage` が DB の全報告から Embed を作り直すので、Web の変更もそこで反映される（仕様上の副作用として API.md に書く）。
- 409 の判断（参加登録の重複）: issue に書かれていないので、tech lead の判断で 409 にした。「上書き（スコア更新）」にすると、二重送信や別の人の登録をスコアごと黙って上書きしてしまうため。web 側は 409 を「既に参加しています」と表示する想定（alerts に記載）。

### 3.5 サービスの処理順（`RaidDomainService`）

#92 の約束（`@Transactional(rollbackFor = [Exception::class])`、最初に `lockRaid`、`Result.failure` を返す分岐はすべて最初の書き込みより前）を守る。

**新規 `registerParticipant(raidId, userId, username, score, now = TimeUtils.utcNow())`: `Result<RaidParticipant>`**
1. `lockRaid(raidId)` → 失敗（`RaidNotFound`）ならそのまま返す
2. `inserted = raidParticipantRepository.insertParticipantIfAbsent(RaidParticipant(raidId = raidId, userId = userId, username = username, score = score, joinedAt = now))`
3. `inserted == 0L` なら `ParticipantAlreadyExists`（`ON CONFLICT DO NOTHING` で何も書いていないので、失敗を返してよい）
4. `findParticipantByRaidIdAndUserId(raidId, userId) ?: error("挿入直後の参加者が見つからない")` を返す

**新規 `updateParticipantScore(raidId, userId, score)`: `Result<RaidParticipant>`**
1. `lockRaid(raidId)`
2. `updated = raidParticipantRepository.updateParticipantScore(raidId, userId, score)`。`0L` なら `ParticipantNotFound`（何も書いていない）
3. 更新後の行を `findParticipantByRaidIdAndUserId` で読んで返す

**`reportThreeTurn` / `cancelThreeTurnReports`**: §3.1 の `allowFinished` を足すだけ。ほかの処理は変えない。

- サービスは `score` の範囲を自分でも確かめる（`require(score >= 0)` ではなく、`Result.failure` にするなら新しいエラーが要る）。**採用: サービスでは確かめない**。入力の検証はコントローラー（§3.2）の責務とし、KDoc に「`score` は 0 以上であること（呼び出し側で検証済み）」と書く。DB にも CHECK 制約は無いが、書き込みの入口は今このコントローラーだけ。
- KDoc には、`cancelParticipation` と同じく「本人か特権ユーザーか、終了済みレイドを操作してよいかの確認は呼び出し側（BFF）が行う」を書く。

### 3.6 セキュリティ（DEV review hardening）

- **認証**: パスはすべて `/api/` 配下なので `InternalApiTokenFilter` で保護される。回帰テストとして `InternalApiTokenFilterIntegrationTest` に「トークン無しの `POST /api/raids/{id}/reports` と `DELETE /api/raids/{id}/participants/{userId}` は 401」を足す（フィルターはメソッドを見ないが、書き込みメソッドでも通らないことを確かめておく）。
- **認可の境界**: bot にはユーザーの情報が無く、トークンを持つ呼び出し元は**どのレイドの、どのユーザーでも**書き換えられる。BFF は次を必ず確かめてからこの API を呼ぶこと（#91 の後続 web タスクの必須要件。alerts に記載）:
  1. `GET /api/raids/{raidId}` でレイドを取り、`checkParticipantEdit(session, raid, targetUserId)` が `allowed`（ギルドの所属・本人か特権・終了済みの扱い）
  2. 本人の操作なら、`userId` は**セッションの `userId`** を使う（クライアントから送られた値を使わない）
  3. 代理登録の `userId` / `username` は、#94 のメンバー一覧（`GET /api/guilds/{guildId}/members`）で得た値にする。bot はギルドのメンバーかどうかを確かめない（issue のバリデーションに含まれていないため。必要なら別 issue）
- **入力の上限**: `username` の長さと制御文字、`score` の範囲、未知のキーを拒否する（§3.2）。
- **ログ**: ボディの値（`username`・`userId`）は出さない。`raidId` と項目名・件数だけ。トークンも出さない（既存の規約）。
- **ロック**: 書き込みはすべて `RaidDomainService` のレイド行ロックを通る。終了処理（`finishUnionRaid`）とも直列になる。

### 3.7 依存パッケージ
追加しない。`tools.jackson.databind.JsonNode` は `spring-boot-starter-web` 経由で既に classpath にある（Gradle のキャッシュに `tools.jackson.core:jackson-databind:3.1.2` があることを確認）。Komapper の `QueryDsl.update(...).set { ... }.where { ... }` は 6.1.0 にある（コンパイルで確かめること）。

---

## 4. サブタスク

すべて **backend**（frontend のサブタスクはなし）。

| ID | 内容 | stack | 作成・変更するファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| B1 | リポジトリに参加者の 1 件取得とスコア更新を追加し、統合テストを足す | backend | `repository/RaidParticipantRepository.kt`、`test/.../repository/RaidParticipantRepositoryIntegrationTest.kt` | — | 0.75h |
| B2 | エラー・`Difficulty.fromValueOrNull`・サービスの新メソッドと `allowFinished` | backend | `service/RaidDomainError.kt`、`service/Difficulty.kt`、`service/RaidDomainService.kt` | B1 | 1.5h |
| B3 | サービスのテスト（連動・終了済み・404/409 相当）とトランザクションのテスト | backend | `test/.../service/RaidDomainServiceTest.kt`、`test/.../service/RaidDomainServiceTransactionTest.kt` | B2 | 2h |
| B4 | リクエストの読み取りと検証、その単体テスト | backend | 新規 `controller/RaidWriteRequests.kt`、新規 `test/.../controller/RaidWriteRequestsTest.kt` | B2（`Difficulty`） | 1.5h |
| B5 | DTO・コントローラーのエンドポイントと例外ハンドラー、コントローラーテスト、フィルターの回帰テスト | backend | `dto/ApiResponses.kt`、`dto/DtoMappers.kt`、`controller/RaidApiController.kt`、新規 `test/.../controller/RaidApiControllerWriteTest.kt`、`test/.../config/InternalApiTokenFilterIntegrationTest.kt` | B2, B4 | 2.5h |
| D1 | API 仕様・CodeBase マップ・規約の更新 | doc-sync | `kotlin/docs/API.md`、`docs/codebase/backend/routes-map.md`、`services-map.md`、`docs/conventions/backend.md` | B5 | 0.5h |

### テストの指針

ID は既存と被らない `70_093_xxx` 系（今は未使用）。B1: `70_093_0xx`、B3（サービス）: `70_093_1xx`、B5（コントローラー）: `70_093_2xx`、トランザクションのテスト: `70_093_9xx`。

**B1 `RaidParticipantRepositoryIntegrationTest`**
- `findParticipantByRaidIdAndUserId`: あれば返り、他ユーザー・他レイドは返さない、無ければ null
- `updateParticipantScore`: 対象だけ `score` が変わり `username`・`joinedAt` は変わらない、1 を返す。参加者でなければ 0

**B3 `RaidDomainServiceTest`**（区切りコメントはメソッドごと）
- `// ---- registerParticipant ----`
  - `参加登録_未参加なら指定のスコアとユーザー名で登録される`（`joinedAt` が `now`）
  - `参加登録_既に参加者ならParticipantAlreadyExistsエラーになりスコアは変わらない`
  - `参加登録_3凸報告だけで参加者の行が無いユーザーも登録できる`
  - `参加登録_終了済みレイドでも登録できる`
  - `参加登録_存在しないレイドはRaidNotFoundエラーになる`
- `// ---- updateParticipantScore ----`
  - `スコア更新_参加者のスコアだけが変わりユーザー名と参加時刻は変わらない`
  - `スコア更新_参加者でなければParticipantNotFoundエラーになる`
  - `スコア更新_終了済みレイドでも更新できる`
  - `スコア更新_存在しないレイドはRaidNotFoundエラーになる`
- `// ---- reportThreeTurn（allowFinished）----`
  - `3凸報告_allowFinishedなら終了済みレイドでも報告と自動参加ができる`
  - 既存の `終了済みレイド` のテスト（既定値で `RaidAlreadyFinished`）がそのまま通ること
- `// ---- cancelThreeTurnReports（allowFinished）----`
  - `報告取り消し_allowFinishedなら終了済みレイドでも取り消せて報告0件なら参加者も削除される`

**B3 `RaidDomainServiceTransactionTest`**（クラスに `@Transactional` が無い既存クラスに追加）
- `外側のトランザクションなし_プロキシ経由の参加登録とスコア更新がコミットされる`（`@Transactional` を外すと `lockRaid` の `check` で落ちる → プローブ P3）
- 既存の `トランザクション外_プロキシを通さない呼び出し…` に `registerParticipant` と `updateParticipantScore` を足す（`IllegalStateException` で、行が増減しない）
- 後片付けは既存と同じく `guildRepository.deleteGuildById` のカスケード

**B4 `RaidWriteRequestsTest`**（Spring なし。`tools.jackson.databind.json.JsonMapper` で文字列から `JsonNode` を作る）
- 通る例: 正しい 3 種類のボディ、`score` が 0 と 2147483647、`username` の前後の空白は除かれる
- 400 にする例（`InvalidRequestException`）: ボディが null / 配列 / 文字列、未知のキー、必須項目の欠落、`userId` が数値・空文字・`"abc"`・`"0"`・`"-1"`・21 桁、`username` が空白だけ・101 文字・改行入り・タブ入り・数値、`score` が `-1`・`1.5`・`"100"`・`2147483648`・`null`・`true`、`difficulty` が `"HARD"`・`"extreme"`・`1`

**B5 `RaidApiControllerWriteTest`**（`@SpringBootTest` + `@Transactional` + `webAppContextSetup`。`RaidApiControllerTest` と同じ形。MockMvc は同じスレッドで動くので、サービスの `@Transactional` はテストのトランザクションに参加し、ロールバックされる）
- 各エンドポイントの正常系: ステータス、`userId` が文字列、DB の状態（参加者・報告の有無）
- 3T 報告で未参加なら参加者ができる（`participantRegistered: true`）。最後の報告の取り消しで参加者が消える（`participantRemoved: true`）。参加の取り消しで報告も消える
- 終了済みレイドに対して、5 本とも成功する
- 404: 存在しないレイド（5 本とも）、未参加者の PATCH、参加者も報告も無い DELETE、未報告の難易度の DELETE
- 409: 参加登録の重複
- 400: `raidId=abc`、パスの `userId=abc` と `0`、パスの `difficulty=extreme`、ボディの不正（代表例を数件。網羅は B4）、**壊れた JSON**、**空のボディ**
- 415: `Content-Type: text/plain`
- Embed を更新しないこと: コントローラーが `RaidNotificationScheduler` を依存に持たないことはレビューで確かめる（テストでは扱わない。`discord.bot.enabled=false` のテストでは JDA が無いため）

**B5 `InternalApiTokenFilterIntegrationTest`**: トークン無しの `POST /api/raids/70093201/reports` と `DELETE /api/raids/70093201/participants/1` が 401。

---

## 5. 検証チェックリスト

規約（`docs/conventions/backend.md`・`testing.md`）由来:
- [ ] コントローラーからリポジトリの書き込みメソッドを呼んでいない。書き込みはすべて `RaidDomainService` 経由
- [ ] コントローラーは `RaidNotificationScheduler`・JDA を注入していない（Embed を更新しない）
- [ ] 新しいメソッドに `@Transactional(rollbackFor = [Exception::class])` があり、最初に `lockRaid` を通る。`Result.failure` を返す分岐は、実際に行を書き換える前にある
- [ ] コントローラーから `reportThreeTurn` / `cancelThreeTurnReports` に `allowFinished = true` を名前付き引数で渡している。listener は変更していない
- [ ] コンストラクタ注入 `private val`、`!!` なし、新しいワイルドカード import なし（`RaidApiController` の `dto.*` は個別 import に直した）、複数行の引数に末尾カンマ、引数 3 つ以上は名前付き引数
- [ ] 想定内の失敗は `Result` + `RaidDomainError` の新しいサブクラス。メッセージは日本語
- [ ] `service` / `repository` の public 関数とコントローラーのハンドラーに KDoc（`@param` / `@return` / 失敗時のエラー / ステータス）。新しいサービスメソッドの KDoc に「権限と終了済みの確認は呼び出し側」と書いてある
- [ ] テスト名は日本語・バッククォート、ID は `70_093_xxx` でテストごとに一意、`Thread.sleep` なし
- [ ] `init.sql` / `migrations/` に変更がない
- [ ] `set -a && . ./.env && set +a && cd kotlin && ./gradlew test` と `cd kotlin && ./gradlew build` が通る

入力の検証・エラー:
- [ ] `@RequestBody` は `tools.jackson.databind.JsonNode?`（Jackson 2 の `com.fasterxml` 版ではない）
- [ ] `score` は整数ノードかつ `canConvertToInt()` かつ 0 以上。`1.5`・`"100"` が 400
- [ ] `userId`（ボディ）は文字列だけを受け、1 以上の Long。パスの `userId` も 1 以上
- [ ] `username` は trim 後に 1〜100 文字で、制御文字を含まない
- [ ] `difficulty` は `fromValueOrNull` で判定し、`fromValue` の例外で 500 にならない
- [ ] 未知のキーは 400
- [ ] 壊れた JSON・空のボディは 400、`Content-Type` 違いは 415（500 にならない）
- [ ] ボディの値をログ・エラーメッセージに出していない

セキュリティ:
- [ ] 5 本とも `/api/` 配下で、トークン無しで 401 になるテストがある
- [ ] KDoc と API.md に「この API は権限を確かめない。BFF が `checkParticipantEdit` で確かめること」が書いてある

### プローブ（開発者が実行し、code-reviewer がもう一度実行して結果を報告する）

ガードテスト・静的検査は追加しないが、検証とトランザクションのテストが本当に効いているかを、次のプローブで確かめる。手順は `docs/conventions/testing.md` の「プローブの回し方」（作業ツリーを直接書き換えて戻す。`git stash` は使わない。`git diff | sha256sum` を前後で比べる）。

止まるべき例（テストが落ちること）:

| ID | 一時的な変更 | 落ちるはずのテスト |
|---|---|---|
| P1 | `HttpMessageNotReadableException` のハンドラーを消す | `RaidApiControllerWriteTest` の「壊れた JSON は 400」（500 になる） |
| P2 | `score` の検査を `isNumber`（`canConvertToInt` も外す）に緩める | `RaidWriteRequestsTest` の `1.5`・`2147483648` の 400 のテスト |
| P3 | `registerParticipant` の `@Transactional` を外す | `RaidDomainServiceTransactionTest` の「プロキシ経由の参加登録とスコア更新がコミットされる」 |
| P4 | コントローラーの `allowFinished = true` を外す（既定の `false` にする） | `RaidApiControllerWriteTest` の「終了済みレイドでも成功する」（報告・取り消し） |
| P5 | `username` の制御文字の検査を外す | `RaidWriteRequestsTest` の改行・タブ入りの 400 のテスト |
| P6 | `RaidApiController` の `JsonNode` の import を `com.fasterxml.jackson.databind.JsonNode` に変える（`RaidWriteRequests.kt` も合わせる） | `RaidApiControllerWriteTest` の正常系（201/200 にならない）。コンパイルが通らない場合はその結果を報告する |

通るべき例:
- [ ] 変更なしで `RaidApiControllerWriteTest`・`RaidWriteRequestsTest`・`RaidDomainServiceTest`・`RaidDomainServiceTransactionTest`・`InternalApiTokenFilterIntegrationTest`・既存の `RaidApiControllerTest` がすべて通る
- [ ] 既存の Discord 経由のテスト（`reportThreeTurn` の終了済みで `RaidAlreadyFinished`）が通る

### プローブ → ファイルの対応（再実行の判断用）

| プローブ | 書き換える・依存するファイル | 絞って回すテスト |
|---|---|---|
| P1 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt` | `--tests 'gg.nikke.raid.bot.controller.RaidApiControllerWriteTest'` |
| P2 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidWriteRequests.kt` | `--tests 'gg.nikke.raid.bot.controller.RaidWriteRequestsTest'` |
| P3 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainService.kt`（`repository/RaidParticipantRepository.kt` にも依存） | `--tests 'gg.nikke.raid.bot.service.RaidDomainServiceTransactionTest'` |
| P4 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt`（`service/RaidDomainService.kt` の `allowFinished` にも依存） | `--tests 'gg.nikke.raid.bot.controller.RaidApiControllerWriteTest'` |
| P5 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidWriteRequests.kt` | `--tests 'gg.nikke.raid.bot.controller.RaidWriteRequestsTest'` |
| P6 | `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt`、`controller/RaidWriteRequests.kt` | `--tests 'gg.nikke.raid.bot.controller.RaidApiControllerWriteTest'` |

人間による確認（任意。完了条件には含めない）:
- [ ] compose の bot に `curl -H 'X-Internal-Api-Token: ...'` で 5 本を叩き、Discord の通知 Embed が変わらないこと

ドキュメント（doc-sync）:
- [ ] `kotlin/docs/API.md` に 5 本（ボディ・レスポンス・エラー 400/404/409/415、権限は BFF、終了済みも可、Embed は更新しない）
- [ ] `routes-map.md`・`services-map.md`・`docs/conventions/backend.md` を更新

---

## 6. back / front の分担

| サブタスク | 担当 |
|---|---|
| B1〜B5 | backend 開発者（`kotlin/`）。B3 と B4 は B2 の後なら並行できる |
| — | frontend 開発者: **作業なし**（このタスクでは起動不要） |
| D1 | doc-sync |

---

## 7. 確認したファイル（CodeBase マップの外で開いたもの）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt`、`GuildMemberApiController.kt`（ハンドラーとエラーの返し方）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainService.kt`、`RaidDomainError.kt`、`ThreeTurnResults.kt`、`Difficulty.kt`（#92 の連動処理とロック、`fromValue` の例外）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/repository/RaidParticipantRepository.kt`、`RaidReportRepository.kt`（ガード付きの書き込み）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/entity/RaidParticipant.kt`、`dto/ApiResponses.kt`、`dto/DtoMappers.kt`
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/UnionRaidEventListener.kt`、`RaidNotificationScheduler.kt`（Embed の更新は listener の `runAfterCommit` からだけ呼ばれる）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/config/InternalApiTokenFilter.kt`（メソッドを問わず `/api/` を保護）
- `kotlin/build.gradle.kts` と Gradle のキャッシュ（Jackson 3.1.2 が MVC で使われ、Kotlin モジュールは Jackson 2 版だけ）
- `init.sql`（`score INTEGER`、`username VARCHAR(255)`、一意制約）
- `web/lib/permissions.ts`（`checkParticipantEdit`: 特権ユーザーは終了済みも可）
- `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/RaidApiControllerTest.kt`、`service/RaidDomainServiceTransactionTest.kt`（テストの形と使用中の ID）

ドキュメントとコードの食い違い: なし。`docs/conventions/backend.md` の「`RaidApiController` は読み取り専用でリポジトリを直接呼ぶ」は、このタスクで古くなるので doc-sync で直す。
