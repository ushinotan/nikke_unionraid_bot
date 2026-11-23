## Self-test result

### ビルド / 起動チェック
- backend (kotlin): `./gradlew build` ✅ (全テスト PASSED、`--rerun-tasks` でも FAILED なし)
- frontend (web): 変更なしのためスキップ
- 新規成果物: GuildMemberApiController / GuildMemberService / JdaGuildMemberSource ✅ (コントローラーは MockMvc テストで `GET /api/guilds/{guildId}/members` の応答を確認、トークン無しは 401)
- 実 Discord での起動確認は未実施。Developer Portal の Server Members Intent を ON にする必要があり、この環境では行えない。

### 手動確認チェックリスト
- [ ] Developer Portal で Server Members Intent を ON にして bot を起動し、READY 後に起動エラー (Disallowed intents) が出ない
- [ ] 正しい `X-Internal-Api-Token` で `GET /api/guilds/{実guildId}/members` が 200 でメンバー一覧を返す
- [ ] bot アカウントが一覧に含まれない
- [ ] ニックネーム設定済みメンバーの displayName がニックネームになる
- [ ] `query` で表示名/グローバル名/ユーザー名の部分一致絞り込みができる
- [ ] `limit` 超過時に `truncated: true` になる (limit=0/101、不正値は 400)
- [ ] 存在しない guildId で 404、READY 前または取得タイムアウト時に 503
- [ ] トークン無し/誤りは 401
- [ ] 大きいギルドで初回 loadMembers 後のレスポンス時間が許容範囲

### RBAC / スコープ
- `/api/` 配下のため InternalApiTokenFilter で保護される。回帰テスト「トークン無しのメンバー一覧リクエストは401」PASSED。
