# QA Report — #94 — 2026-11-23 — nikke-unionraid-bot

## Environment
- Branch: `feature/94`（+1 / -0 vs `origin/develop`。リベース不要）
- Backend `http://localhost:8080`: 到達可能（`/api/guilds` トークン無しで 401、トークン付きで 200）
- Frontend `http://localhost:3000`: 到達可能（200）。ただし frontend テストは対象外（下記）
- DB（`nikke_unionraid_db`）: `pg_isready` で受付中（healthy）
- Smoke: 実施（`/api/guilds` の認証応答を確認）
- **注意**: compose スタック（`nikke_unionraid_*`）は別 worktree から起動されており develop のコードで動いている。このブランチの新エンドポイントは稼働中サーバーでは検証していない。新エンドポイントの検証は Spring Boot 統合テスト（MockMvc + 実 `InternalApiTokenFilter`）で行った。コンテナの起動・停止・再作成は行っていない。

## Metrics
- バックエンドテスト（`./gradlew test --no-daemon`）: `BUILD SUCCESSFUL in 26s`
- テストケースの実行時間の合計: 約 4.2 s（JUnit XML 集計）
- フロントエンド E2E / `web/__tests__`: 実行なし（対象外）
- 全体の所要（QA 実行）: 約 1 分（テスト実行 26 s ＋ 準備・集計）

## Results per scenario

| Suite | ID | Scenario | Status | Duration | Test |
|---|---|---|---|---|---|
| Backend 結合 | I-01 | 正常系: userId は文字列、displayName・avatarUrl・truncated あり | ✅ PASS | 0.014 s | QA `I-01 正常系 ...` |
| Backend 結合 | I-02 | bot / system ユーザーが結果に含まれない | ✅ PASS | 0.009 s | QA `I-02 bot と system ...` |
| Backend 結合 | I-03 | 表示名: ニックネーム → グローバル名 → ユーザー名 | ✅ PASS | 0.008 s | QA `I-03 表示名は...` |
| Backend 結合 | I-04 | query の部分一致（大文字小文字無視） | ✅ PASS | 0.007 s | QA `I-04 query は部分一致...` |
| Backend 結合 | I-05 | query に一致なし → 空配列 | ✅ PASS | 0.007 s | QA `I-05 query に一致...` |
| Backend 結合 | I-06 | limit 超過で先頭のみ・truncated=true、ちょうどは false | ✅ PASS | 0.008 / 0.010 s | QA `I-06` ×2 |
| Backend 結合 | I-07 | 未参加ギルド → 404 NOT_FOUND | ✅ PASS | 0.011 s | QA `I-07 ...` |
| Backend 結合 | I-08 | メンバー取得不能 → 503 | ✅ PASS | 0.009 s | QA `I-08 ...` |
| Backend 結合 | I-09 | 入力不正 → 400 BAD_REQUEST（guildId / limit 範囲外・境界 / query 101 文字） | ✅ PASS | 0.008〜0.018 s | QA `I-09` ×4 |
| Backend 結合 | I-10 | トークン無し・不一致 → 401 | ✅ PASS | 0.005 / 0.010 s | QA `I-10` ×2 |
| Backend 単体 | U-01 | 表示名の決定（空白のみは次の候補へ） | ✅ PASS | — | DEV `GuildMemberServiceTest` ×4 |
| Backend 単体 | U-02 | bot / system ユーザーの除外 | ✅ PASS | — | DEV `GuildMemberServiceTest` |
| Backend 単体 | U-03 | 部分一致検索（ニックネーム・グローバル名・ユーザー名） | ✅ PASS | — | DEV `GuildMemberServiceTest` ×3 |
| Backend 単体 | U-04 | 並び順（表示名順、同名は userId 順） | ✅ PASS | — | DEV `GuildMemberServiceTest` |
| Backend 単体 | U-05 | limit 境界の truncated | ✅ PASS | — | DEV `GuildMemberServiceTest` ×2 |
| Backend 単体 | U-06 | ギルドなし → GuildNotFound / 取得不能 → GuildMembersUnavailable | ✅ PASS | — | DEV `GuildMemberServiceTest` ×2 |
| Backend 単体 | U-07 | JDA 供給元（未接続・ギルドなし・未ロード時のみ load・タイムアウト・並行） | ✅ PASS | — | DEV `JdaGuildMemberSourceTest`（16 件） |
| Backend 回帰 | G-01 | 既存 `/api/guilds` はトークン無しで 401、ありで 200 | ✅ PASS | 0.033 s | QA `G-01 ...` ＋ DEV `InternalApiTokenFilterIntegrationTest` |
| Backend 回帰 | G-02 | `discord.bot.enabled=false` でアプリケーションコンテキストが起動 | ✅ PASS | — | スイート全体の起動で確認 |
| Backend 回帰 | G-03 | backend 既存テスト全体が通る | ✅ PASS | — | 下記サマリー参照 |
| Frontend E2E | E-XX | — | ⏭ 対象外 | — | シナリオ 0 件（`web/` 変更なし） |
| 手動 | M-01 | Portal で Server Members Intent を ON にして bot 起動 | ⏳ 手動確認待ち | — | — |
| 手動 | M-02 | 実ギルドで `GET /api/guilds/{id}/members` を呼ぶ | ⏳ 手動確認待ち | — | — |
| 手動 | M-03 | （任意）Intent OFF で起動するとヒント付きエラー | ⏳ 手動確認待ち | — | — |
| 手動 | M-04 | `DEPLOYMENT.md` / `README.md` の Discord 設定手順 | ⏳ 手動確認待ち | — | — |

## Summary

| Suite | Total | ✅ | ❌ | ⏭ |
|---|---|---|---|---|
| Backend（gradle test 全体、14 スイート） | 161 | 160 | 0 | 1 |
| 　うち QA 追加（GuildMemberApiQaTest） | 16 | 16 | 0 | 0 |
| 　うち DEV（GuildMember 関連・Filter 関連） | 56 | 56 | 0 | 0 |
| Frontend E2E | 0 | 0 | 0 | 0（対象外） |
| 手動（M） | 4 | 0 | 0 | 4（手動確認待ち） |

- スキップ 1 件は既存テスト `RaidParticipantRepositoryIntegrationTest` の「同じraidIdとuserIdの参加者はupsertされ1行のまま」。`@Disabled("Issue #54 ...")` 付きで、本件の変更とは無関係。
- 失敗 0。

## Detailed failures
なし。

## Evidence
- バックエンド: `docs/initiatives/91/evidence/2026-11-23_14-25-36/`（JUnit XML 14 件、`gradle-test.log`）
- 🎬 videos/: 0（フロントエンド E2E 対象外）
- 📸 screenshots/: 0（同上）

## Verdict: ✅ ALL AUTOMATED PASSED（手動 M-01〜M-04 は未確認）

- 自動化可能なシナリオ（I / U / G）はすべて通過。
- M-01〜M-04 は本物の Discord と Developer Portal 設定が必要なため、人間の確認待ち。
- Issue は `in-qa` のまま。`done`（close）への遷移は行わない（PR #131 の `Closes #94` によりマージ時に閉じる）。
