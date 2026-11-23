# QA Report — #93 — 2026-11-23 — nikke-unionraid-bot

## Environment
- Branch: feature/93 (+1 / -0 vs origin/develop。リベース不要)
- Backend http://localhost:8080 → HTTP 404（稼働確認のみ。ルート `/` は未定義のため 404 は想定内。実テストはプロセス内 Spring Test + 共有 DB）· Frontend http://localhost:3000 → HTTP 200 · Smoke: 実行対象外（`ensure_up: true` だが、テストは in-process で共有 DB に接続するため個別の smoke は実施せず。全体スイートの実行を smoke に代えた）
- Docker: 既存コンテナ（別ワークツリーから起動）を利用。`docker compose up/down/restart/build` は実行していない。PHASE 4 のティアダウンは実施しない。
- 対象範囲: backend のみ。`web/` は計画どおり（E2E シナリオ 0 件）`docker.run_frontend`（Playwright）は**スキップ**。
- 実行コマンド: `set -a && . ./.env && set +a && cd kotlin && ./gradlew test`（全件）

## Metrics — Total duration: 25s（Gradle `BUILD FAILED in 24s`、ラッパー込み 25s）
- テスト開始 epoch 1795419821 → 終了 1795419846
- 計画フェーズ（QA ドキュメント読込 + 設定検証 + 稼働確認）は別途。テスト実行時間のみ計測

## Results per scenario (QA 固有)

| Suite | ID | Scenario | Status | Duration |
|---|---|---|---|---|
| backend（#93 QA） | I-01〜I-47, G-02, I-42 他 | `RaidApiControllerWriteQaTest` 27 件 | ✅ 27 / 27 | 同一クラス内（合計 Gradle 25s に含む） |
| backend（#93 QA） | U-07 | `DifficultyQaTest` 1 件 | ✅ 1 / 1 | — |
| backend（#92 QA、G-05 回帰） | I-02 | `RaidDomainServiceQaTest` 「手動登録済みの参加者はjoinedAtも上書きされず報告は保存される」 | ❌ FAILED | — |
| backend（#92 QA、G-05 回帰） | G-04 | `UnionRaidEventListenerQaTest` 5 件 | ✅ 5 / 5 | — |
| backend（既存 DEV） | I-01〜I-47 他 | `RaidApiControllerWriteTest` 15 件、`RaidWriteRequestsTest` 23 件 | ✅ 38 / 38 | — |
| backend（既存） | G-01〜G-03 他 | 既存スイート（残り） | ✅ | — |
| frontend | E-XX | 対象外（計画どおり 0 件）。Playwright 実行なし | ⏭ | — |

## Summary

| Suite | Total | ✅ | ❌ | ⏭ |
|---|---|---|---|---|
| backend 全体（`./gradlew test`） | 286 | 285 | 1 | 1 |
| backend QA クラス（#93 の 2 クラス） | 28 | 28 | 0 | 0 |
| backend QA クラス（#92 の QA クラスを含む全 QA 系） | 58 | 57 | 1 | 0 |
| frontend | 0 | — | — | — |

- ⏭ 1 件は既存の `@Disabled` テスト（`RaidParticipantRepositoryIntegrationTest` 「同じraidIdとuserIdの参加者はupsertされ1行のまま」、理由は Issue #54 の JDBC タイムゾーン問題）。今回の変更とは無関係。

## Detailed failures

### ❌ RaidDomainServiceQaTest > I-02 手動登録済みの参加者はjoinedAtも上書きされず報告は保存される
- **Message**: `expected: <2026-11-22T07:44:04.229Z> but was: <2026-11-22T07:44:04.230Z>`
- **Location**: `kotlin/src/test/kotlin/gg/nikke/raid/bot/service/RaidDomainServiceQaTest.kt:46`（`assertEquals(manualJoinedAt..., p.joinedAt...)`）
- **Test code**: 参加者を `joinedAt = OffsetDateTime.now().minusDays(1)` で登録し、`reportThreeTurn` 後に DB から読んだ `joinedAt` が上書きされていないことを確認。比較は `truncatedTo(MILLIS)` 済み。
- **Hypothesis**: 1 ミリ秒の差。`now()` のナノ秒〜マイクロ秒の端数が DB（PostgreSQL の timestamp はマイクロ秒精度）への往復で丸められ、ミリ秒に切り詰める前後で値が 1ms ずれたと見られる。機能の不具合ではなく、テストの時刻比較（精度の扱い）の問題の可能性が高い。ただし未検証。`joinedAt` が本当に上書きされたわけではない（値の差は 1ms のみで、`reportThreeTurn` の時刻 `now()` とは一致しない）。
  - 注: 同種の時刻ずれ問題で既存テストが `@Disabled`（Issue #54）になっているため、同系統の不安定さの可能性がある。
- **Evidence**: `docs/initiatives/91/evidence/2026-11-23_16-43-41/TEST-gg.nikke.raid.bot.service.RaidDomainServiceQaTest.xml`、`gradle.log`
- **Regression judgment**: #93 の変更範囲（`RaidDomainService` の 3T 報告）に隣接する。#92 の QA テスト（commit 33e5b61）で、今回の差分では変更されていない。#93 起因かどうかは develop 側との比較が必要で、今回は未確認（stash・checkout 禁止のため）。

## Evidence
- 🎬 videos/: 0（backend のみのため対象なし）
- 📸 screenshots/: 0（同上）
- バックエンド結果: `docs/initiatives/91/evidence/2026-11-23_16-43-41/`（JUnit XML 23 ファイル + `gradle.log`）

## Pending manual checks（自動化不可）
- M-01: ✅ **実施済み（2026-11-23 16:52 JST）**。結果は下の「M-01 の実施結果」を参照。
- M-02: M-01 の後に Discord ボタンで報告し、Embed が DB から作り直され Web の変更が反映されることを確認（仕様上の副作用）

## M-01 の実施結果（手動）

- **起動したもの**: `feature/93` のコードを、`./gradlew bootRun --args='--server.port=18093 --discord.bot.enabled=false'` で起動した。DB は compose の共有 DB を使った。
  - compose の bot コンテナは別の worktree で古いイメージから作られたもので、新しいエンドポイントを持っていない。
  - そのコンテナは止めずにおいた。同じトークンで Discord に二重接続しないよう、こちらは JDA を無効にして起動した。
- **対象のレイド**: 実ギルドで進行中のレイド 2429。Embed メッセージがあり、参加者・報告ともに 0 件の状態から始めた。テスト用のユーザー ID は `93999001`。
- **呼び出した API（すべてトークン付き）**

  | # | 呼び出し | 結果 |
  |---|---|---|
  | 1 | POST `/participants`（score 100） | 201 |
  | 2 | PATCH `/participants/{userId}`（score 200） | 200 |
  | 3 | POST `/reports`（normal） | 200、`participantRegistered=false` |
  | 4 | POST `/reports`（hard） | 200、`participantRegistered=false` |
  | 5 | DELETE `/reports/{userId}/hard` | 200、`participantRemoved=false` |
  | 6 | DELETE `/participants/{userId}`（後片付けを兼ねる） | 200、`removedReportCount=1` |

- **書き込み途中の DB**: 参加者が 1 行（score 200）、報告が normal の 1 行で、期待どおりだった。
- **Embed の確認**: Discord API でメッセージを取得し、書き込みの前、5 本目の後、6 本目の後の 3 回比べた。
  - `edited_timestamp` は 3 回とも `2026-11-23T07:05:45.697Z` で変わっていない。
  - 3凸 の欄も「なし」のままだった。
  - bootRun のログにも Discord や JDA への言及はない。
- **後片付け**: レイド 2429 の参加者・報告は 0 / 0 に戻した。bootRun は停止した。
- **注意**: 今回は JDA を無効にして起動したので、「API が JDA を通じて Embed を更新しない」ことはコードレビュー（コントローラーが Scheduler や JDA に依存していない）とあわせた確認になる。JDA が有効な状態での確認は、このブランチの bot をデプロイしたあとに M-02 と一緒に行う。

## Verdict: ✅ PASSED（判断して通過扱い、2026-11-23）

- 今回の QA 対象（#93 の 2 クラス、28 件）は、すべて通過した。M-01 も実施済み（上の結果を参照）。
- 全体スイートでは、#92 の QA テスト `RaidDomainServiceQaTest` の I-02 が 1 件失敗した（時刻が 1ms ずれた）。
  - 原因はテスト側にある。Java の時刻はナノ秒精度だが、PostgreSQL はマイクロ秒に丸めて保存する。テストは両方をミリ秒で切り捨てて比べるため、ミリ秒の境目の直前に当たると結果が食い違う。
  - #93 による退行ではない。同じクラスを 3 回続けて実行し、3 回とも通過した。
  - このテストは子 Bug #138 で直す。
- 人間の判断により、#93 は QA 通過として扱う（`done` に移し、`approved` を付ける）。
- 残る確認: M-02 は、このブランチの bot をデプロイしたあとに行う。
