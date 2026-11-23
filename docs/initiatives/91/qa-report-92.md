# QA Report — #92 — 2026-11-23 — nikke-unionraid-bot

## 環境
- ブランチ: `feature/92`（PR #134、develop 比 +1 / -0）
- Backend `http://localhost:8080`: 到達可（`/health` 200、`/api/guilds` トークンなし 401）
- Frontend `http://localhost:3000`: 到達可（200）。ただし対象外（後述）
- DB `nikke_unionraid_db`: healthy
- Smoke: 実施せず（`smoke_script` 未定義。API 到達と認証応答の確認で代替）
- 注意: compose スタックは develop 系のコードで動いている（#92 のコードは入っていない）。結合テストは Gradle が DB に接続して実行したもので、稼働中の bot の挙動は確認していない
- 実行コマンド: `set -a && . ./.env && set +a && cd kotlin && ./gradlew test --rerun-tasks`
- 証跡: `docs/initiatives/91/evidence/2026-11-23_15-55-51/`（`backend-test.log`、`junit-xml/`）

## Metrics
- 合計時間（Gradle 実行）: 約 26s（15:55:51〜15:56:17）。JUnit の個別テスト時間の合計は 5.38s
- 対象の差分: Frontend は変更なしのため E2E・`web/` テストは未実行

## 結果（#92 シナリオ別）

| Suite | ID | シナリオ（要約） | 結果 | テスト |
|---|---|---|---|---|
| Int | I-01 | 未参加の人が 3凸報告 → スコア0・報告者名で自動登録 | ✅ | RaidDomainServiceTest 3凸報告_未参加なら… / 3凸報告_新規登録される |
| Int | I-02 | 手動登録済みの参加者の名前・スコア・参加日時が上書きされない | ✅ | DEV 3凸報告_手動登録済み… / QA RaidDomainServiceQaTest I-02 |
| Int | I-03 | 同じ難易度の再報告は 1 行のまま | ✅ | DEV 同一ユーザー同一難易度… / QA I-03 |
| Int | I-04 | 別難易度の報告は 2 行、参加者は 1 行 | ✅ | DEV 2つ目の難易度… / QA I-04 |
| Int | I-05 | 終了済みレイドへの報告は失敗し何も作られない | ✅ | DEV 3凸報告_終了済みレイド… |
| Int | I-06 | 存在しないレイドへの報告は何も作られない | ✅ | DEV 存在しないレイド… / QA I-06 |
| Int | I-07 | 最後の報告を取り消すと参加者も削除 | ✅ | DEV 報告取り消し_最後の報告… |
| Int | I-08 | 報告が残れば参加者は残る | ✅ | DEV 報告取り消し_報告が残っていれば… |
| Int | I-09 | 手動登録でスコア入力済みでも報告 0 件で参加者を削除 | ✅ | DEV 報告取り消し_手動登録でスコア入力済み… |
| Int | I-10 | 未報告の難易度だけ指定しても報告なしの手動登録者は消えない | ✅ | DEV 報告取り消し_未報告の難易度だけ… |
| Int | I-11 | 参加者の行が無い既存の報告も取り消せる | ✅ | DEV 報告取り消し_参加者の行が無い既存… |
| Int | I-12 | 終了済みレイドの取り消しは失敗し参加者と報告が残る | ✅ | DEV 報告取り消し_終了済みレイド… / QA I-12 |
| Int | I-13 | 難易度未選択の取り消しは NoDifficultySelected | ✅ | DEV 報告取り消し_難易度が空だと… |
| Int | I-14 | 参加取り消しで参加者と全報告がまとめて削除 | ✅ | DEV 参加取り消し_参加者と全難易度の報告… |
| Int | I-15 | 他ユーザー・他レイドの参加者と報告は残る | ✅ | DEV 参加取り消し_他ユーザー… / QA I-15 ×2 |
| Int | I-16 | 報告だけあり参加者行が無い人の参加取り消しで報告は削除 | ✅ | DEV 参加取り消し_報告だけで… |
| Int | I-17 | 参加者でも報告者でもない人の取り消しは成功・件数 0 | ✅ | DEV 参加取り消し_参加者でも報告者でもなければ… |
| Int | I-18 | 存在しないレイドの参加取り消しは RaidNotFound | ✅ | DEV 参加取り消し_存在しないレイド… |
| Int | I-19 | 終了済みレイドでも参加取り消しができる | ✅ | DEV 参加取り消し_終了済みレイド… |
| Int | I-20 | 3凸報告の途中で例外 → 報告もロールバック | ✅ | DEV Transaction 原子性_参加者の登録で例外… |
| Int | I-21 | 最後の報告の取り消しと別難易度の報告が直列化される | ✅ | DEV Transaction 直列化_最後の報告の取り消し… |
| Int | I-22 | トランザクション外の呼び出しは止まり行は変わらない | ✅ | DEV Transaction トランザクション外_×2、外側のトランザクションなし_… |
| Int | I-23 | 報告とレイド終了が同時 → 終了が先ならどちらも残らない | ✅ | QA RaidDomainServiceConcurrencyQaTest（実コミット） |
| Int | I-24 | 参加取り消しの途中で例外 → 報告もロールバック | ✅ | DEV Transaction 原子性_参加の取り消し… / 最後の報告の取り消し… |
| Int | I-25 | 参加者の条件付き登録（新規 1 / 既存 0） | ✅ | DEV RaidParticipantRepositoryIntegrationTest 条件付き登録 ×2 |
| Int | I-26 | 参加者の削除は対象ユーザーだけ | ✅ | DEV RaidParticipantRepositoryIntegrationTest 参加者の削除_… |
| Int | I-27 | 報告の一括削除（両難易度・他ユーザーは残る） | ✅ | DEV RaidReportRepositoryIntegrationTest ユーザーの報告を全削除_… |
| Int | I-28 | レイドのロック付き取得（あれば返す / 無ければ null） | ✅ | DEV UnionRaidRepositoryIntegrationTest ロック付き取得_… |
| Unit | U-01 | 新規の報告の応答 | ✅ | QA UnionRaidEventListenerQaTest |
| Unit | U-02 | 同じ難易度の再報告の応答 | ✅ | QA UnionRaidEventListenerQaTest |
| Unit | U-03 | 取り消しで参加者から外れた旨の応答 | ✅ | QA UnionRaidEventListenerQaTest |
| Unit | U-04 | 報告が残る取り消しでは外れた旨を出さない | ✅ | QA UnionRaidEventListenerQaTest |
| Unit | U-05 | 想定外の例外は汎用文に置換、例外メッセージは出ない | ✅ | QA UnionRaidEventListenerQaTest |
| E2E | E-xx | — | 対象外 | `web/` の変更なし（0 シナリオ） |
| Reg | G-01 | 既存の 3凸報告・取り消し・報告済み判定が戻り値変更後も通る | ✅ | DEV RaidDomainServiceTest 該当群 |
| Reg | G-02 | レイド終了など `union_raids` を更新する既存処理 | ✅ | DEV UnionRaidRepositoryIntegrationTest / RaidDomainServiceTest レイド終了系 |
| Reg | G-03 | `GET /api/raids/{raidId}` の形、3凸報告した人が participants に出る | ✅ | QA RaidApiControllerQaTest G-03 |
| Reg | G-04 | backend 既存テスト全体で失敗 0・テストデータが残らない | ❌ | 全体: 204 件中 2 件失敗（下記）。残存データは無し |
| Manual | M-01 | Discord ボタンで 3T 報告 → participants に自分（スコア 0） | ⏳ 手動確認待ち | — |
| Manual | M-02 | 報告をすべて取り消す → participants から消え外れた旨が出る | ⏳ 手動確認待ち | — |
| Manual | M-03 | 2 難易度報告して 1 つだけ取り消す → participants に残る | ⏳ 手動確認待ち | — |
| Manual | M-04 | 同じ難易度を再報告 → 「既に報告済み」 | ⏳ 手動確認待ち | — |

## 集計

| Suite | Total | ✅ | ❌ | ⏭ |
|---|---|---|---|---|
| Backend 結合（I-01〜I-28） | 28 | 28 | 0 | 0 |
| Backend 単体（U-01〜U-05） | 5 | 5 | 0 | 0 |
| Frontend E2E | 0 | — | — | — |
| 回帰（G-01〜G-04） | 4 | 3 | 1 | 0 |
| 自動 合計 | 37 | 36 | 1 | 0 |
| 手動（M-01〜M-04） | 4 | — | — | — （手動確認待ち 4） |

Gradle 全体: 204 件実行 / 201 PASSED / 2 FAILED / 1 SKIPPED（`RaidParticipantRepositoryIntegrationTest` の「同じraidIdとuserIdの参加者はupsertされ1行のまま」。既存の `@Disabled`（Issue #54、JDBC のタイムゾーン問題）。#92 で新たに無効化したものではない）。

## 失敗の詳細

### 1. `RaidApiControllerTest` > `GET guilds - ギルド一覧を取得できる`（child bug #135）
```text
java.lang.AssertionError: JSON path "$.guilds.length()" expected:<2> but was:<3>
  at ...RaidApiControllerTest.GET guilds - ギルド一覧を取得できる(RaidApiControllerTest.kt:54)
```

### 2. `RaidApiControllerTest` > `GET guilds - ギルドが存在しない場合は空配列を返す`（child bug #136）
```text
java.lang.AssertionError: Expected an empty value at JSON path "$.guilds" but found: [{"guildId":"1506552878733721751","createdAt":null}]
  at ...RaidApiControllerTest.GET guilds - ギルドが存在しない場合は空配列を返す(RaidApiControllerTest.kt:66)
```

**原因の仮説（未確認）:** テストは guilds テーブルの件数（0 件、または 2 件）を前提にしている。compose DB には実データのギルド `1506552878733721751` が残っており（`union_raids` id 2429、`raid_reports` id 2198、報告者 牛乃タン、2026-11-23 06:54 UTC）、件数が合わない。#92 の差分は `GuildRepository` と `controller` に触れていないため、#92 の変更による回帰ではないと見ている。ただし、このテストは #92 の変更範囲外で、仮説をまだ再現・切り分けしていない。

**扱い:** 失敗は失敗として扱い、マスクしていない。実データは削除していない（読み取りのみ）。

## DB 残存確認（読み取り専用 SELECT）
- テストが作る ID（`7009…` / `70_092…`）のギルド・レイドは無し
- `guilds`: 実データの `1506552878733721751` の 1 件のみ（テスト由来ではない）
- `union_raids`: id 2429 の 1 件のみ（実データ）
- `raid_participants`: 0 件
- `raid_reports`: id 2198 の 1 件のみ（実データ）
- → 結合テストの実コミット（I-23 など）による残存データは無し

## 手動確認待ち（失敗ではない）
- M-01〜M-04: 実 Discord のボタン操作が必要。稼働中の bot は develop 系のコードのため、#92 の挙動はここでは確認できない。PR #134 の手動チェック 2 件も未実施。

## Evidence
- `docs/initiatives/91/evidence/2026-11-23_15-55-51/backend-test.log`
- `docs/initiatives/91/evidence/2026-11-23_15-55-51/junit-xml/`（19 ファイル）
- 🎬 videos/ (0) · 📸 screenshots/ (0)（バックエンドのみのため録画なし）

## Verdict: ❌ 2 FAILURES（自動 36/37 合格、G-04 のみ不合格。手動 4 件は確認待ち）

---

## 追記: 再実行と手動確認 M-01（2026-11-23）

### #135 / #136 の修正後の再実行
- 失敗 2 件は `RaidApiControllerTest` のギルド一覧テストが「guilds テーブルが空」を前提にしていたことが原因だった（#92 の回帰ではない）。
- `b820517` でテストを修正（件数は既存件数 + 2 で比較、空配列のテストはテストのトランザクション内で既存ギルドを消してから確認）。
- 実データのギルドが残った DB で `./gradlew test --rerun-tasks`: **204 件 / 失敗 0 / skipped 1**（既存の #54）。実データは変わっていない。
- G-04 ✅。自動シナリオはすべて合格。

### M-01 ✅ Discord ボタンでの 3T 報告 → 参加者に自動登録
- bot を `feature/92` のコードで起動し、本物のサーバーで HARD を報告した。
- `raid_participants` に報告者が 1 行入った（score 0、username は Discord の表示名、joined_at は報告時刻と同じ）。
- `GET /api/raids/{id}` の `participants` にも同じ内容が出た。
- 報告前は develop の bot で NORMAL を報告しており、参加者は 0 件だった（移行しない方針どおり）。HARD の報告で自己修復した。

### 残り
- M-02〜M-04 は手動確認待ち。

### M-02 ✅ 報告をすべて取り消す → 参加者も削除
- 本物のサーバーで、取り消しメニューから NORMAL と HARD を取り消した。
- `raid_reports` と `raid_participants`（raid 2429）がどちらも 0 件になり、`GET /api/raids/{id}` の `participants` / `reports` も空になった。bot のログにエラーはない。
- Discord 上の返答文言（参加者から外れ、スコアも消える旨）は DB からは確認できないため、操作した人の目視による。
