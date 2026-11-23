## Code Review — #94 — attempt 1 — 2026-11-23

### Verdict: ⚠️ APPROVED WITH NOTES

対象: `feature/94` の未コミット差分（backend のみ。B1〜B5）。開発者によるコミットはなし（`origin/develop..HEAD` は空）。保護ブランチへの変更なし。

### 🔴 Critical

なし。

確認した項目:
- 層の向き: JDA の import は `bot/JdaGuildMemberSource.kt` と `bot/DiscordBot.kt` だけ。`service` / `controller` は JDA を参照していない（ポート `GuildMemberSource` 経由）。
- 認証: エンドポイントは `/api/guilds/{guildId}/members`（`@RequestMapping("/api")`）で、`InternalApiTokenFilter` の保護範囲に入る。401 の回帰テストを `InternalApiTokenFilterIntegrationTest.kt:47` に追加済み。
- 認可の境界: backend にはユーザー文脈がない。controller の KDoc（`GuildMemberApiController.kt:22-23`）に「BFF が guildIds・特権を確認する前提」と書いてある。web 側の必須要件として #91 の後続へ引き継ぐ（計画 §3.5 のとおり）。
- エラー処理: 想定内の失敗は `Result` + `RaidDomainError.GuildNotFound` / `GuildMembersUnavailable`。controller が `when` で 404 / 503 に写し、それ以外は `logger.error(..., e)` + 汎用メッセージの 500。`error` は UPPER_SNAKE_CASE の固定値。
- 入力検査: `query` は trim 後に 100 文字以下、`limit` は 1〜100（既定 100）。400 は handler 内で直接返しており、`IllegalArgumentException` を一律 400 にはしていない。
- 個人情報・ログ: ログに出すのは `guildId` とパラメーター名だけ。表示名・アバター URL・`query` の中身は出していない（既存の `RaidApiController` は `e.value` を出しているが、新コントローラーは出していない）。
- JDA: `enableIntents(GUILD_MEMBERS)` + `MemberCachePolicy.ALL`、`ChunkingFilter` は既定のまま。`loadMembers()` は `guild.isLoaded == false` のときだけ、HTTP スレッドで `setTimeout(10s).get()`。JDA 6.4.1 の bytecode で、`GuildImpl.isLoaded()` が `GUILD_MEMBERS 有効 && memberCount <= memberCache.size()` であること、`GatewayTask.get()` がゲートウェイスレッドで `UnsupportedOperationException` を投げ、それ以外では `future.join()` であることを確認した。このため 2 回目以降のリクエストでは `loadMembers` を呼ばない。
- `JdaGuildMemberSource` に `@ConditionalOnProperty` は付いていない。`discord.bot.enabled=false` でも Bean が作られる。
- 永続化の変更なし（DB・`init.sql`・`migrations/` は触っていない）。秘密情報のハードコードなし。
- 単体テスト: `GuildMemberServiceTest`（14 件）と `JdaGuildMemberSourceTest`（6 件）をレビュー側でも実行し、すべて成功した。`@SpringBootTest` のテストは DB が要るため、開発者の `./gradlew test` の結果を採った。

### 🟡 Important

1. **`kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/DiscordBot.kt:60`: Intent のヒントがすべての起動失敗に付く。**
   外側の `catch (e: Exception)` で `$INTENT_HINT` を付けているので、Intent と関係ない失敗（`CommandRegistrar.register` や `scheduler.resumeSchedules` の例外など）でも「Server Members Intent が ON か確認してください」と出て、原因を取り違えやすい。さらに READY 待ちの失敗は、`waitForReadyWithTimeout`（`:99`）で付けたヒントを外側でもう一度付けるので、例外チェーンにヒントが 2 回出る。
   → ヒントは 4014 が実際に届く経路（`:99` の `ExecutionException`）だけに付け、`:60` は元の文言に戻す。計画 §3.2 の「`waitForReadyWithTimeout` の `ExecutionException` / その他の例外」は `waitForReadyWithTimeout` の中のこと。

2. **`kotlin/src/test/kotlin/gg/nikke/raid/bot/bot/JdaGuildMemberSourceTest.kt:108-119`: 「2 回目の呼び出しで `loadMembers` を呼ばない」が、未ロードからロード済みへの遷移として検証されていない。**
   このテストは最初から `isLoaded=true` のギルドで 2 回呼んでいるだけ。計画 §5 のチェックリストにある「リクエストごとに `loadMembers` を呼ばない」の回帰防止にはなっていない。たとえば `guild.isLoaded` の判定を消して毎回 `loadMembers` を呼ぶ形に変えても、テスト `:97-106` と `:108-119` はどちらも通ってしまう。
   → `isLoaded` を 1 回目 `false`、2 回目 `true` にスタブし（`thenReturn(false, true)`）、2 回呼んだあとに `verify(guild, times(1)).loadMembers()` を確かめる。2 回目がキャッシュから返ることも確かめる。実装自体は上の JDA の確認から正しく動く見込みなので、直すのはテストだけ。

### 🟢 Suggestions（負債として。ブロックしない）

- `JdaGuildMemberSource.kt:43-46`: JDA の `Task.get()` は `CompletableFuture.join()` なので `InterruptedException` を投げない。この `catch` には到達しない。残すなら理由をコメントに書くか、削除する。
- `GuildMemberApiController.kt:46` と `GuildMemberService.kt:25`: `query` を 2 回 trim している。サービス側だけで正規化すれば済む（controller の trim は長さ検査のためなので、残すならどちらかにコメントを書く）。
- `GuildMemberApiController.kt:55-62`: `onSuccess { return ... }` の非ローカル return は読みにくい。`result.fold(onSuccess = ..., onFailure = ...)` にすると流れが 1 つの式にまとまる。
- `GuildMemberApiControllerTest.kt:113-123`: `limit=0` / `limit=101` のテストに `jsonPath("$.error").value("BAD_REQUEST")` がない。`limit=abc`（型変換エラー → 400）のテストもあるとよい。
- `JdaGuildMemberSourceTest.kt:55,63`: `loadedGuild` / `unloadedGuild` の `@Suppress("UNCHECKED_CAST")` は不要（キャストしていない）。
- `GuildMemberService.kt:6`: クラスに KDoc がない（必須は public 関数だけなので任意）。
- 同じギルドへの初回リクエストが同時に来ると `loadMembers` が重複して走る。計画で許容済み。運用で問題が出たら in-flight を共有する。

### 📄 Documentation

- D1（`DEPLOYMENT.md` §2 とトラブルシューティング（4014）、`README.md` の Intent 必須と 100 サーバー超の審査、`kotlin/docs/API.md` の「4. ギルドのメンバー一覧取得」、`docs/codebase/backend/routes-map.md` / `services-map.md`）はまだ反映されていない。grep で `Server Members` / `4014` / `members` のどれも見つからなかった。**doc-sync の実行が必要**（完了条件 3）。

### Summary

計画の B1〜B5 はすべて実装済みで、受け入れ条件 (2) API がメンバーを返す、(4) テストの追加、を満たしている。(1) 実機起動は人間による確認、(3) ドキュメントは doc-sync の担当。ポートとアダプターで分けた構成（`service` 内のポート `GuildMemberSource` と、`bot` 内の JDA 実装）は計画どおりで、`service` は Spring なしで単体テストできている。エラーの写し方、入力の上限、ログに個人情報を出さないこと、`loadMembers` を初回だけ呼ぶことなど、セキュリティと性能の要点も押さえている。表示名の決め方（空白だけの値をスキップする）、bot / system の除外、大文字小文字を無視した部分一致、安定した並び順、`truncated` の境界（ちょうど limit のとき false）は、テストの網羅も十分。

指摘はブロックしない 2 点。起動失敗のヒントを付ける範囲（`DiscordBot.kt:60`）と、`loadMembers` を初回だけ呼ぶことのテスト（`JdaGuildMemberSourceTest.kt:108-119`）。

オーケストレーターへ: config の「DEV review hardening」に従い、続けて `kotlin-reviewer`（`*.kt`）と `security-reviewer`（新しい `/api` エンドポイント、Privileged Intent、個人情報）を実行し、結果をこのファイルにまとめること。

---

## スタックレビュアーの結果（オーケストレーターが追記）

`kotlin-reviewer` と `security-reviewer` を未コミット差分に対して並行で実行した。critical / high は 0 件のため、判定は APPROVED_WITH_NOTES のまま。

| # | ラベル | 重大度 | 指摘 | 出典 | 対応 |
|---|---|---|---|---|---|
| R1 | SHOULD | medium | `isLoaded == false` の間、同一ギルドへの並行リクエストがそれぞれ `loadMembers()` を発行し、HTTP スレッドを最大10秒ずつ塞ぐ（`JdaGuildMemberSource.kt:37-41`）。KDoc の「初回のみ」も実態と合わない | kotlin / security | 修正ラウンドで対応 |
| R2 | SHOULD | medium | `detach` がなく、shutdown 後も古い JDA を参照し続ける（`JdaGuildMemberSource.kt:22-30`, `DiscordBot` の `@PreDestroy`） | kotlin | 修正ラウンドで対応 |
| R3 | SHOULD | medium | 汎用 `catch (e: Exception)` でタイムアウト・キャンセル・失敗を区別できない。タイムアウトのテストが実際に投げられる型を使っていない（`JdaGuildMemberSource.kt:47-50`） | kotlin | 修正ラウンドで対応 |
| R4 | SHOULD | medium | 例外ログにスタックトレース全文と不正な入力値が出る（`GuildMemberApiController.kt:145`, `JdaGuildMemberSource.kt:45,48`） | security / kotlin | 修正ラウンドで対応 |
| R5 | SHOULD | low-medium | コントローラーテストが並び順・query 正規化・`limit=abc` を検証していない | kotlin | 修正ラウンドで対応 |
| R6 | SHOULD | medium | 毎回全メンバーを変換→フィルタ→ソートしており、大規模ギルドで負荷が大きい | security | 🟡 持ち越し（KDoc に前提を明記） |
| R7 | IMO | low | `MemberCachePolicy.ALL` のメモリ常駐 | kotlin / security | 🟡 持ち越し |
| R8 | IMO | low | `guildId <= 0` を 400 にしていない | security | 🟡 持ち越し |
| R9 | IMO | low | 404 / 503 の差で bot の参加ギルドが分かる。BFF（#91 の web タスク）で `session.guildIds` 照合と `isPrivileged` を必須にすること | security | 🟡 #91 web タスクへ申し送り |
| R10 | IMO | low | `onSuccess { return }` → `fold` | kotlin / code-reviewer | 修正ラウンドで対応 |
| R11 | IMO | low | `DiscordBot` が具象 `JdaGuildMemberSource` に依存 | kotlin | 🟡 持ち越し |
