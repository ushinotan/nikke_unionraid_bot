## Code Review — #94 — attempt 3 — 2026-11-23

### Verdict: ⚠️ APPROVED WITH NOTES

対象: `feature/94` の未コミット差分（backend のみ）。開発者によるコミットはなし（`origin/develop..HEAD` は空）。保護ブランチへの変更なし。attempt 2 から変わったのは `JdaGuildMemberSource.kt` とそのテストだけで、開発者の申告どおり（ほかのファイルの更新時刻は attempt 2 以前のまま）。

レビュー側で `JdaGuildMemberSourceTest`（16 件）と `GuildMemberServiceTest` を `--rerun-tasks` で実行し、すべて成功した。`@SpringBootTest` のテストは DB が要るため、開発者の `./gradlew test` の結果（全件成功、`JdaGuildMemberSourceTest` は 10 回連続で成功）を採った。

---

### 🔴 Critical

なし。

#### C1（attempt 2）の対応確認: ✅ 解消

| 方針（attempt 2） | 実装 | 結果 |
|---|---|---|
| リーダーは `loadMembers()` を開始するだけで、`Task.get()`（`join()`）でブロックしない | `startLoad`（`JdaGuildMemberSource.kt:108-123`）が `onSuccess` / `onError` で共有 Future に結果を渡す。`Task.get()` は呼ばれていない | ✅ |
| リーダーもフォロワーも同じ共有 Future を `get(timeout)` で待つ。期限はこちらで持つ 1 つだけ | `await`（`:125-141`）。`future.get(timeoutMillis, MILLISECONDS)` で、割り込みも効く。JDA の `setTimeout` は外した | ✅ |
| エントリーの除去は `whenComplete` で、2 引数の `remove(key, value)` のまま | `:92` `flight.future.whenComplete { _, _ -> inFlight.remove(guildId, flight) }` | ✅ |
| 期限が来たら共有 Future を終わらせ、`task.cancel()` を試す | `:130-137` で `completeExceptionally(TimeoutException)` を呼び、続けて `task?.cancel()`。キャンセルの失敗は DEBUG ログ | ✅ |
| `Exception` 以外の `Throwable` でもフォロワーを待たせない | `:101-104` で `Throwable` を捕まえて共有 Future を失敗で完了し、`Error` だけ投げ直す | ✅ |
| ログの形（WARN にクラス名とメッセージ、スタックトレースは DEBUG） | `logFailure`（`:143-147`）で保っている | ✅ |
| テスト (a) 完了しない Task で期限内に失敗し、次のリクエストで再取得する | `JdaGuildMemberSourceTest.kt:233-247`（期限 100 ms、`loadMembers` と `cancel` がどちらも 2 回） | ✅ |
| テスト (b) リーダーが失敗したらフォロワーもすぐ失敗を受け取る | `:257-283`（期限 30 秒で、5 秒以内に 2 件とも失敗） | ✅ |
| テスト (c) 同時リクエストと「完了後に再取得」のテストを新しい形に合わせる | `:285-308`、`:388-397` | ✅ |

これで C1 の「Future が完了しないと HTTP スレッドがリークし、ギルドが再起動まで 503 になる」問題は起きない。JDA 側のリクエストが `MemberChunkManager.clear()` / `shutdown()` で捨てられても、リーダーとフォロワーは自分の期限で抜ける。最初に期限が来たスレッドが共有 Future を終わらせるので、エントリーも消える。

#### attempt 2 の 🟡 の対応確認

| 指摘 | 結果 |
|---|---|
| 🟡1 `isLoaded` から `putIfAbsent` までの競合 | ✅ `putIfAbsent` で勝ったあとに `:95` で `isLoaded` をもう一度確かめている。テストは `:218-231` |
| 🟡2 除去が `complete` より先に起きる | ✅ `whenComplete` で除去するので、順序の問題はなくなった |
| 🟡3 `@Suppress("UNCHECKED_CAST")` がそろっていない | ✅ Task のモックは `CapturingTask` / `taskReturning` / `taskFailing`（`:74-118`）にまとまっていて、どれにも `@Suppress` が付いている |
| 🟢 同時リクエストのテストで、待ち状態にならなくても先へ進む | ✅ `awaitState`（`:124-130`）が 5 秒で `check` に失敗し、失敗の理由（どの状態で止まったか）を出す |
| 🟢 `JDA.Status?` の nullable なレシーバー | ❌ 未対応（`:80`）。ブロックしない |

#### kotlin-reviewer の attempt 2 の指摘の対応確認

| 指摘 | 結果 |
|---|---|
| `Error` が出るとフォロワーが待たされたままになる | ✅ 解消。`:101-104` で共有 Future を完了してから投げ直す。テスト `:342-386` で、リーダーが `AssertionError` を投げ、フォロワーはすぐ `GuildMembersUnavailable` を受け取り、次のリクエストで再取得できることを確かめている |
| 並行テストで `Thread.State` をポーリングしている | ⚠️ 一部対応。`Thread.sleep` はなくなり、5 秒の期限と明示的な失敗メッセージも付いた。ただ、同期の手段は今も `Thread.State.TIMED_WAITING` のポーリング（`:124-130` と、`:268`, `:270`, `:296`, `:298`, `:317`, `:330`, `:375` の呼び出し）。下の 🟢1 を参照。現状は誤検知（別の理由で `TIMED_WAITING` になる）がほぼ起きない作りで、外れたときも `verify(times(1))` などで落ちるので、気づかずに通ることはない。ブロックはしない |
| フォロワーの失敗経路のテストがない | ✅ 解消。`onError`（`:257-283`）、開始時の `Error`（`:342-386`）、フォロワーへの割り込み（`:310-340`、割り込みフラグが残ることも確認）の 3 本がある。フォロワーがいるときのタイムアウトだけテストがない（🟢2） |

---

### 🟡 Important

1. **`JdaGuildMemberSource.kt:115-117`: `onSuccess` の中の変換失敗をログに出さずに捨てている。**
   `catch (t: Throwable) { flight.future.complete(Result.failure(...)) }` にはログがない。`toSnapshot()` が失敗すると、理由が分からないまま 503 になる。ほかの失敗経路はすべて `logFailure` を通るので、ここだけ合っていない。
   → `flight.future.complete(logFailure("変換に失敗しました", guildId, t))` のように、`logFailure` を通す（`startLoad` に `guildId` を渡す）。あわせて `onError`（`:119-122`）のログも `logFailure` にそろえると、`guildId` と DEBUG のスタックトレースが付く。今は `guildId` がない。

### 🟢 Suggestions（負債として。ブロックしない）

1. **並行テストの同期（`JdaGuildMemberSourceTest.kt:124-130`）。** リーダーが待ちに入ったことは `CapturingTask` のコールバックが捕まったこと（`captured.success != null`）でも確かめられる。この判定は `Thread.State` より確実なので、リーダーについてはこちらに替えられる。フォロワーには外から見える合図がない。替えるなら、`internal` なテスト用フック（例: `inFlight` の参照数）が要る。今の形で十分動いているので、急がない。
2. **フォロワーがいるときのタイムアウトのテストがない。** リーダーの期限が来て `completeExceptionally(TimeoutException)` が呼ばれると、フォロワーは `ExecutionException` → `:138` の `catch (e: Exception)` で失敗を返す。コードを読む限りは正しい。ただテストでは確かめていない。
3. **`:131` で `completeExceptionally` の戻り値を見ていない。** `get` がタイムアウトしてから `completeExceptionally` を呼ぶまでのあいだに成功の結果が届くと、Future は成功で終わっているのに、このスレッドは失敗を返す（`cancel` も呼ぶが、実害はない）。`if (!flight.future.completeExceptionally(e)) return flight.future.getNow(...)` のように、戻り値が false なら結果を使うと取りこぼしがなくなる。起きる幅はごく小さい。
4. **リーダーだけが割り込まれたとき（`:127-129`）。** 共有 Future は完了しないので、エントリーは残る。JDA が完了させないケースでは、次のリクエストがフォロワーとして 1 回だけ期限まで待ち、そのタイムアウトでエントリーが消える。待ちの上限はあるので問題はない。KDoc に一言書いてもよい。
5. **タイムアウト後の `cancel()` で WARN が 2 回出る可能性。** JDA 6.4.1 の `GatewayTask.cancel()` は `onCancel` を実行する。そこでチャンクのリクエストがキャンセルされると、`onError` に `CancellationException` が届き、`:120` の WARN が「タイムアウトしました」のあとにもう 1 回出る可能性がある。気になるなら、`onError` で `CancellationException` を DEBUG に落とす。
6. `:80`: `JDA.Status?` の `?` は不要（attempt 2 から持ち越し）。

### 📄 Documentation

- D1 はまだ反映されていない。`DEPLOYMENT.md` の Server Members Intent と 4014、`README.md`、`kotlin/docs/API.md` の「4. ギルドのメンバー一覧取得」（trim 前の長さで検査することも書く）、`docs/codebase/backend/routes-map.md` / `services-map.md` を grep したが、該当する記述はなかった。**doc-sync の実行が必要。**

### Summary

C1 は方針どおりに直っている。リーダーは JDA の Future でブロックせず、コールバックで共有 Future に結果を渡す。リーダーもフォロワーも、こちらで持つ 1 つの期限で、割り込みの効く `get(timeout)` で待つ。エントリーは `whenComplete` で必ず消える。`Error` でもフォロワーを待たせない。期限はコンストラクタで差し替えられ、テストは 100 ms と 30 秒の期限で、完了しない Task、リーダーの失敗、開始時の `Error`、割り込み、再確認の各経路を確かめている。`Thread.sleep` を使わず、テストは安定して通っている。attempt 2 の 🟡 3 件と、kotlin-reviewer の 3 件のうち 2 件は解消した。残りの 1 件（`Thread.State` のポーリング）は、期限と失敗メッセージが付いたので許容できる。

残る指摘はブロックしない。`onSuccess` の変換失敗がログに出ない（🟡1）のと、テストと境界の細かい点（🟢）だけ。層の分け方、認証の範囲、エラーの写し方、個人情報をログに出さないことは、前回までと同じく守られている。

オーケストレーターへ: config の「DEV review hardening」に従い、`kotlin-reviewer` と `security-reviewer` を未コミット差分に対して実行し、結果をこのファイルにまとめること。そのあと doc-sync を実行すること（D1）。

---

## kotlin-reviewer の結果（オーケストレーターが追記）

critical / high は 0 件。判定は APPROVED_WITH_NOTES のまま。security-reviewer は attempt 1 でエンドポイントを確認済みで、今回の変更は `JdaGuildMemberSource` 内部の並行処理だけのため、再実行していない。

| # | ラベル | 重大度 | 指摘 | 対応 |
|---|---|---|---|---|
| K1 | SHOULD | medium | タイムアウト直後に onSuccess が完了しても、`completeExceptionally` の戻り値を見ずに失敗を返し `task.cancel()` する（`JdaGuildMemberSource.kt:130-137`）。code-reviewer 🟢 と同じ | 🟡 持ち越し |
| K2 | SHOULD | medium | onSuccess 内の `catch (t: Throwable)` がログなしで失敗にし、Error も握りつぶす（`:113-117`）。code-reviewer 🟡1 と同じ | 🟡 持ち越し |
| K3 | IMO | low | `detach()` 時に取得中の Flight を完了させていない | 🟡 持ち越し |
| K4 | IMO | low | 待機者ごとのタイムアウト起点、ExecutionException 経由のログが待機者数だけ出る点を KDoc に | 🟡 持ち越し |
| K5 | IMO | low | `awaitState` の `Thread.yield` スピン、未カバー経路（タイムアウト時のフォロワー、cancel 例外、リーダー割り込み） | 🟡 持ち越し |
| K6 | IMO | low | INTENT_HINT の連結文言・命名 | 🟡 持ち越し |
