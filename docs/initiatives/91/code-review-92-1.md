## Code Review — #92 — attempt 1 — 2026-11-23

### 判定: ❌ REJECTED（scope: backend）

本体コードは計画どおりで、問題は見つからなかった。差し戻しの理由はガードテストの穴だけ。
レビュー側でプローブを追加して確かめたところ、`cancelThreeTurnReports` / `cancelParticipation` の `@Transactional` を外しても、`rollbackFor` を外しても、テストが 1 件も落ちない。
#109 / PR #127 と同じ「ガードのすり抜け」に当たるため、DEV review hardening の方針に従って差し戻す。修正はテストの追加だけで済む。

---

### ガードのプローブ（レビュー側で再実行）

`kotlin/` をスクラッチ領域にコピーし、そこで書き換えて実行した。作業ツリーのファイルは変更していない（書き換えたファイルは毎回元に戻し、`diff` で一致を確認済み）。DB は compose の `nikke_unionraid_db`。

| # | プローブ | 期待 | 結果 |
|---|---|---|---|
| 基準 | 変更なしで `service.*` と `repository.*` を実行 | 全件成功 | ✅ 成功（RaidDomainServiceTest 46、TransactionTest 4、repository 系すべて成功。スキップ 1 件は既存の `@Disabled`（#54）） |
| P1 | プロキシを通さずに 3 メソッドを呼ぶ → `IllegalStateException`、行は増減しない | 成功 | ✅ 成功（`トランザクション外_...` のテストが 3 メソッドすべてを確認している） |
| P1b | `check(isActualTransactionActive())` を `check(true)` に置き換える | テストが落ちる | ✅ 落ちた: `トランザクション外_プロキシを通さない呼び出しはIllegalStateExceptionで止まり行は増減しない`（IllegalStateException が投げられない） |
| P2 | 3 メソッドとも `rollbackFor` を外す | 原子性のテストが落ちる | ✅ 落ちた: `原子性_参加者の登録で例外が起きると報告もロールバックされる`（報告の行が残る） |
| P3 | `lockRaid` の `findUnionRaidByIdForUpdate` を `findUnionRaidById` に置き換える | 直列化のテストが落ちる | ✅ 落ちた: `直列化_レイド行をロック中は別ユーザーの報告も待たされコミット後に完了する`（TimeoutException が投げられない）。計画にあった元のテスト（`直列化_最後の報告の取り消しが…`）は、開発者の報告どおりロックが無くても成功する。参加者の一意インデックスで待たされるため。ロックを守っているのは追加された方のテスト |
| P4（追加） | `cancelThreeTurnReports` と `cancelParticipation` の `@Transactional` を外す | テストが落ちる | ❌ **落ちない**（テスト全体を実行しても全件成功）。本番では Discord の取り消しメニューが毎回 `IllegalStateException` になり、汎用エラーしか出なくなる |
| P5（追加） | `cancelThreeTurnReports` と `cancelParticipation` の `rollbackFor` だけを外す | テストが落ちる | ❌ **落ちない**（service 配下を実行して全件成功）。SQLException が起きると、報告の削除だけがコミットされて参加者が残りうる |
| P6（追加） | `cancelThreeTurnReports` の「何も消さなかったら返す」ガードを消す | テストが落ちる | ✅ 落ちた: `報告取り消し_未報告の難易度だけを指定すると報告なしの手動登録者は削除されない` |
| 後片付け | `70_092_xxx` のギルド・レイドが DB に残っていない | 0 件 | ✅ guilds 0 件、union_raids 0 件 |

---

### 🔴 Critical

1. **2 つの取り消しメソッドで、トランザクション境界の退行を検出できない（P4）**
   - `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/RaidDomainServiceTransactionTest.kt:72` と `:98`: `cancelThreeTurnReports` / `cancelParticipation` を呼んでいるのが、どちらも `TransactionTemplate` の中だけ。このため外側のトランザクションに参加してしまい、メソッド自身の `@Transactional` が無くても成功する。`RaidDomainServiceTest` もクラスに `@Transactional` が付いているので同じ。
   - 直し方: `RaidDomainServiceTransactionTest` に、外側のトランザクションを持たずに **Spring Bean（プロキシ）経由で** `cancelThreeTurnReports` と `cancelParticipation` を呼ぶテストを足す。成功し、結果がコミットされている（報告・参加者が消えている）ことを確かめる。完了条件は、P4 の書き換えでこのテストが落ちること。

2. **2 つの取り消しメソッドで、ロールバックの退行を検出できない（P5）**
   - 原子性のテスト（`RaidDomainServiceTransactionTest.kt:49-62`）があるのは `reportThreeTurn` だけ。
   - 直し方: 同じ形（`@MockitoSpyBean` の `raidParticipantRepository.deleteParticipant` で `doAnswer { throw java.sql.SQLException("probe") }`）で次の 2 本を足す。
     - `cancelThreeTurnReports`（最後の報告を取り消す）→ 例外の後も **報告の行が残っている** こと
     - `cancelParticipation` → 例外の後も **報告の行が残っている** こと
   - 完了条件は、P5 の書き換えで 2 本とも落ちること。

3. **原子性テストが期待していない例外でも成功してしまう（1・2 の前提）**
   - `RaidDomainServiceTransactionTest.kt:57`: `assertThrows(Exception::class.java)` だと、`@Transactional` が無いときの `IllegalStateException`（書き込む前に止まるので報告も残らない）でも成功する。2 の新しいテストでも同じ問題が起き、P4 を見逃す。
   - 直し方: 投げられた例外が `SQLException("probe")` であること（Spring のプロキシで包まれた場合は cause をたどって）を確かめる。既存の 1 本と新しい 2 本の全部に入れる。

### 🟡 Important
- なし

### 🟢 Suggestions（直さなくても差し戻しにはしない）
- `UnionRaidEventListener.kt:296-318` / `:325-352`: `try` の範囲が `replaceSelectMenu` と `scheduler.updateRaidMessage` まで含んでいる。`updateRaidMessage` は中で例外を処理するので今は害がない。ただ、コミット後の処理で例外が起きると「エラーが発生しました」と表示されるのに報告は保存されている、という食い違いが起きうる。範囲をサービス呼び出しだけに絞るとわかりやすい。
- `RaidDomainService.hasReported` は本体コードから呼ばれなくなった（テストだけで使われている）。後続で使わないなら、削除するかテスト用の補助関数に移す。
- `RaidDomainServiceTransactionTest.kt:83`、`:107`: `future!!`。`lateinit` や `checkNotNull` に置き換えられる（規約の `!!` 禁止は本体コードが対象だが、そろえておくとよい）。

### 📄 Documentation
- doc-sync（D1）はまだ実行されていない。`docs/codebase/backend/services-map.md:32` と `entities.md:10` は、今も「本体コードから書き込まれていない」と書いている。パイプラインの順序（self-test の後）に従って実行すること。計画 §2 の項目（ロックの約束、`cancelParticipation`、`routes-map.md` の追記）を必ず入れる。

---

### まとめ
- **計画への準拠**: §3.1〜3.6 をほぼそのまま実装している。具体的には、3 メソッドへの `@Transactional(rollbackFor = [Exception::class])`、ロックした行での終了判定、すべての `Result.failure` が最初の書き込みより前にあること、`isActualTransactionActive` の `check`、分離レベルを変えていないこと、`onDuplicateKeyIgnore(raidId, userId)` で既存の参加者を上書きしないこと、`joinedAt = now` を渡していること、何も消さなかったときは参加者を消さないこと、`cancelParticipation` で終了済みを判定しないことと KDoc の注意書き。listener は `hasReported` の事前呼び出しをやめ、`alreadyReported` と `participantRemoved` の文言、`event.user.idLong`、汎用エラー文、`raidId` だけのログになっている。
- **規約**: 依存の向き（listener → service → repository）を守っている。listener からリポジトリの書き込みを呼んでいない。SQL は Komapper の DSL だけ。`init.sql` / `migrations/` は変更なし。コミットはされておらず、ブランチは `feature/92`。秘密情報も無い。
- **良かった点**: 開発者がプローブで、計画の直列化テストではロックを守れていないことに気づき、別ユーザーで待たされることを確かめるテストを足した。P3 で再現を確認済み。結果型で `alreadyReported` をロックの中で判定するようにしたことで、ロックの外で読む不正確な事前読み取りも無くなった。サービスのテストは計画の B4 の項目をすべて満たしている。
- **差し戻しの範囲**: `RaidDomainServiceTransactionTest.kt` にテストを 3 本足し、assert を 1 か所厳しくするだけ。本体コードは変えなくてよい。

---

## スタックレビュアーの結果（オーケストレーターが追記）

`kotlin-reviewer` と `database-reviewer` を未コミット差分に対して並行で実行した。どちらも critical / high は 0 件。

| # | ラベル | 重大度 | 指摘 | 出典 | 対応 |
|---|---|---|---|---|---|
| K1 | SHOULD | medium | 直列化テストの「ロック待ち」判定が `started.get(500ms)` のタイムアウト頼み。失敗時に非同期スレッドが後片付けと競合する（`RaidDomainServiceTransactionTest.kt:62-70, 87-95`） | kotlin | 修正ラウンドで対応 |
| K2 | SHOULD | medium | try の範囲にコミット後の `replaceSelectMenu` / `updateRaidMessage` が入り、成功済みでも「エラーが発生しました」や二重応答になりうる（`UnionRaidEventListener.kt:296-318, 325-352`）。code-reviewer の提案と同じ | kotlin / code-reviewer | 修正ラウンドで対応 |
| D1 | SHOULD | low〜medium | リポジトリの書き込みメソッドが public で、レイド行ロックを通さずに呼べる（規約頼み） | database | 修正ラウンドで対応 |
| K3 | IMO | low | JDA イベントスレッドがロック待ちでブロック。`lock_timeout` 未設定 | kotlin | 🟡 持ち越し |
| K4 | IMO | low | `any(...) ?: dummyParticipant()` の意図が分かりにくい | kotlin | 修正ラウンドで対応（コメントか mockito-kotlin） |
| K5 | IMO | low | `cancelParticipation` の呼び出し元がまだない。BFF で本人・特権・終了済みの確認が必要 | kotlin | 🟡 #91 web タスクへ申し送り |
| D2 | IMO | low | `is_3t=0` の行の扱いが報告済み判定と取り消しで揃っていない | database | 🟡 持ち越し |
| D3 | IMO | low | 既存の報告データのバックフィルなし（issue で「移行しない」と決定済み） | database | 🟡 持ち越し |
| D4 | IMO | low | `finishRaid` の KDoc にロックの約束を書く | database | 修正ラウンドで対応 |
| D5 | IMO | low | 冗長な `idx_raid_id`、`saveRaidReport` の KDoc 不一致（差分外） | database | 🟡 持ち越し |
