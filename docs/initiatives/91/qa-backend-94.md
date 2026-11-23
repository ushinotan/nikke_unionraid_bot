# QA Backend — #94 テスト対応表

## 新規ファイル

- `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/GuildMemberApiQaTest.kt`（16 テスト、全て通過）
  - 実際の `InternalApiTokenFilter` を通し、`GuildMemberSource` のみ `@Primary` のフェイクに差し替える。DB 書き込みなし（クリーンアップ不要）。
- DEV 側のテストは編集していない。

## シナリオ → テスト対応

| ID | テスト（クラス / 名前） |
|---|---|
| I-01 | QA `I-01 正常系 ...` / DEV Controller `メンバー一覧を取得できる` |
| I-02 | QA `I-02 bot と system ...`（フェイク経由）/ DEV Jda `Snapshotに正しく変換される`（bot・system とも isBot=true を確認） |
| I-03 | QA `I-03 表示名は...`（3 段階すべて API 経由） |
| I-04 | QA `I-04 query は部分一致...`（大文字小文字）/ DEV Controller `queryで絞り込める` |
| I-05 | QA `I-05 queryに一致するメンバーがいなければ空配列` |
| I-06 | QA `I-06 limit を超えると...` と `I-06 ちょうど件数...` / DEV Controller `limitで切られると...` |
| I-07 | QA `I-07 ... 404 NOT_FOUND` / DEV Controller `ギルドがなければ404` |
| I-08 | QA `I-08 ... 503` / DEV Controller `取得不能なら503` |
| I-09 | QA `I-09 guildId...` `limit 範囲外(-1,101)` `limit 境界(1,100)` `query 101/100文字` / DEV Controller 各 400 テスト |
| I-10 | QA `I-10 トークン無し` `I-10 トークン不一致`（members エンドポイントで実フィルター経由）/ DEV FilterIntegration `トークン無しのメンバー一覧リクエストは401` |
| U-01 | DEV Service `ニックネームがあれば...` `グローバル名を表示名` `ユーザー名を表示名` `空白だけの値は次の候補に回す` |
| U-02 | DEV Service `botとsystemは除外される` |
| U-03 | DEV Service `queryはニックネーム/グローバル名/ユーザー名に部分一致...` |
| U-04 | DEV Service `表示名順で同名はuserId順に並ぶ` |
| U-05 | DEV Service `limitを超えるとtruncatedがtrue` `ちょうどlimitならfalse` |
| U-06 | DEV Service `ソースがnullならGuildNotFound` `ソースが失敗ならGuildMembersUnavailable` |
| U-07 | DEV `JdaGuildMemberSourceTest`（attach 前失敗、ギルドなし null、未ロード時のみ load、タイムアウト、並行呼び出し等 15 件） |
| G-01 | QA `G-01 既存の api guilds はトークン無しで401 トークンありで200` / DEV FilterIntegration の既存テスト |
| G-02 / G-03 | 新規テストなし。私の担当では全体スイートを実行していない（指示により新規クラスのみ実行）。オーケストレーター側で `./gradlew test` を実行すること |

U-XX は DEV のテストで足りているため追加していない。

## 計画とコードの差異（Divergence）

- 計画の I-09「`query` が長すぎる」「`limit` が範囲外」は実装で確定: `limit` は 1〜100（既定 100）、`query` は 100 文字以内。エラーコードは `BAD_REQUEST`。計画との矛盾なし。
- `limit` が数値でない場合は `MethodArgumentTypeMismatchException` ハンドラーで 400（DEV テストが確認済み）。
- 計画 G-01 の例にある `/api/raids` は一覧エンドポイントではない（実在は `/api/raids/{raidId}`）。代わりに `/api/guilds` を使った。
- I-08 は JDA 無効の環境では実際の `JdaGuildMemberSource` ではなくフェイクで再現している（計画どおり）。

## 疑わしいバグ

なし。補足として、DEV の `GuildMemberApiControllerTest` はトークンフィルター無しで MockMvc を構築しているため、I-10 の members エンドポイントは `InternalApiTokenFilterIntegrationTest`（トークン無しのみ）で担保されていた。不一致トークンの members 経路は今回の QA テストで補った。
