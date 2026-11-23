## Code Review — #92 — attempt 2 — 2026-11-23

### 判定: ⚠️ APPROVED WITH NOTES

前回の差し戻し理由だった Critical 3 件（P4・P5 が見逃されること、原子性テストの assert が緩いこと）は、どれも直っている。レビュー側でプローブ P1〜P6 を再実行し、止まるべき書き換えはすべてテストで検出された。
スタックレビュアーの指摘のうち修正ラウンドで直す予定だったもの（K1、K2、K4、D1、D4）も対応済み。残っているのは、差し戻すほどではない 🟡 が 2 件と、doc-sync の未実行だけ。

---

### ガードのプローブ（レビュー側で再実行）

前回と同じ手順で実行した。`kotlin/` をスクラッチ領域にコピーし、そこを書き換えて `service.*` と `repository.*`（101 件）を実行した。作業ツリーのファイルは変更していない。書き換えたファイルは毎回作業ツリーから戻し、`diff -r` で一致を確認済み。DB は compose の `nikke_unionraid_db`。

| # | プローブ | 期待 | 結果 |
|---|---|---|---|
| 基準 | 変更なしでテスト全体（`./gradlew test`）を実行 | 全件成功 | ✅ 189 件成功、スキップ 1 件（既存の `@Disabled`、#54） |
| P1 | プロキシを通さずに 3 メソッドを呼ぶ → `IllegalStateException`、行は増減しない | 成功 | ✅ 成功（`トランザクション外_プロキシを通さない呼び出しは…`） |
| P1b | サービスの `check(isActualTransactionActive())` を `check(true)` に置き換える | 開発者の報告どおり成功（リポジトリ側のガードが止める） | ✅ 101 件すべて成功。リポジトリの `requireActiveTransaction` が書き込みの前に `IllegalStateException` を投げるので、二重の防御になっている |
| P1c | P1b に加えて `requireActiveTransaction` も `check(true)` にする（ガードを両方外す） | テストが落ちる | ✅ 落ちた: `トランザクション外_…`（IllegalStateException が投げられない） |
| P1d（追加） | `requireActiveTransaction` だけを `check(true)` にする | （参考） | ⚠️ 101 件すべて成功。リポジトリ側のガード単体を確かめるテストが無い（🟡-1） |
| P2 | 3 メソッドとも `rollbackFor` を外す | 原子性のテストが落ちる | ✅ 3 件落ちた: 原子性テスト 3 本すべて |
| P3 | `lockRaid` の `findUnionRaidByIdForUpdate` を `findUnionRaidById` に置き換える | 直列化のテストが落ちる | ✅ 落ちた: `直列化_レイド行をロック中は別ユーザーの報告も待たされコミット後に完了する`（「ロック待ちに入る前に別スレッドが完了した」）。もう 1 本の直列化テストは、前回と同じく参加者の一意インデックスで待たされるので成功する |
| P4 | `cancelThreeTurnReports` と `cancelParticipation` の `@Transactional` を外す | テストが落ちる | ✅ 3 件落ちた: `外側のトランザクションなし_プロキシ経由の取り消しが成功しコミットされる`（IllegalStateException）と、取り消しの原子性テスト 2 本（「SQLException(probe) が cause に無い: IllegalStateException」。厳しくした assert が効いている） |
| P5 | 同じ 2 メソッドの `rollbackFor` だけを外す | テストが落ちる | ✅ 2 件落ちた: `原子性_最後の報告の取り消しで…`、`原子性_参加の取り消しで…`（報告の行が残っていない） |
| P6 | `cancelThreeTurnReports` の「何も消さなかったら返す」ガードを消す | テストが落ちる | ✅ 落ちた: `報告取り消し_未報告の難易度だけを指定すると報告なしの手動登録者は削除されない` |
| 安定性 | `RaidDomainServiceTransactionTest` を `--rerun-tasks` で 5 回続けて実行 | 毎回成功 | ✅ 5/5 成功（7 件ずつ） |
| 後片付け | `70_092_xxx` のギルド・レイドが DB に残っていない | 0 件 | ✅ guilds 0 件、union_raids 0 件 |

---

### 前回の指摘への対応

| 指摘 | 対応 | 確認 |
|---|---|---|
| Critical 1（P4） | `外側のトランザクションなし_プロキシ経由の取り消しが成功しコミットされる` を追加（`RaidDomainServiceTransactionTest.kt:103-123`） | ✅ P4 で落ちる |
| Critical 2（P5） | 取り消し 2 メソッドの原子性テストを追加（`:71-101`、`deleteParticipant` の spy で `SQLException("probe")`） | ✅ P5 で落ちる |
| Critical 3（assert） | `assertProbe` で cause の連鎖から `SQLException("probe")` を探す（`:188-193`）。3 本すべてで使っている | ✅ P4 で IllegalStateException を見逃さない |
| K1 | `runWhileHoldingRaidLock` を追加。専用の executor、`CountDownLatch`、`pg_stat_activity` の `wait_event_type = 'Lock'` を期限 10 秒でポーリング、`finally` で cancel・`shutdownNow`・`awaitTermination`。`future!!` を `checkNotNull` に置き換えた | ✅ |
| K2 | listener の try をサービス呼び出しだけに絞った。コミット後の応答は `runAfterCommit` でログだけ残す（`UnionRaidEventListener.kt:296-322, 329-361, 383-390`） | ✅ |
| K4 | `any(...) ?: dummyParticipant()` の意図をコメントで説明（`:58`） | ✅ |
| D1 | `repository/TransactionGuard.kt` の `requireActiveTransaction` を、6 つの書き込みメソッドの先頭で呼ぶ。KDoc も追加 | ✅ 本体コードでこれらを呼んでいるのは `RaidDomainService` だけ（grep で確認）。`RaidApiController` と `RaidNotificationScheduler` は読み取りだけなので、本番での退行は無い。既存テストの呼び出し元はクラスに `@Transactional` が付いている |
| D4 | `finishRaid` の KDoc にロックの約束とロック順を追記 | ✅ |
| `hasReported` | テストで使うので残した | 受け入れる（🟢のまま） |

---

### 🔴 Critical
- なし

### 🟡 Important（差し戻しにはしない。持ち越しまたは次の修正で対応）
1. **リポジトリ側のガード単体を確かめるテストが無い（P1d）**
   - `kotlin/src/main/kotlin/gg/nikke/raid/bot/repository/TransactionGuard.kt:11-15` の `requireActiveTransaction` を無効にしても、テストが 1 件も落ちない。今はサービスの `check` が先に止めるので実害は無い。ただ、D1 の目的は「サービスを通さずにリポジトリを呼ぶ将来のコード」を止めることなので、ガード自体が退行しても気づけない。
   - 直し方の例: クラスに `@Transactional` を付けないテスト（`RaidDomainServiceTransactionTest` に足してもよい）で、`raidParticipantRepository.deleteParticipant(…)` や `raidReportRepository.saveRaidReport(…)` などをトランザクションの外から直接呼び、`IllegalStateException` になることを確かめる。完了条件は、P1d の書き換えでそのテストが落ちること。
2. **ロック待ちの判定がデータベース全体のセッションを数えている**
   - `RaidDomainServiceTransactionTest.kt:228-231`: `pg_stat_activity` の条件が `datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'` だけなので、同じ DB を使う別のセッション（compose で動いているボット本体など）がたまたまロック待ちになっていても成立する。その場合、P3 の退行（ロックが無い状態）を見逃すおそれがある。今回の 5 回の連続実行と P3 では問題は出なかったので、可能性は低い。
   - 直し方の例: 判定を別スレッドのセッションに絞る。たとえば `query ILIKE '%union_raids%FOR UPDATE%'` で絞る、またはロックを持っている側の `pg_backend_pid()` を取っておき、`pg_blocking_pids(pid)` にそれが含まれるセッションを待つ。

### 🟢 Suggestions
- `RaidDomainServiceTransactionTest.kt:238` の `Thread.sleep(20)` は、期限付きでポーリングする間隔なので、規約（`docs/conventions/testing.md:35`。時刻に依存するテストで `sleep` を使って待たないこと）の趣旨には反しない。ただ、計画のチェックリストにある「`Thread.sleep` なし」とは文字の上で食い違うので、理由をコメントに 1 行書いておくとよい。
- `RaidDomainService.hasReported` は本体コードから呼ばれていない（テストだけで使う）。後続のタスクで使わないなら整理する。

### 📄 Documentation
- doc-sync（D1）はまだ実行されていない。`docs/codebase/backend/entities.md:10` は、今も「今は本体コードから書き込まれていない」と書いている。パイプラインの順序に従って実行すること。計画 §2 と §5 の項目（`services-map.md`・`entities.md`・`routes-map.md` の記述の修正、ロックの約束、`cancelParticipation`、`requireActiveTransaction` の存在）を入れる。

---

### まとめ
- **計画への準拠**: 本体コードは attempt 1 で確認済みの内容から変わっておらず、計画 §3 に沿っている。今回の差分は、テストの強化（3 本の追加と assert の強化）、listener の try の範囲の整理、リポジトリ側のガード、KDoc の追記。
- **規約**: listener → service → repository の向きを守っている。本体コードに `!!` は無い。SQL は Komapper の DSL だけ。`init.sql` / `migrations/` は変更なし。コミットはされておらず、ブランチは `feature/92`。秘密情報も無い。
- **良かった点**: 原子性テストの assert を cause の連鎖で厳しくしたことで、P4 の書き換えが「SQLException(probe) が cause に無い: IllegalStateException」という分かりやすいメッセージで落ちるようになった。直列化テストは時間切れ頼みをやめ、`pg_stat_activity` で実際の待機状態を見るようになった。後片付け（cancel、`shutdownNow`、`awaitTermination`）も確実に行う。リポジトリのガードを足したことで、P1b のようにサービス側の `check` を消しても書き込みの前に止まる二重の防御になった。

---

## kotlin-reviewer の結果（オーケストレーターが追記）

critical / high は 0 件。判定は APPROVED_WITH_NOTES のまま。database-reviewer は attempt 1 で確認済みで、今回の DB 関連の変更はリポジトリのガード追加だけのため、再実行していない。

| # | ラベル | 重大度 | 指摘 | 対応 |
|---|---|---|---|---|
| K6 | SHOULD | medium | `awaitLockWait` が DB 全体のロック待ちセッションを数えるため、別セッション（稼働中の bot など）の待ちで誤って通過しうる。holder の pid と `pg_blocking_pids` で絞る（`RaidDomainServiceTransactionTest.kt:225-243`）。code-reviewer 🟡2 と同じ | テストのみの修正ラウンドで対応 |
| — | 🟡 | — | `TransactionGuard` 単体を検証するテストがない（code-reviewer 🟡1、プローブ P1d） | テストのみの修正ラウンドで対応 |
| K7 | IMO | low | `insertOrUpdateParticipant` は main に呼び出し元がなく、ガードで常に ISE になる。手動登録・スコア更新は RaidDomainService にロック付きメソッドを新設して経由すること | 🟡 #91 web タスクへ申し送り |
| K3 | IMO | low | JDA イベントスレッドでのロック待ちと 3 秒の応答期限（`lock_timeout` 未設定） | 🟡 持ち越し |
| K8 | IMO | low | `Difficulty.fromValue` が try の外（自前生成の値なので実害なし） | 🟡 持ち越し |
