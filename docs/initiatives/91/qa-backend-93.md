# QA Backend — #93 シナリオ対応表

計画: `docs/initiatives/91/qa-plan-93.md`。実コードを読んで DEV テストに対応づけ、足りない分だけ QA クラスを追加した（DEV テスト・プロダクションコードは未編集）。

## 追加ファイル（`kotlin/src/test/kotlin/gg/nikke/raid/bot/` 配下）

| ファイル | 内容 |
|---|---|
| `controller/RaidApiControllerWriteQaTest.kt` | 27 テスト。実 HTTP(MockMvc)+実 DB、`@Transactional` でロールバック。ギルド ID は 70_093_501〜528、ユーザー ID は 53_000_001〜 |
| `service/DifficultyQaTest.kt` | U-07（Spring・DB 不要） |

## 対応表

| ID | 対応テスト |
|---|---|
| I-01 | DEV WriteTest「POST participants - 参加登録は201…」 |
| I-02 | DEV WriteTest「参加登録の重複は409でスコアは変わらない」 |
| I-03, I-14, I-21, I-36 | DEV WriteTest「存在しないレイドは5本とも404」 |
| I-04, I-15, I-22, I-27, I-35 | DEV WriteTest「終了済みレイドでも5本とも成功する」（＋サービス層 DEV） |
| I-05 | **QA** I-05 |
| I-06 | **QA** I-06 |
| I-07 | **QA** I-07（5 値全部）＋ DEV（-1・1.5 の一部） |
| I-08 | **QA** I-08（POST・PATCH の 0 / Int.MAX） |
| I-09 / I-10 / I-11 | **QA** I-09 / I-10 / I-11（網羅は U-03/U-04 / U-02） |
| I-12 | DEV「PATCH participants - スコア更新は200…」＋ **QA** I-12（DB の username・joinedAt・id 不変） |
| I-13 | **QA** I-13（行が作られないことを確認）＋ DEV 404 テスト |
| I-16 | **QA** I-16（5 値、既存スコア不変） |
| I-17 | **QA** I-17（他ユーザー・他レイドの同一ユーザー） |
| I-18 | DEV「DELETE participants - 参加の取り消しで参加者と報告が消える」 |
| I-19 | DEV「…報告だけある場合は報告を消して200」 |
| I-20 | DEV「未参加者のPATCHと参加者も報告も無いDELETE…は404」 |
| I-23 | **QA** I-23 |
| I-24 | DEV「POST reports - 未参加なら参加者が自動登録され…」 |
| I-25 | **QA** I-25 |
| I-26 | **QA** I-26（報告 1 行・参加者 1 行を DB で確認）＋ DEV（応答） |
| I-28 | **QA** I-28 |
| I-29 | **QA** I-29 |
| I-30 | **QA** I-30 |
| I-31 | **QA** I-31（DB からも消える）＋ DEV |
| I-32 | **QA** I-32（残りの報告が hard のみ） |
| I-33 | DEV「…未報告の難易度のDELETEは404」（既存の報告・参加者が消えないことも確認済み） |
| I-34 | **QA** I-34（`extreme`・`HARD`、報告不変）＋ DEV「パスの型不正は400」 |
| I-37 | **QA** I-37（reports・PATCH）＋ DEV（participants） |
| I-38 | **QA** I-38（participants・reports の空ボディ）＋ DEV（reports・PATCH） |
| I-39 | **QA** I-39（reports・PATCH）＋ DEV（participants） |
| I-40 | **QA** I-40（5 本の raidId=abc と userId=abc / 0）＋ DEV |
| I-41 | DEV `InternalApiTokenFilterIntegrationTest`「トークン無しの書き込みリクエストは401」 |
| I-42 | **QA** I-42（フィルター付き MockMvc で値違いトークンの POST / DELETE が 401 かつ何も書かれない。正しいトークンでは通る） |
| I-43 | **QA** I-43（4 種の不正ボディ＋壊れた JSON。応答に送った値が含まれない） |
| I-44, I-45 | DEV `RaidDomainServiceTransactionTest` |
| I-46, I-47 | DEV `RaidParticipantRepositoryIntegrationTest`（1 件取得・スコア更新の 2 テスト） |
| U-01〜U-06 | DEV `RaidWriteRequestsTest` |
| U-07 | **QA** `DifficultyQaTest`（DEV に直接のテスト無し） |
| G-01 | DEV `RaidDomainServiceTest`「3凸報告_終了済みレイドはRaidAlreadyFinished…」ほか、既存の 3凸報告・取り消しテスト群（`allowFinished` 既定 false のまま） |
| G-02 | **QA** G-02（書き込み後の `GET /api/raids/{id}` に反映・削除後は空）＋ 既存 `RaidApiControllerTest` |
| G-03 | DEV `InternalApiTokenFilterIntegrationTest` / `InternalApiTokenFilterTest` |
| G-04 | DEV 差分の `UnionRaidEventListenerQaTest`（今回の QA 実行対象外。G-05 で確認） |
| G-05 | 全体 `./gradlew test` は未実行（指示により新規クラスのみ）。要全体実行 |
| M-01, M-02 | 手動（自動化対象外） |

## 計画とコードの乖離（コードを正として記述）

- I-42: DEV の MockMvc はフィルターを通さない。QA はフィルターを `addFilters` した別の MockMvc を使い、トークン値は `src/test/resources/application.properties` の `test-internal-token`。
- I-07 の `null`: `{"score":null}` は欠落と同じ 400（`isIntegralNumber` が偽）。計画どおり。
- I-33 / I-20 / I-34 のエラー本文: 404 は `error=NOT_FOUND`、409 は `CONFLICT`、400 は `BAD_REQUEST`。400 の `message` は固定文（項目名のみ）。ただし `handleTypeMismatch`（パス型不正）はログに `e.value`（不正な入力値）を出す（応答本文には出ない）。**POTENTIAL BUG（軽微）**: ログに入力値が残る。I-43 は計画どおり「応答本文だけ」を確認した。
- 計画 I-17 / I-23 は DEV コントローラーテストに無く、QA で補完。サービス層・リポジトリ層では DEV に同等テストがある。
- 検出した不具合・実装との矛盾はなし。

## 実行結果

`set -a && . ./.env && set +a && cd kotlin && ./gradlew test --tests '*RaidApiControllerWriteQaTest' --tests '*DifficultyQaTest'`
→ BUILD SUCCESSFUL。`RaidApiControllerWriteQaTest` 27 件、`DifficultyQaTest` 1 件、失敗 0・スキップ 0。
