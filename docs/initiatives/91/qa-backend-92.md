# QA Backend — #92 シナリオ対応表

計画: `docs/initiatives/91/qa-plan-92.md`。実コードを読んで DEV テストに対応づけ、足りない分だけ QA クラスを追加した（DEV テストは未編集、プロダクションコードも未編集）。

## 追加ファイル（すべて `kotlin/src/test/kotlin/gg/nikke/raid/bot/` 配下）

| ファイル | 内容 |
|---|---|
| `service/RaidDomainServiceQaTest.kt` | I-02(joinedAt)・I-03・I-04・I-06・I-12・I-15 の補完（実 DB、`@Transactional` でロールバック） |
| `service/RaidDomainServiceConcurrencyQaTest.kt` | I-23（実コミット、クラスに `@Transactional` なし、ギルド削除のカスケードで後片付け） |
| `bot/UnionRaidEventListenerQaTest.kt` | U-01〜U-05（Mockito。DB・Discord 不要） |
| `controller/RaidApiControllerQaTest.kt` | G-03 |

実行結果: 4 クラス 14 テストすべて PASSED（`./gradlew test --tests` で新規クラスのみ実行）。

## 対応表

| ID | 対応テスト |
|---|---|
| I-01 | DEV `RaidDomainServiceTest` 「3凸報告_未参加なら参加者にスコア0と報告者名で登録される」 |
| I-02 | DEV 「3凸報告_手動登録済みの参加者はユーザー名とスコアが上書きされない」＋ QA 「I-02 …joinedAtも上書きされず…」（joinedAt・報告保存を補完） |
| I-03 | DEV 「同一ユーザー同一難易度の報告はupsertされ…」「…alreadyReportedがtrue」＋ QA I-03（1 行のまま時刻更新を確認） |
| I-04 | DEV 「2つ目の難易度でも参加者は1行のまま」＋ QA I-04（報告 2 行を確認） |
| I-05 | DEV 「3凸報告_終了済みレイドはRaidAlreadyFinishedエラーになり報告は保存されない」（参加者 0 も確認済み） |
| I-06 | DEV 「3凸報告_存在しないレイドはRaidNotFound」＋ QA I-06（報告・参加者が作られないことを確認） |
| I-07 | DEV 「報告取り消し_最後の報告を取り消すと参加者も削除される」 |
| I-08 | DEV 「報告取り消し_報告が残っていれば参加者は残る」 |
| I-09 | DEV 「報告取り消し_手動登録でスコア入力済みでも報告0件で参加者が削除される」 |
| I-10 | DEV 「報告取り消し_未報告の難易度だけを指定すると報告なしの手動登録者は削除されない」 |
| I-11 | DEV 「報告取り消し_参加者の行が無い既存の報告でも取り消せる」 |
| I-12 | DEV 「報告取り消し_終了済みレイドは参加者も報告も残る」（DEV に既存）＋ QA I-12（重複気味。ユーザー別に両方の件数を確認） |
| I-13 | DEV 「報告取り消し_難易度が空だとNoDifficultySelectedエラー」 |
| I-14 | DEV 「参加取り消し_参加者と全難易度の報告がまとめて削除される」 |
| I-15 | DEV 「参加取り消し_他ユーザーの参加者と報告は残る」（同一レイド他ユーザーのみ）＋ QA I-15 ×2（他レイドの同一ユーザー、他ユーザーの報告行を補完） |
| I-16 | DEV 「参加取り消し_報告だけで参加者の行が無くても報告は削除される」 |
| I-17 | DEV 「参加取り消し_参加者でも報告者でもなければ成功し件数は0」 |
| I-18 | DEV 「参加取り消し_存在しないレイドはRaidNotFound」 |
| I-19 | DEV 「参加取り消し_終了済みレイドでも削除できる」 |
| I-20 | DEV `RaidDomainServiceTransactionTest` 「原子性_参加者の登録で例外が起きると報告もロールバックされる」 |
| I-21 | DEV Transaction 「直列化_最後の報告の取り消しがロック中なら別難易度の報告は待たされコミット後に両立する」（＋別ユーザー版） |
| I-22 | DEV Transaction 「トランザクション外_プロキシを通さない呼び出しは…」（3 メソッドすべて IllegalStateException、行増減なしを確認）、「外側のトランザクションなし_プロキシ経由の取り消しが成功しコミットされる」（取り消し 2 メソッド）、「…ガード付きリポジトリの書き込みを直接呼ぶと…」 → 計画の想定（取り消し 2 メソッドの自動テストが無い）は古い。**追加不要** |
| I-23 | **QA** `RaidDomainServiceConcurrencyQaTest`（新規追加） |
| I-24 | DEV Transaction 「原子性_参加の取り消しで参加者の削除が失敗すると報告の削除もロールバックされる」（＋報告取り消し版） |
| I-25 | DEV `RaidParticipantRepositoryIntegrationTest` 「参加者の条件付き登録_新規は1を返し…」「…既存の参加者がいると0を返し…」 |
| I-26 | DEV 同 「参加者の削除_対象のユーザーだけ消え他ユーザーと他レイドの同じユーザーは残る」 |
| I-27 | DEV `RaidReportRepositoryIntegrationTest` 「ユーザーの報告を全削除_両難易度が消え他ユーザーの行は残り件数を返す」 |
| I-28 | DEV `UnionRaidRepositoryIntegrationTest` 「ロック付き取得_あればレイドを返し無ければnullを返す」 |
| U-01〜U-05 | **QA** `UnionRaidEventListenerQaTest`（新規。手動に回さず自動化できた） |
| G-01 | 既存 `RaidDomainServiceTest` の 3凸報告・取り消し・報告済み判定テスト群（戻り値型変更後も DEV で通っている）。今回は新規クラスのみ実行のため全体実行は未実施 |
| G-02 | DEV `UnionRaidRepositoryIntegrationTest`（レイド終了・notifyTime クリア・通知送信成功記録・終了後の notifyMessageId 保持）＋ `RaidDomainServiceTest`（レイド終了系）。デッドロック有無は I-23（finishRaid ↔ reportThreeTurn のロック順）で確認 |
| G-03 | **QA** `RaidApiControllerQaTest`（既存 `RaidApiControllerTest` は DEV で追加されたセットを拡張せず、別クラスで報告→participants を確認） |
| G-04 | 全体 `./gradlew test` は未実行（指示により新規クラスのみ）。新規の実コミットテスト（I-23）はギルド削除で後片付け。要全体実行 |

## 計画とコードの乖離（コードを正として記述）

- I-22: 計画は「DEV は 3凸報告だけ」としていたが、実際は 3 メソッド全部の no-proxy テストがあり、取り消し 2 メソッドのプロキシ経由・外側 Tx なしテストもある。
- finishRaid は `lockRaid` を取らない（単一 UPDATE のみ。KDoc に記載）。I-23 は UPDATE の行ロックにより報告の `FOR UPDATE` が待たされる形で成立する。
- 計画 I-15 は「他レイドの同じユーザー」を求めていたが DEV は同一レイド内の別ユーザーのみ → QA で補完。
- U-01〜U-05 の文言は計画では「〜系」。実装の文言をそのまま検証した。リスナーはボタン（`onButtonInteraction`）ではなく選択メニュー（`onStringSelectInteraction`、`handleDifficultySelect`/`handleCancelSelect`）で報告・取り消しを処理する。
- U-05: `reportThreeTurn` / `cancelThreeTurnReports` の例外は try/catch で汎用文に置換される。`Result.failure` 側（RaidNotFound 等）は `raidErrorMessage` 経由で `error.message` を含む文になりうる（RaidDomainError の message は固定文で、内部情報は含まない想定）。
- リスナーのテスト方式: `RaidDomainService` の公開メソッドは `Result` を返すため Mockito の `when` でスタブできず（value class）、リポジトリをモックにした匿名サブクラスで差し替えた（kotlin-spring により open）。

## 疑わしいバグ

なし。補足の観察のみ:
- `UnionRaidEventListener.handleReportCancelButton` は `ensureRaidActive` → `findReportedDifficulties` を Tx 外で行うが、書き込みは行わず `cancelThreeTurnReports` 側で再判定されるため問題なし。
- `handleDifficultySelect` で `Difficulty.fromValue(...)` が `try` の外にある（未知の value で NoSuchElementException）。通常は選択肢が固定なので到達しにくいが、`handleCancelSelect` も同様。低優先度。
