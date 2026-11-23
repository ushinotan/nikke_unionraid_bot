# Tech Lead 計画 — #92 参加者と 3T 報告を連動させる

## エグゼクティブサマリー

| 項目 | 内容 |
|---|---|
| 種別 | story（親: #91 代理登録） |
| 複雑度 | 指定なし（見立て: M） |
| ブランチ | `feature/92` |
| スコープ | **backend のみ**（`kotlin/`）。web の変更はなし |
| 影響 | `service/RaidDomainService.kt`（トランザクション・連動処理・`cancelParticipation` を追加）、`repository/{UnionRaid,RaidParticipant,RaidReport}Repository.kt`（行ロック付きの取得・挿入・削除メソッドを追加）、新規 `service/ThreeTurnResults.kt`（結果型）、`bot/UnionRaidEventListener.kt`（結果型に合わせて文言を変え、想定外の例外を処理する） |
| スキーマ変更 | **なし**（`init.sql` と `migrations/` は触らない。理由は §3.5） |
| 見積もり | 実装 0.75 日、テスト 0.75 日、ドキュメント（doc-sync）0.25 日 |
| リスク | 中。同時操作で参加者と報告の整合性が崩れないように、**レイド行のロック**と**Spring のトランザクション境界**を正しく組む必要がある。手動登録者のスコアが消える仕様なので、ユーザーへの伝え方にも注意する |
| ブロッカー | なし（`depends_on` なし） |
| 受け入れ条件の絞り込み | **しない**（4 項目すべてこのタスクで満たす） |

---

## 1. Blueprint の要約（PO の要求）

参加者（`raid_participants`）と 3T 報告（`raid_reports`）を連動させる。Discord のボタンからでも Web からでも同じ処理を通るように、連動処理は `RaidDomainService` に置く。

- 3T 報告を登録したとき、まだ参加者でなければ自動で登録する（スコア 0、`username` は報告者の名前）
- 3T 報告を取り消して報告が 0 件になったら、参加者も削除する（手動で参加登録した人も対象で、入力済みのスコアも消える）
- 参加を取り消すと、その人の 3T 報告もすべて削除する（Web から使うドメインメソッドとして用意する。API はまだ作らない）
- 各処理はトランザクションの中で行い、参加者と報告の整合性を保つ
- `UnionRaidEventListener` の報告・取り消しが、上記の連動処理を通るようにする
- 既存の `raid_reports` を参加者へ移すマイグレーションはしない

完了条件: (1) Discord ボタンの 3T 報告で参加者に自動登録される、(2) 取り消して 0 件になると参加者も消える、(3) 参加取り消しのドメインメソッドで参加者と報告がまとめて消える、(4) 連動処理のテストがある。

親 #91 の前提（このタスクの範囲外だが設計に効く）: 終了済みレイドを操作してよいかどうかは web 側（`web/lib/permissions.ts`: 特権ユーザーは終了済みも可、一般メンバーは不可）で判定する。

---

## 2. 影響マップ（CodeBase 由来の実名）

パッケージのルートは `kotlin/src/main/kotlin/gg/nikke/raid/bot/`。

### backend

| 層 | ファイル | 変更 |
|---|---|---|
| repository | `repository/UnionRaidRepository.kt` | **追加** `findUnionRaidByIdForUpdate(raidId: Int): UnionRaid?`（`SELECT ... FOR UPDATE`）。トランザクションの中で呼ぶ前提を KDoc に書く |
| repository | `repository/RaidParticipantRepository.kt` | **追加** `insertParticipantIfAbsent(participant): Long`（`ON CONFLICT (raid_id, user_id) DO NOTHING`。戻り値は挿入件数 1/0）、`deleteParticipant(raidId, userId): Long`。既存の `insertOrUpdateParticipant` は変えない（KDoc の「本体コードから使われていない」注意書きはそのまま正しい） |
| repository | `repository/RaidReportRepository.kt` | **追加** `deleteRaidReportsByRaidIdAndUserId(raidId, userId): Long`（難易度・`is_3t` を問わず全行を削除） |
| service | `service/RaidDomainService.kt` | コンストラクタに `RaidParticipantRepository` を追加。`reportThreeTurn` / `cancelThreeTurnReports` を連動処理つき・トランザクションつきに変更。**新規** `cancelParticipation`。private `lockRaid` / `lockActiveRaid`（§3.2） |
| service | **新規** `service/ThreeTurnResults.kt` | 結果型 `ThreeTurnReportResult`、`ThreeTurnCancelResult`、`ParticipationCancelResult`（§3.3） |
| service | `service/RaidDomainError.kt` | **変更なし**（新しい失敗パターンは無い） |
| bot | `bot/UnionRaidEventListener.kt` | `handleDifficultySelect` / `handleCancelSelect` を新しい結果型に合わせる。`hasReported` の事前呼び出しをやめ、結果型の `alreadyReported` を使う。参加者から外れたときに文言で伝える。想定外の例外を `logger.error` + 汎用メッセージで返す（§3.6） |
| controller / dto | — | **変更なし**（`GET /api/raids/{raidId}` の `participants` に自動登録された人が出るようになるだけ。API の形は変わらない） |
| 永続化 | `init.sql`、`migrations/` | **変更なし**（§3.5） |
| entity | `entity/RaidParticipant.kt` | **変更なし**。ただし `joinedAt` の既定値が `null` なので、自動登録では `joinedAt = now` を必ず渡す（渡さないと Komapper が `NULL` を明示的に挿入し、`DEFAULT now()` が効かない） |

依存の向きは `docs/conventions/backend.md` のとおり（`bot → service → repository`）。listener からリポジトリの書き込みメソッドを直接呼ばない。

### frontend
変更なし。参加取り消しの BFF ルート・画面は #91 の後続タスクで作る（alerts に記載）。

### ドキュメント（doc-sync が担当）

| ファイル | 内容 |
|---|---|
| `docs/codebase/backend/services-map.md` | `reportThreeTurn` の「`raid_participants` には書かない」を、連動処理の説明に直す。`cancelParticipation` を追加。`RaidParticipantRepository` の「今は本体コードから呼ばれていない」を直し、追加したリポジトリメソッドを書く。「参加者・報告に書き込む処理はレイド行を `FOR UPDATE` でロックしてから行う」というロックの約束を書く |
| `docs/codebase/backend/entities.md` | `RaidParticipant` の「今は本体コードから書き込まれていない」を「3T 報告で自動登録、報告 0 件で削除」に直す |
| `docs/codebase/backend/routes-map.md` | ボタン `raid_report` / `raid_report_cancel` の処理欄に、参加者の自動登録・削除を追記 |
| `kotlin/docs/API.md` | API の形は変わらない。`participants` の説明に「3T 報告すると自動で入る（スコア 0）」と一文足す程度（任意） |

---

## 3. 技術方針

### 3.1 トランザクション境界（Komapper + Spring）

- `komapper-spring-boot-starter-jdbc` は Spring のトランザクションに `JdbcDatabase` の接続を乗せる（既存のテストが `@Transactional` でロールバックできているのがその証拠）。したがって **Spring の `@Transactional` を使う**。Komapper の `database.withTransaction {}` は混ぜない。
- 付ける場所: `RaidDomainService` の `reportThreeTurn`、`cancelThreeTurnReports`、`cancelParticipation` の 3 つ（public メソッド）。
  ```kotlin
  @Transactional(rollbackFor = [Exception::class])
  ```
  - **`rollbackFor = [Exception::class]` は必須**。Spring の既定は「非チェック例外だけロールバック」で、Komapper / JDBC が `java.sql.SQLException`（Java ではチェック例外）を投げた場合にコミットされてしまう。
  - `Result.failure` を返してもロールバックはされない（例外ではないので）。したがって **失敗を返す判定はすべて書き込みの前に済ませる**（§3.4 の順序を守る）。書き込みの後で `Result.failure` を返す分岐を作らない。
- プロキシ: `kotlin("plugin.spring")` が `@Service` クラスを open にするので AOP プロキシは効く。ただし **同じクラスの中から呼ぶと `@Transactional` は効かない**。listener からは注入された Bean 経由で呼ぶ（今の形のまま）。サービス内で `reportThreeTurn` から別の `@Transactional` メソッドを呼ぶ形にはしない（private ヘルパーに分ける）。
- 伝播は既定の `REQUIRED`。テスト（クラスに `@Transactional`）ではテストのトランザクションに参加する。
- **fail-closed の保険**: private `lockRaid` の先頭で
  ```kotlin
  check(TransactionSynchronizationManager.isActualTransactionActive()) {
      "参加者・報告の更新はトランザクション内で呼ぶ必要がある"
  }
  ```
  を置く。トランザクションの外（プロキシを通らない呼び出し・`@Transactional` の付け忘れ）では `FOR UPDATE` のロックが文の終わりで外れて整合性が守れないため、書き込む前に `IllegalStateException` で止める。

### 3.2 同時実行（ロックと分離レベル）

想定する競合:
- 同じ人が同じボタンを 2 回押す / 2 つのメニューで同時に操作する
- 同じ人の「報告」と「取り消し」が同時に走る（Discord と、後続の Web から同時など）
- 「報告」と「レイド終了」（`/レイド終了`、自動終了）が同時に走る

**チェックしてから書く（check-then-act）だけだと壊れる例**: T1 が最後の報告を取り消して「残り 0 件」と判断し、参加者を消す直前に、T2 が別の難易度を報告して `ON CONFLICT DO NOTHING`（参加者はまだいるので何もしない）を通る。T2 の報告は READ COMMITTED では T1 から見えないので、T1 はそのまま参加者を消す → **参加者のいない報告**が残る。一意制約や upsert だけではこれを防げない。

**採用: レイド行（`union_raids`）を `SELECT ... FOR UPDATE` でロックしてから読む・書く。**
- 3 メソッドとも、最初に `unionRaidRepository.findUnionRaidByIdForUpdate(raidId)` を呼ぶ。同じレイドへの書き込みはここで直列になる。
- 終了判定もロックした行で行う。`finishUnionRaid` は同じ行への `UPDATE` なので、報告と終了は必ずどちらかが先に完了する。報告が後なら、ロック取得時に最新の行（`finished_at` あり）が返り `RaidAlreadyFinished` になる。今ある「終了直後に報告が保存される」レースもこれで閉じる。
- **分離レベルは既定の READ COMMITTED のまま**。ロックを取った後の各 SQL は新しいスナップショットで読むので、前の持ち主がコミットした報告・参加者が見える。REPEATABLE READ / SERIALIZABLE にすると、ロック待ちの後にシリアライズ失敗になり、リトライ処理が要るので使わない。
- **ロックの順序**: 必ず `union_raids`（行ロック）→ `raid_reports` → `raid_participants`。`guilds` には触らない。ほかに `union_raids` を書くのは `finishUnionRaid` / `markNotificationSent` / `clearNotifyTime`（どれも 1 文で、ほかの行のロックを取らない）と、ギルド退出時の `DELETE guilds`（`guilds` → `union_raids` → 子の順でカスケード）なので、循環待ち（デッドロック）は起きない。
- 粒度: レイド単位で直列になるが、1 ギルド数十人のボタン操作で、ロックを持つのは数ミリ秒（SQL 3〜4 本）なので問題ない。読み取り（`GET /api/raids/{id}`、通知 Embed の更新）は MVCC なのでブロックされない。
- `FOR UPDATE` か `FOR NO KEY UPDATE` か: 子テーブルへの INSERT は外部キーの検査で親行に `KEY SHARE` ロックを取るため、`FOR UPDATE` だと**別トランザクションからの**子の INSERT もブロックされる。ただし子への書き込みはすべてこのロックを通す約束なので実害はない。Komapper の DSL で書ける `forUpdate()` を使う（文字列 SQL にしない）。
- Discord の 3 秒ルール: ロック待ちは数ミリ秒の想定。選択メニューの処理は今も `deferEdit` していないが、そのままでよい（変える必要が出たら別タスク）。

**後続タスクへの約束**: 参加者・報告を書き込む処理（#91 の手動参加登録、スコア更新 #96 / #105 など）は、すべて `RaidDomainService` に置き、同じ `lockRaid` を最初に通すこと。通さない書き込みが 1 つでもあると、上の保証が崩れる。→ alerts に記載。

### 3.3 ドメインの結果型（新規 `service/ThreeTurnResults.kt`）

```kotlin
/** 3凸報告の結果。 */
data class ThreeTurnReportResult(
    /** 同じ難易度を報告済みだった（報告時刻だけ更新した）なら true */
    val alreadyReported: Boolean,
    /** この報告で参加者に自動登録したなら true */
    val participantRegistered: Boolean,
)

/** 3凸報告の取り消し結果。 */
data class ThreeTurnCancelResult(
    /** 実際に取り消された難易度。未報告の難易度だけを指定したときは空 */
    val cancelled: Set<Difficulty>,
    /** 報告が 0 件になり参加者からも削除したなら true */
    val participantRemoved: Boolean,
)

/** 参加取り消しの結果。 */
data class ParticipationCancelResult(
    /** 参加者の行を削除したなら true（参加者でなかった場合は false） */
    val participantRemoved: Boolean,
    /** 削除した 3凸報告の件数 */
    val removedReportCount: Long,
)
```

- `reportThreeTurn` の戻り値を `Result<Long>`（中身は upsert の件数で、KDoc の「ID」は誤り）から `Result<ThreeTurnReportResult>` に変える。`alreadyReported` をロックの中で判定するので、listener の `hasReported` 事前呼び出し（ロックの外で読むので不正確）が要らなくなる。
- `cancelThreeTurnReports` の戻り値を `Result<Set<Difficulty>>` から `Result<ThreeTurnCancelResult>` に変える。listener が「参加者からも外れた（スコアも消えた）」ことを伝えられるようにするため。
- 呼び出し元は listener とテストだけ（`Grep` で確認済み）。

### 3.4 各メソッドの処理順

共通の private ヘルパー:
```kotlin
/** レイド行をロックして返す。無ければ RaidNotFound。トランザクション外なら IllegalStateException */
private fun lockRaid(raidId: Int): Result<UnionRaid>
/** lockRaid に加えて、終了済みなら RaidAlreadyFinished */
private fun lockActiveRaid(raidId: Int): Result<UnionRaid>
```
既存の public `ensureRaidActive`（ロックなし）は listener のボタン処理（メニューを出すかどうかの事前確認）用にそのまま残す。

**`reportThreeTurn(raidId, userId, username, difficulty, now)`** — `@Transactional(rollbackFor = [Exception::class])`
1. `lockActiveRaid(raidId)` → 失敗ならそのまま返す（まだ何も書いていない）
2. `alreadyReported = raidReportRepository.findRaidReport(raidId, userId, difficulty.value) != null`
3. `raidReportRepository.saveRaidReport(...)`（既存の upsert のまま）
4. `participantRegistered = raidParticipantRepository.insertParticipantIfAbsent(RaidParticipant(raidId, userId, username = username, score = 0, joinedAt = now)) == 1L`
   - 既に参加者なら**何も変えない**（手動登録者の `username`・`score`・`joined_at` を上書きしない。issue の「未参加なら登録」のとおり）
5. `Result.success(ThreeTurnReportResult(alreadyReported, participantRegistered))`

**`cancelThreeTurnReports(raidId, userId, difficulties)`** — `@Transactional(rollbackFor = [Exception::class])`
1. `difficulties` が空 → `NoDifficultySelected`（ロック前）
2. `lockActiveRaid(raidId)` → 失敗ならそのまま返す
3. `targets = findReportedDifficulties(raidId, userId) ∩ difficulties`。空なら `ThreeTurnCancelResult(emptySet(), participantRemoved = false)` を返す
   - **何も消さなかったときは参加者を消さない**（報告なしで手動登録している人が、空振りの取り消しで消えないようにする）
4. `raidReportRepository.deleteRaidReports(raidId, userId, targets.map { it.value })`
5. `remaining = raidReportRepository.findRaidReportsByRaidIdAndUserId(raidId, userId)`（`is_3t = 1`。取り消しメニュー・集計と同じ「報告」の定義）
6. `remaining` が空なら `raidParticipantRepository.deleteParticipant(raidId, userId)`。`participantRemoved` は削除件数 > 0
   - 移行していない既存の報告（参加者の行が無い）でも、削除 0 件で問題なく通る
7. `Result.success(ThreeTurnCancelResult(targets, participantRemoved))`

**新規 `cancelParticipation(raidId, userId)`** — `@Transactional(rollbackFor = [Exception::class])`
1. `lockRaid(raidId)` → 無ければ `RaidNotFound`
   - **終了済みかどうかは判定しない**。#91 の方針どおり、終了済みレイドを操作してよいか（特権ユーザーは可）は web の権限判定（`requireRaidEditPermission` / `lib/permissions.ts`）が決める。KDoc に明記する
2. `removedReportCount = raidReportRepository.deleteRaidReportsByRaidIdAndUserId(raidId, userId)`（`is_3t` を問わず全行。参加をやめたら報告の痕跡を残さない）
3. `participantRemoved = raidParticipantRepository.deleteParticipant(raidId, userId) > 0`
4. `Result.success(ParticipationCancelResult(participantRemoved, removedReportCount))`
   - 参加者でも報告者でもなかった場合も成功（両方 0）。冪等にして、二重送信で失敗にしない。404 にするかどうかは後続の web / API 側で結果を見て決められる
- `userId` に誰でも指定できるメソッドなので、**呼び出し側（後続の BFF）が「本人か特権ユーザーか」を必ず確認すること**を KDoc に書く（backend にはユーザー文脈がない）。

### 3.5 スキーマ変更をしない理由

- 必要な一意制約はすでにある: `raid_participants UNIQUE(raid_id, user_id)`（自動登録の `ON CONFLICT DO NOTHING` のキー）、`raid_reports UNIQUE(raid_id, user_id, difficulty)`。
- 検索・削除の条件 `(raid_id, user_id)` は、どちらのテーブルでも一意制約のインデックスの先頭列で引ける。新しいインデックスは要らない。
- **検討して採らなかった案: `raid_reports(raid_id, user_id)` → `raid_participants(raid_id, user_id)` の複合外部キー（`ON DELETE CASCADE`）**
  - 既存の `raid_reports` には参加者の行が無いので、制約を足すには既存データを参加者に移す必要がある。issue で「移行しない」と決まっている。
  - 外部キーだけでは §3.2 の競合は解決しない（取り消し側が古い件数で参加者を消すと、カスケードで同時に入った報告まで消える）。結局レイド行のロックが要る。
- したがって `init.sql` と `migrations/` は変更しない。

### 3.6 listener（`bot/UnionRaidEventListener.kt`）の変更

- `handleDifficultySelect`
  - `raidDomainService.hasReported(...)` の事前呼び出しを削除し、結果の `alreadyReported` で文言を分ける。
  - 文言（例）: 新規かつ自動登録 →「報告を受け付けました。」のまま（参加者に入ったことは言わなくてよい）。`alreadyReported` →「{難易度}は既に報告済みでした。報告時刻を更新しました。」のまま。
- `handleCancelSelect`
  - `result.getOrThrow()` の集合は `cancelled` に読み替える。
  - `participantRemoved == true` のとき、末尾に「3凸報告が無くなったため、参加者からも外れました（入力済みのスコアも削除されます）。」を付ける。手動登録者のスコアが黙って消えるのを避けるため。
- 両方とも、サービス呼び出しを `try { ... } catch (e: Exception) { logger.error("...", e); replaceSelectMenu(event, "エラーが発生しました。時間をおいて再度お試しください。") }` で囲む。ロック待ちのタイムアウトや DB の一時エラーで、Discord に「インタラクションに失敗しました」だけが出る状態を避ける。例外メッセージ（`e.message`）はユーザーに出さない。ログには `raidId` だけを出し、ユーザー名は出さない。
- `userId` は今のとおり `event.user.idLong` から取る（コンポーネント ID やメッセージから取らない）。本人の報告しか触れない現在の性質を保つ。
- listener からリポジトリの書き込みメソッドを呼ばない（`guildRepository.insertGuild` など既存のものは対象外）。

### 3.7 既存データ・運用への影響

- デプロイ時点で進行中のレイドでは、デプロイ前に報告した人は参加者に入っていない。その人が報告し直すか、新しいレイドから揃う（issue で移行しないと決定済み）。
- web はまだ `participants` を画面に出していない（`web/lib/api-types.ts` に型だけある）ので、表示の変化は無い。
- `GET /api/raids/{raidId}`（`RaidApiController`）は参加者と報告を別々の SQL で読むため、書き込みの途中では片方だけ新しい値が見えることがありうる。読み取り専用で次の取得で揃うので、このタスクでは直さない（後続で一貫性が要るなら `@Transactional(readOnly = true)` などを検討）。
- 依存パッケージの追加・バージョン変更はない（Komapper 6.1.0 の `forUpdate()` / `onDuplicateKeyIgnore()` を使う。名前はコンパイルで確認すること）。

---

## 4. サブタスク

すべて **backend**（frontend のサブタスクはなし）。

| ID | 内容 | stack | 作成・変更するファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| B1 | リポジトリにロック付き取得・条件付き挿入・削除を追加し、統合テストを足す | backend | `repository/UnionRaidRepository.kt`、`repository/RaidParticipantRepository.kt`、`repository/RaidReportRepository.kt`、`test/.../repository/{UnionRaid,RaidParticipant,RaidReport}RepositoryIntegrationTest.kt` | — | 1.5h |
| B2 | 結果型を追加し、`RaidDomainService` に連動処理・トランザクション・ロック・`cancelParticipation` を実装 | backend | 新規 `service/ThreeTurnResults.kt`、`service/RaidDomainService.kt` | B1 | 2h |
| B3 | listener を結果型に合わせ、文言と例外処理を直す | backend | `bot/UnionRaidEventListener.kt` | B2 | 1h |
| B4 | 連動処理のサービステスト（既存テストの戻り値の読み替えを含む） | backend | `test/.../service/RaidDomainServiceTest.kt` | B2 | 2h |
| B5 | トランザクション境界・同時実行のテスト（`@Transactional` を付けない別クラス） | backend | 新規 `test/.../service/RaidDomainServiceTransactionTest.kt` | B2 | 2h |
| D1 | CodeBase マップと API 仕様の更新 | doc-sync | `docs/codebase/backend/services-map.md`、`entities.md`、`routes-map.md`、（任意）`kotlin/docs/API.md` | B3 | 0.5h |

### テストの指針

**B1 リポジトリ統合テスト**（既存の形: `@SpringBootTest` + `@Transactional`。ID は既存と被らない `70_092_0xx` 系）
- `insertParticipantIfAbsent`: 新規は 1 を返して行ができる（`score = 0`、`joinedAt` が渡した値）。既存の参加者がいると 0 を返し、`username`・`score` が変わらない。
- `deleteParticipant`: 対象のユーザーだけ消え、同じレイドの他ユーザー・他レイドの同じユーザーは残る。
- `deleteRaidReportsByRaidIdAndUserId`: 両難易度と `is3t = 0` の行も消え、他ユーザーの行は残る。件数を返す。
- `findUnionRaidByIdForUpdate`: あれば返り、無ければ null（ロックそのものは B5 で確かめる）。

**B4 `RaidDomainServiceTest`**（既存クラスに追加。区切りコメントはメソッドごと。名前は `` `<操作>_<条件>で<結果>` ``）
- `// ---- reportThreeTurn（参加者連動）----`
  - `3凸報告_未参加なら参加者にスコア0と報告者名で登録される`（`participantRegistered = true`、`joinedAt` が `now`）
  - `3凸報告_手動登録済みの参加者はユーザー名とスコアが上書きされない`（先に `insertOrUpdateParticipant` で score=1234、名前 "manual" を入れておく。`participantRegistered = false`）
  - `3凸報告_同じ難易度の再報告はalreadyReportedがtrueになる`
  - `3凸報告_2つ目の難易度でも参加者は1行のまま`
  - `3凸報告_終了済みレイドでは参加者も登録されない`（既存の終了済みテストに参加者 0 件の確認を足してもよい）
- `// ---- cancelThreeTurnReports（参加者連動）----`
  - `報告取り消し_最後の報告を取り消すと参加者も削除される`（`participantRemoved = true`）
  - `報告取り消し_報告が残っていれば参加者は残る`（ノーマル・ハード報告 → ノーマルだけ取り消し）
  - `報告取り消し_手動登録でスコア入力済みでも報告0件で参加者が削除される`（issue の明記事項）
  - `報告取り消し_未報告の難易度だけを指定すると報告なしの手動登録者は削除されない`（空振りで消さない）
  - `報告取り消し_参加者の行が無い既存の報告でも取り消せる`（移行なしのデータ。リポジトリで報告だけ直接入れる）
  - `報告取り消し_終了済みレイドは参加者も報告も残る`
  - 既存の取り消しテストは `getOrThrow()` → `getOrThrow().cancelled` に読み替える
- `// ---- cancelParticipation ----`
  - `参加取り消し_参加者と全難易度の報告がまとめて削除される`（`participantRemoved = true`、`removedReportCount = 2`）
  - `参加取り消し_他ユーザーの参加者と報告は残る`
  - `参加取り消し_報告だけで参加者の行が無くても報告は削除される`
  - `参加取り消し_参加者でも報告者でもなければ成功し件数は0`
  - `参加取り消し_存在しないレイドはRaidNotFoundエラーになる`
  - `参加取り消し_終了済みレイドでも削除できる`（終了済みの判定は web 側という方針の確認）

**B5 `RaidDomainServiceTransactionTest`**（`@SpringBootTest`、**クラスに `@Transactional` を付けない**。実際にコミットされるので、`@AfterEach` か `try/finally` で `guildRepository.deleteGuildById(guildId)` を呼び、カスケードで後片付けする。ギルド ID は `70_092_9xx` 系）
- **原子性（ロールバック）**: `@MockitoSpyBean` で `RaidParticipantRepository` を差し替え、`insertParticipantIfAbsent` で `doAnswer { throw java.sql.SQLException("probe") }` を投げる（`doThrow` だと Kotlin のメソッドはチェック例外を宣言していないので Mockito が拒否する）。`reportThreeTurn` が例外を投げた後、**報告の行が残っていない**ことを確かめる。`rollbackFor` を付け忘れるとこのテストが落ちる。
- **ロックによる直列化**: `TransactionTemplate`（`PlatformTransactionManager` を注入）で T1 を開き、中で `cancelThreeTurnReports`（最後の報告を取り消す）を呼んだ後、**コミット前に**別スレッドで `reportThreeTurn`（別の難易度）を `CompletableFuture` で開始する。`future.get(500, MILLISECONDS)` が `TimeoutException` になる（= ロック待ちしている）ことを確かめてから T1 をコミットし、future の完了後に「HARD の報告があり、参加者もいる」ことを確かめる。ロックが無い実装では future がすぐ終わるので落ちる。`Thread.sleep` は使わない。
- **トランザクション外の呼び出しを止める**: `RaidDomainService(unionRaidRepository, raidReportRepository, raidParticipantRepository)` をプロキシを通さず直接作って `reportThreeTurn` を呼ぶと `IllegalStateException` になり、報告の行ができていないこと。

---

## 5. 検証チェックリスト

規約（`docs/conventions/backend.md`・`database.md`・`testing.md`）由来:
- [ ] listener からリポジトリの書き込みメソッドを直接呼んでいない。連動処理はすべて `RaidDomainService` にある
- [ ] SQL を文字列で組み立てていない（`forUpdate()`・`onDuplicateKeyIgnore()` など Komapper の DSL）
- [ ] コンストラクタ注入 `private val`、`!!` なし、ワイルドカード import を新規に増やさない、複数行の引数に末尾カンマ、引数 3 つ以上は名前付き引数
- [ ] 想定内の失敗は `Result` + `RaidDomainError`。想定外の例外は `logger.error(..., e)` で記録し、ユーザーには汎用メッセージ（`e.message` を出さない）
- [ ] 追加・変更した `service` / `repository` の public 関数に KDoc（`@param` / `@return` / 失敗時のエラー）。`cancelParticipation` の KDoc に「終了済み判定と本人・特権の確認は呼び出し側」と書いてある。`reportThreeTurn` の KDoc の「ID を返す」という誤りを直してある
- [ ] テスト名は日本語・バッククォート、ギルド ID・ユーザー ID はテストごとに一意、`Thread.sleep` なし
- [ ] `init.sql` / `migrations/` に変更がない（§3.5）
- [ ] `set -a && . ./.env && set +a && cd kotlin && ./gradlew test --no-daemon` と `cd kotlin && ./gradlew build` が通る

トランザクション・同時実行:
- [ ] 3 メソッドに `@Transactional(rollbackFor = [Exception::class])` が付いている（`org.springframework.transaction.annotation.Transactional`）
- [ ] 3 メソッドとも最初に `findUnionRaidByIdForUpdate` でレイド行をロックし、終了判定はロックした行で行っている
- [ ] `Result.failure` を返す分岐が、すべて最初の書き込みより前にある
- [ ] `lockRaid` の先頭に `TransactionSynchronizationManager.isActualTransactionActive()` の `check` がある
- [ ] 分離レベルを変えていない（READ COMMITTED のまま）
- [ ] 自動登録は `ON CONFLICT DO NOTHING` で、既存の参加者の `username`・`score`・`joined_at` を上書きしない。`joinedAt = now` を渡している
- [ ] 取り消しで何も消さなかったときは参加者を消さない

ガードのプローブ（DEV review hardening。開発者が実行し、code-reviewer がもう一度実行して結果を報告する）:
- 止まるべき例（すり抜けてはいけない）
  - [ ] `RaidDomainService` をプロキシなしで作って `reportThreeTurn` / `cancelThreeTurnReports` / `cancelParticipation` を呼ぶ → 3 つとも `IllegalStateException` で、行が増減していない（B5 のテストは `reportThreeTurn` だけなので、残り 2 つは一時的に手で確かめる）
  - [ ] 一時的に `rollbackFor` を外す → B5 の原子性テストが落ちる（確認後に戻す）
  - [ ] 一時的に `findUnionRaidByIdForUpdate` を `findUnionRaidById`（ロックなし）に差し替える → B5 の直列化テストが落ちる（確認後に戻す）
- 通るべき例
  - [ ] 既存の `RaidDomainServiceTest`（テストのトランザクションに参加）がすべて通る
  - [ ] B5 の 3 テストが通り、テスト後に `70_092_9xx` のギルド・レイドが DB に残っていない

Discord（listener）:
- [ ] `hasReported` の事前呼び出しが無く、`alreadyReported` で文言を分けている
- [ ] `participantRemoved` のときにスコアも消える旨を伝えている
- [ ] サービス呼び出しの想定外の例外で、選択メニューが汎用エラー文に置き換わる
- [ ] `userId` は `event.user.idLong` から取っている

人間による確認（本物の Discord。完了条件 1・2）:
- [ ] ボタンで 3T 報告すると、`GET /api/raids/{raidId}` の `participants` に自分（スコア 0）が出る
- [ ] 報告をすべて取り消すと `participants` から消え、メッセージに参加者から外れた旨が出る

ドキュメント（doc-sync）:
- [ ] `services-map.md`・`entities.md`・`routes-map.md` の「参加者は書き込まれていない」系の記述を直し、ロックの約束（参加者・報告への書き込みは `RaidDomainService` でレイド行をロックしてから）を書いた

---

## 6. back / front の分担

| サブタスク | 担当 |
|---|---|
| B1〜B5 | backend 開発者（`kotlin/`）。B4 と B5 は B2 の後なら並行可 |
| — | frontend 開発者: **作業なし**（このタスクでは起動不要） |
| D1 | doc-sync |

---

## 7. 確認したファイル（CodeBase マップの外で開いたもの）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainService.kt`（今の報告・取り消し処理と順序）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/UnionRaidEventListener.kt`（ボタン・選択メニューの処理、`hasReported` の事前呼び出し）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/repository/{RaidParticipant,RaidReport,UnionRaid}Repository.kt`（既存の upsert・削除・`finishUnionRaid` の条件）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/entity/RaidParticipant.kt`（`joinedAt` の既定値が `null`）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainError.kt`
- `init.sql`（一意制約・外部キー・インデックス）
- `kotlin/build.gradle.kts`（`plugin.spring`、Komapper 6.1.0。`@Transactional` の使用箇所は本体に無く、テストだけ）
- `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/RaidDomainServiceTest.kt`、`repository/RaidParticipantRepositoryIntegrationTest.kt`（テストの型と使用中の ID）
- `web/lib/permissions.ts`（終了済みレイドの扱い: 特権ユーザーは可）

ドキュメントとコードの食い違い: なし（`services-map.md` / `entities.md` の「参加者は本体から書き込まれていない」は現時点では正しく、このタスクで古くなるので doc-sync で直す）。
