## Code Review — #94 — attempt 2 — 2026-11-23

### Verdict: ❌ REJECTED

対象: `feature/94` の未コミット差分（backend のみ）。開発者によるコミットはなし。保護ブランチへの変更なし。前回（attempt 1）の R1〜R5・R10 と code-reviewer の 🟡 2 件への対応を確認し、single-flight の実装を重点的に見た。

レビュー側で `JdaGuildMemberSourceTest`（13 件）と `GuildMemberServiceTest` を実行し、すべて成功した。`@SpringBootTest` のテストは DB が要るため、開発者の `./gradlew test` の結果を採った。

---

### 🔴 Critical

#### C1. リーダーの `Task.get()` に上限がなく、条件によっては永久に返らない。返らないあいだ in-flight エントリーも消えず、そのギルドは再起動まで 503 になる

場所: `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/JdaGuildMemberSource.kt:88`（待ち）、`:75-80`（エントリーの除去と `complete`）、`:103`（フォロワーの待ち）

`setTimeout(10s)` を付けているので待ちに上限があるように見えるが、JDA 6.4.1 の bytecode を確認すると、タイムアウトは JDA 側の仕組みに任されていて、その仕組みには抜けがある。

- `GuildImpl.loadMembers` の `GatewayTask` は、`MemberChunkManager$ChunkRequest`（`CompletableFuture`）から作られる。この future が完了する経路は、チャンクの受信、キャンセル、`MemberChunkManager$TimeoutHandler` によるタイムアウトの 3 つだけ。
- `TimeoutHandler` は `scheduleAtFixedRate(…, 5, 5, SECONDS)` で 5 秒ごとに `requests` マップを走査し、期限切れのリクエストに `completeExceptionally(TimeoutException())` を呼ぶ。
- `WebSocketClient.invalidate()`（再開できないセッション切断で再 IDENTIFY するとき）は `chunkManager.clear()` を呼ぶ。中身は `requests::clear` で、保留中のリクエストを**完了させずに**マップから捨てる。これ以降、そのリクエストは `TimeoutHandler` からも見えないので、タイムアウトしない。
- `MemberChunkManager.shutdown()` は `timeoutHandle.cancel()` でタイマーを止める。止めたあとに残ったリクエストもタイムアウトしない。

`Task.get()` は `CompletableFuture.join()` で、割り込みも効かない。ロード中の約 10〜15 秒のあいだにセッションが無効になると、次のことが起きる。

1. リーダーの HTTP スレッドが永久にブロックされる（スレッドリーク）。
2. `finally { inFlight.remove(guildId, mine) }`（`:78`）まで進まないので、エントリーが残り続ける。
3. 同じギルドへの以後のリクエストはすべてフォロワーになり、`:103` で 11 秒待ったあと 503 になる。`isLoaded` も false のままなので、プロセスを再起動するまで直らない。

これは計画 §5 のチェックリスト「`loadMembers()` は … HTTP スレッドで、**タイムアウト付きで待つ**」を満たしていない。single-flight を入れたことで、1 回の取りこぼしがギルド単位で恒久的な障害になった。

あわせて直すこと（原因は同じ「リーダーとフォロワーで待ちの期限が別々」）:

- **リーダーとフォロワーの期限が食い違う。** JDA のタイムアウトは 5 秒ごとの走査で判定されるため、リーダーの実際の待ちは 10〜15 秒になる。フォロワーの期限は 11 秒（`:103`）。このため、リーダーが 12 秒で成功しても、先に待っていたフォロワーは 503 になることがある。
- **`Exception` 以外の `Throwable` で `mine` が完了しない。** `load()`（`:84-97`）は `Exception` しか捕まえない。`Error` が出ると、`finally` でエントリーは消えるが、`mine.complete(...)`（`:80`）には到達しない。フォロワーは理由なく 11 秒待たされる。

**直し方（方針）:** リーダーも `Task.get()` でブロックしないようにする。

- エントリーを `putIfAbsent` で取ったスレッドは `guild.loadMembers()` を開始するだけにする。結果は `task.onSuccess { shared.complete(...) }` と `task.onError { shared.completeExceptionally(...) }` で、共有の `CompletableFuture` に渡す。
- リーダーもフォロワーも、同じ `shared.get(MEMBER_LOAD_TIMEOUT_SECONDS, SECONDS)` で待つ。待ちの上限は、こちらで持つ 1 つの期限だけにする。
- エントリーの除去は `shared.whenComplete { … inFlight.remove(guildId, shared) }`（`remove(key, value)` の 2 引数版のまま）で行う。どの経路で終わっても必ず消えるようにする。
- 期限が来ても `shared` が終わっていなければ、`shared.completeExceptionally(TimeoutException())` などで終わらせる。そうすればエントリーが消え、次のリクエストでやり直せる。可能なら `task.cancel(true)` も呼ぶ。`orTimeout` を使ってもよい。
- JDA の `setTimeout` は残してよいが、待ちの上限としては扱わない。
- 例外のログは今の形（WARN にクラス名とメッセージ、スタックトレースは DEBUG）を保つ。

**追加するテスト**（`kotlin/src/test/kotlin/gg/nikke/raid/bot/bot/JdaGuildMemberSourceTest.kt`）:

- (a) 完了しない Task（`onSuccess` / `onError` のコールバックをどちらも呼ばないモック）で、期限内に `GuildMembersUnavailable` が返ること。その後のリクエストで `loadMembers` がもう一度呼ばれること（エントリーが消えていること）。期限はテストで短くできるよう、コンストラクタ引数か `internal` にする。
- (b) リーダーが失敗したとき、フォロワーも失敗の結果を受け取ること（11 秒待たないこと）。
- (c) 既存の同時リクエストのテストと「完了後に再取得できる」テストを、新しい形に合わせて直すこと。

---

### 🟡 Important

1. **`JdaGuildMemberSource.kt:56-59`: `isLoaded` を見てから `putIfAbsent` するまでの間に競合がある。**
   A がロードを終えて `remove` したあとに、A の完了前に `isLoaded == false` を見ていた B が `putIfAbsent` すると、B は 2 回目の `loadMembers` を出してしまう。回数は限られるのでブロックはしない。直すなら、`putIfAbsent` で勝ったあとに `guild.isLoaded` をもう一度確かめ、true ならキャッシュから返す（ダブルチェック）。C1 の作り直しと一緒に入れるのが手軽。
2. **`JdaGuildMemberSource.kt:78-80`: 「エントリーの除去」が「`complete`」より先に起きる。**
   この間に来たリクエストは、完了直前の結果を共有せずに新しいロードを始める。C1 の方針（`whenComplete` で除去）にすれば、この順序の問題も消える。
3. **`JdaGuildMemberSourceTest.kt:171,185,198,213`: `mock(Task::class.java) as Task<List<Member>>` に `@Suppress("UNCHECKED_CAST")` がない。**
   `taskReturning`（`:74`）には付いているので揃っていない。コンパイル警告が出る。共通のヘルパー（例: `mockTask()`）にまとめれば 1 か所で済む。

### 🟢 Suggestions（負債として。ブロックしない）

- `JdaGuildMemberSource.kt:62`: `JDA.Status?` の nullable なレシーバーは不要（`JDA.status` は非 null）。
- `GuildMemberApiController.kt:46`: 長さを trim 前の値で検査するようになった。前後に空白を足して 100 文字を超えると、中身が短くても 400 になる。仕様として問題はないが、doc-sync で `kotlin/docs/API.md` に「trim 前の長さで検査する」と書くこと。
- `JdaGuildMemberSourceTest.kt:226-229`: 同時リクエストのテストは、5 秒以内にフォロワーが `TIMED_WAITING` にならなくても先へ進む。その場合のテスト失敗は「`loadMembers` が 2 回呼ばれた」という原因の分かりにくい形で出る。ループのあとに `assertEquals(Thread.State.TIMED_WAITING, follower.state)` を足すと、失敗の理由が明確になる。

### 前回指摘への対応（確認結果）

| 指摘 | 結果 |
|---|---|
| 🟡1 INTENT_HINT の範囲 | ✅ `DiscordBot.kt:100` の `ExecutionException` だけに付いている。外側の catch は元の文言 |
| 🟡2 false → true の遷移テスト | ✅ `JdaGuildMemberSourceTest.kt:149-166`（`thenReturn(false, true)` + `times(1)`） |
| R1 同時の `loadMembers` | ⚠️ 正常系はまとめられている。ただし異常系は C1 のとおり |
| R2 detach | ✅ `DiscordBot.kt:66` で `jda.shutdown()` の前に呼んでいる。SHUTTING_DOWN / SHUTDOWN のときも失敗にする（`JdaGuildMemberSource.kt:51`）。テストあり |
| R3 タイムアウト・キャンセル・失敗の区別 | ✅ ログで区別している。テストは実際に投げられる `CompletionException(TimeoutException)` を使っている。ただしタイムアウト自体が来ない経路がある（C1） |
| R4 ログ | ✅ 型変換エラーは `e.name` だけ出す。WARN にクラス名とメッセージ、スタックトレースは DEBUG |
| R5 コントローラーテスト | ✅ 並び順、空白だけの query、前後に空白のある query、`limit=abc`、各エラーで `$.error` を検証している |
| R10 `fold` | ✅ `GuildMemberApiController.kt:53` |
| 🟢 trim の重複・`@Suppress`・KDoc | ✅ trim はサービス側だけ（`GuildMemberService.kt:33`）。サービスのクラスと関数に KDoc あり |

### 📄 Documentation

- D1（`DEPLOYMENT.md` の Server Members Intent と 4014、`README.md`、`kotlin/docs/API.md` の「4.」、`docs/codebase/backend/routes-map.md` / `services-map.md`）はまだ反映されていない（grep で該当なし）。C1 を直したあとに **doc-sync の実行が必要**。

### Summary

前回の 🟡・SHOULD への対応は丁寧で、ログ、テスト、`detach`、`fold`、KDoc はすべて期待どおり。層の分け方、認証の範囲、エラーの写し方、個人情報をログに出さないことも、引き続き守られている。

ただ、single-flight のリーダーが JDA の `Task.get()`（`join()`）でブロックする作りには問題がある。JDA 6.4.1 では、セッションの無効化（`WebSocketClient.invalidate()` → `MemberChunkManager.clear()`）やシャットダウン（`MemberChunkManager.shutdown()`）でタイムアウトの監視から外れたリクエストが、永久に完了しない。そうなるとリーダーのスレッドはリークし、in-flight エントリーも残るので、そのギルドへのリクエストは再起動まで毎回 11 秒待ったうえで 503 になる。計画の「タイムアウト付きで待つ」を満たしていないため、差し戻す。

直し方の要点: 待ちの期限をこちらで持つ 1 つにまとめる。リーダーもフォロワーも共有 future を `get(timeout)` で待つ。エントリーは `whenComplete` で必ず消す。修正範囲は `JdaGuildMemberSource.kt` とそのテストだけ。
