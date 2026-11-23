# Tech Lead 計画 — #94 bot でサーバーメンバーの一覧を取得できるようにする

## エグゼクティブサマリー

| 項目 | 内容 |
|---|---|
| 種別 | story（親: #91 代理登録） |
| 複雑度 | 指定なし（見立て: M） |
| ブランチ | `feature/94` |
| スコープ | **backend のみ**（`kotlin/`）+ 運用ドキュメント。web（BFF・画面）の変更はなし |
| 影響 | `bot/DiscordBot.kt`（Intent・メンバーキャッシュ）、新規 `service/GuildMemberService.kt` ほか、`controller/` に `GET /api/guilds/{guildId}/members` を追加、`dto/ApiResponses.kt` |
| 見積もり | backend 実装・テスト 1〜1.5 日、ドキュメント 0.25 日 |
| リスク | 中。Privileged Intent が Portal で OFF のままだと **bot が起動しない**（JDA close code 4014）。メンバーキャッシュ方針によってはメモリ・起動時間・ゲートウェイのレート制限に影響する |
| ブロッカー | なし（`depends_on` なし） |
| 受け入れ条件の絞り込み | **しない**（4 項目すべてこのタスクで満たす。「Intent を有効にして起動する」の実機確認だけは本物の Discord が要るため人間が行う） |

---

## 1. Blueprint の要約（PO の要求）

特権ユーザーが他メンバーを代理登録できるように（#91）、bot からサーバーメンバーの一覧を取れるようにする。

- Developer Portal で Server Members Intent を ON にする（再招待は不要）
- `DiscordBot` の `JDABuilder` に `GatewayIntent.GUILD_MEMBERS` を追加する
- 内部 API `GET /api/guilds/{guildId}/members` を追加する
  - 返す項目: `userId`、表示名（サーバーニックネーム → グローバル名 → ユーザー名）、アバター
  - bot ユーザーは除外する
  - `?query=` で名前の部分一致検索
- `DEPLOYMENT.md` / README に Server Members Intent を ON にする手順を追記する

完了条件: (1) `GUILD_MEMBERS` を有効にして起動する、(2) API がメンバーを返す、(3) デプロイ手順が更新されている、(4) テストが追加されている。

---

## 2. 影響マップ（CodeBase 由来の実名）

パッケージのルートは `kotlin/src/main/kotlin/gg/nikke/raid/bot/`。

### backend

| 層 | ファイル | 変更 |
|---|---|---|
| bot | `bot/DiscordBot.kt` | `JDABuilder.createDefault(token)` に `.enableIntents(GatewayIntent.GUILD_MEMBERS)` と `.setMemberCachePolicy(MemberCachePolicy.ALL)` を追加。READY 後に `guildMemberSource.attach(jda)` を呼ぶ |
| bot | **新規** `bot/JdaGuildMemberSource.kt` | `GuildMemberSource` の JDA 実装（`@Component`、**`@ConditionalOnProperty` は付けない**）。JDA を `@Volatile var` で保持し、`attach(jda)` で受け取る |
| service | **新規** `service/GuildMemberSource.kt` | ポート（interface）と、生データ `GuildMemberSnapshot` |
| service | **新規** `service/GuildMemberService.kt` | `@Service`。bot 除外・表示名決定・部分一致・並び替え・件数上限。`Result<GuildMemberPage>` を返す |
| service | **新規** `service/GuildMember.kt`（`GuildMemberService.kt` 内でも可） | ドメインの結果型 `GuildMember(userId, displayName, avatarUrl)`、`GuildMemberPage(members, truncated)` |
| service | `service/RaidDomainError.kt` | `GuildNotFound`、`GuildMembersUnavailable` を追加 |
| controller | **新規** `controller/GuildMemberApiController.kt`（`@RequestMapping("/api")`） | `GET /guilds/{guildId}/members`。`RaidApiController` は「読み取り専用・リポジトリ直呼び」の性格なので、JDA 依存のサービスを呼ぶ別コントローラーに分ける（`RaidApiController` の `@ExceptionHandler` は自クラスにしか効かないので、新コントローラーにも 400/500 ハンドラーを置く） |
| dto | `dto/ApiResponses.kt` | `GuildMembersResponse(members, truncated)`、`GuildMemberDto(userId: String, displayName: String, avatarUrl: String)` |
| dto | `dto/DtoMappers.kt` | `GuildMember.toDto()` |
| config | `config/InternalApiTokenFilter.kt` | **変更なし**（パスが `/api/` 配下なので自動で保護される） |
| 永続化 | — | **なし**（DB・`init.sql`・`migrations/` は触らない。メンバーは保存しない） |

依存の向きは `docs/conventions/backend.md` のパッケージ表に従う: `controller → service`、`bot → service`（実装）、`service` は JDA を import しない。

### frontend
変更なし。BFF の API Route（`web/app/api/guilds/[guildId]/members/route.ts`）、`lib/api-types.ts` の型、E2E のモック（`web/e2e/support/mock-backend.ts`）は、#91 の後続タスク（代理登録 UI）で追加する前提。→ alerts に記載。

### ドキュメント（誰が直すか）

| ファイル | 内容 | 担当 |
|---|---|---|
| `DEPLOYMENT.md` §2「事前準備（Discord Developer Portal）」 | 手順に「`Bot` タブ → `Privileged Gateway Intents` → **Server Members Intent** を ON（再招待は不要）」を追加。§トラブルシューティング表に「起動時に `Disallowed intents` / close code 4014 で落ちる → Server Members Intent が OFF」を追加 | **doc-sync**（ルート直下の運用ドキュメント。開発者はコードに集中する） |
| `README.md`「Discord Bot の必要権限」 | Server Members Intent が必須であること、用途（代理登録のメンバー一覧）、bot が 100 サーバーを超えると Discord の審査が必要になる旨 | **doc-sync** |
| `kotlin/docs/API.md` | 「4. ギルドのメンバー一覧取得」を追加（パス・クエリ・レスポンス例・エラー 400/404/503・userId は文字列） | **doc-sync**（規約上「API を変えたら直す」対象。下の §3.4 の仕様をそのまま写す） |
| `docs/codebase/backend/routes-map.md`、`services-map.md` | ルート表に 1 行、サービス・ポート・エラーを追記 | **doc-sync** |

開発者（backend）は `docs/` と上記ドキュメントを触らない。doc-sync が担当できない運用の場合は、`DEPLOYMENT.md` / `README.md` / `kotlin/docs/API.md`（いずれも `docs/` 外）は backend 開発者が直してよい。

---

## 3. 技術方針

### 3.1 Intent とメンバーキャッシュ（JDA）

`JDABuilder.createDefault` の既定は `ChunkingFilter.NONE` と `MemberCachePolicy.DEFAULT`（オーナー・ボイス接続中など）なので、Intent を足すだけでは `guild.members` に一部しか入らない。

採用する構成:

```kotlin
JDABuilder.createDefault(token)
    .enableIntents(GatewayIntent.GUILD_MEMBERS)
    .setMemberCachePolicy(MemberCachePolicy.ALL)
    // ChunkingFilter は既定（NONE）のまま。起動時に全ギルドを chunk しない
    .addEventListeners(eventListener)
    .build()
```

- **遅延ロード**: API が呼ばれたとき、`guild.isLoaded` が false なら `guild.loadMembers()` で一度だけ全員を取得する。`MemberCachePolicy.ALL` なので取得した Member はキャッシュに残り、以降は `GUILD_MEMBER_ADD/UPDATE/REMOVE`（`GUILD_MEMBERS` Intent で届く）で最新に保たれる。
- **`ChunkingFilter.ALL` を採らない理由**: `awaitReady()` が全ギルドの chunk 完了まで待つため、`DiscordBot.READY_TIMEOUT_SECONDS = 30` を超えて起動失敗するリスクがある。
- `loadMembers()` の待ち方: `Task.setTimeout(...)` + `get()`。**HTTP のリクエストスレッドで呼ぶこと**（JDA のイベントスレッドでは `get()` 禁止。規約「`complete()` はイベントスレッドで使わない」と同じ理由）。タイムアウトは定数（例: `MEMBER_LOAD_TIMEOUT_SECONDS = 10L`）。タイムアウト・例外は `GuildMembersUnavailable` にして 503。例外は `logger.warn(..., e)` で記録する（握りつぶさない）。
- メモリ: 100 サーバー未満・NIKKE ユニオン規模（数十〜数百人）なら問題ない。100 サーバー超は Intent 自体に審査が要る（README に記載）。
- ゲートウェイのレート制限: `loadMembers` はギルドごとに初回だけ。同時に複数リクエストが来たときの重複ロードは許容する（初回のみで、JDA 側も Request Guild Members をキューする）。気になるなら `ConcurrentHashMap<Long, CompletableFuture>` で in-flight を共有してよいが必須ではない。
- API バージョン: JDA は既存の `6.4.1` のまま。新しい依存は追加しない。使う API（`GatewayIntent.GUILD_MEMBERS`、`MemberCachePolicy.ALL`、`Guild.isLoaded`、`Guild.loadMembers()`、`Guild.memberCache`、`Member.nickname`、`Member.effectiveAvatarUrl`、`User.globalName`、`User.name`、`User.isBot`、`User.isSystem`）は JDA 5 以降に存在する。コンパイルで確認すること。

### 3.2 起動失敗時の扱い
Portal で Intent が OFF だと、JDA は close code 4014（`DISALLOWED_INTENTS`）で切断し、`awaitReady` が失敗する → 既存の `catch (e: Exception)` で `IllegalStateException` になり起動を中止する（fail-closed で正しい）。
原因が分かるように、`waitForReadyWithTimeout` の `ExecutionException` / その他の例外のメッセージに「Developer Portal で Server Members Intent が ON か確認してください」というヒントを足す（ログ文言のみ。トークンは出さない）。

### 3.3 層の分け方（テスト可能性のため）

```
GuildMemberApiController ──> GuildMemberService ──> GuildMemberSource (interface, service パッケージ)
                                                        ▲
                                        JdaGuildMemberSource (bot パッケージ, JDA を保持)
```

- `GuildMemberSource`:
  ```kotlin
  interface GuildMemberSource {
      /** ギルドのメンバーを返す。bot がそのギルドにいなければ null。取得できなければ例外ではなく Result の失敗で返す */
      fun findMembers(guildId: Long): Result<List<GuildMemberSnapshot>?>
  }
  data class GuildMemberSnapshot(
      val userId: Long,
      val username: String,
      val globalName: String?,
      val nickname: String?,
      val avatarUrl: String,
      val isBot: Boolean,
  )
  ```
  （形は目安。`null` = ギルドなし、失敗 = 取得不能、が区別できればよい）
- `JdaGuildMemberSource`: `jda` 未設定（bot 無効・READY 前）→ 失敗（`GuildMembersUnavailable`）。`jda.getGuildById(guildId)` が null → `null`。Member → Snapshot の変換は `nickname = member.nickname`、`globalName = user.globalName`、`username = user.name`、`avatarUrl = member.effectiveAvatarUrl`（ギルドアバター → ユーザーアバター → 既定アバターの順で必ず値がある）、`isBot = user.isBot || user.isSystem`。
  - **`@ConditionalOnProperty` を付けない**: テスト（`discord.bot.enabled=false`）でも Bean が存在し、`GuildMemberService` の注入が壊れないようにする。`DiscordBot` は条件付きのまま、READY 後に `attach(jda)` する。`shutdown()` では参照を外さなくてよい（JDA が止まれば getGuildById が空になる）。
- `GuildMemberService.findGuildMembers(guildId: Long, query: String?, limit: Int, ): Result<GuildMemberPage>`:
  1. `source.findMembers(guildId)` → null なら `RaidDomainError.GuildNotFound`、失敗ならそのまま。
  2. `isBot` を除外。
  3. 表示名 = `nickname ?: globalName ?: username`（空文字・空白だけの値は次へ回す）。
  4. `query` があれば、`nickname` / `globalName` / `username` のいずれかに大文字小文字を無視して部分一致（`lowercase(Locale.ROOT)` で `contains`）。
  5. 表示名で並べ替え（同名は `userId` 昇順）して安定させる。
  6. 先頭 `limit` 件に切り、超えていたら `truncated = true`。
- `RaidDomainError` に追加:
  - `GuildNotFound`: 「サーバーが見つかりません」
  - `GuildMembersUnavailable`: 「メンバー一覧を取得できませんでした。しばらくしてから再度お試しください」

### 3.4 API 仕様

`GET /api/guilds/{guildId}/members?query={文字列}&limit={整数}`（`X-Internal-Api-Token` 必須）

| パラメーター | 仕様 |
|---|---|
| `guildId`（パス） | `Long`。型が合わなければ既存どおり 400 `BAD_REQUEST` |
| `query`（任意） | 前後の空白を除く。空なら絞り込みなし。**100 文字を超えたら 400**（Discord の名前は最大 32 文字なので十分） |
| `limit`（任意） | 既定 100、**1〜100 の範囲外は 400** |

レスポンス 200:
```json
{
  "members": [
    { "userId": "300000001", "displayName": "ニック", "avatarUrl": "https://cdn.discordapp.com/..." }
  ],
  "truncated": false
}
```

| ステータス | `error` | 条件 |
|---|---|---|
| 400 | `BAD_REQUEST` | `guildId` の型不正、`query` が長すぎる、`limit` が範囲外 |
| 401 | `UNAUTHORIZED` | トークンなし・不一致（既存フィルター） |
| 404 | `NOT_FOUND` | bot がそのギルドにいない（`GuildNotFound`） |
| 503 | `SERVICE_UNAVAILABLE` | bot が未起動・READY 前、`loadMembers` のタイムアウト・失敗（`GuildMembersUnavailable`） |
| 500 | `INTERNAL_SERVER_ERROR` | 想定外の例外 |

- 404 の判定は **JDA のギルド**で行う（DB の `guilds` テーブルではない）。`/raids` は DB 基準だが、メンバー一覧は bot が実際に参加しているかが正なので。
- 400 は、`IllegalArgumentException` を一般的に 400 に変換すると他のバグを隠すため、コントローラー内専用の例外（例: `private class InvalidRequestParameterException`）か、ハンドラー内で直接 `ResponseEntity.badRequest()` を返す形にする。
- `userId` は既存の Snowflake 規約どおり **文字列**。
- `Result` の失敗は `when` で `RaidDomainError` のサブクラスごとにステータスへ写す（網羅しない分岐は 500）。

### 3.5 セキュリティ（DEV review hardening）

- **認証**: パスが `/api/` 配下なので `InternalApiTokenFilter` で保護される（servletPath + pathInfo 判定）。`/api` 外にマッピングしないこと。回帰テストとして `InternalApiTokenFilterIntegrationTest` に「トークン無しの `/api/guilds/{id}/members` は 401」を 1 件足す。
- **認可の境界**: backend にはユーザー文脈がない。トークンを持つ呼び出し元は、bot がいる**どのギルドのメンバーでも**取れる。したがって **BFF 側で「セッションの `guildIds` にその guildId があること」と `isPrivileged`（`lib/permissions.ts`）を必ず確認してから呼ぶ**こと。これは #91 の後続（web）タスクの必須要件として alerts に記載する。このタスクでは web を作らないので、未保護の公開経路は増えない。
- **個人情報**: 表示名・アバター URL は個人に関わる情報。**ログに出さない**（`guildId`・件数・所要時間だけ）。`query` もログに出さない（出すなら長さだけ）。トークンもログに出さない（既存規約）。
- **入力の上限**: `query` の長さ・`limit` の範囲を制限し、巨大なレスポンスや高コストな絞り込みを防ぐ。
- **guildId の存在確認**: bot が参加していないギルドは 404。参加しているかどうかは token 保持者には分かるが、`/api/guilds` で既に一覧できるので新たな漏えいではない。
- **ゲートウェイのレート制限**: `loadMembers` はギルドごとに初回だけ（キャッシュ方針 ALL）。リクエストごとに `loadMembers` を呼ぶ実装にしないこと（DoS でレート制限を使い切られる）。
- `bot`・`system` ユーザーは除外する（自分自身の bot も含む）。

---

## 4. サブタスク

すべて **backend**（frontend のサブタスクはなし。並列化の対象なし）。

| ID | 内容 | stack | 作成・変更するファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| B1 | ポート・ドメイン型・エラーを追加 | backend | 新規 `service/GuildMemberSource.kt`（`GuildMemberSource`、`GuildMemberSnapshot`）、新規 `service/GuildMember.kt`（`GuildMember`、`GuildMemberPage`）、`service/RaidDomainError.kt` | — | 0.5h |
| B2 | `GuildMemberService`（除外・表示名・部分一致・並び替え・上限）と単体テスト | backend | 新規 `service/GuildMemberService.kt`、新規 `test/.../service/GuildMemberServiceTest.kt`（Spring なし、フェイクの `GuildMemberSource`） | B1 | 2h |
| B3 | JDA 実装と単体テスト | backend | 新規 `bot/JdaGuildMemberSource.kt`、新規 `test/.../bot/JdaGuildMemberSourceTest.kt`（Mockito で `JDA` / `Guild` / `Member` / `User` / `Task` をモック。JDA の型は interface なので可能） | B1 | 2h |
| B4 | `DiscordBot` に Intent・キャッシュ方針・`attach` を追加、起動失敗メッセージにヒント | backend | `bot/DiscordBot.kt` | B3 | 0.5h |
| B5 | DTO・コントローラー・コントローラーテスト・フィルター回帰テスト | backend | `dto/ApiResponses.kt`、`dto/DtoMappers.kt`、新規 `controller/GuildMemberApiController.kt`、新規 `test/.../controller/GuildMemberApiControllerTest.kt`、`test/.../config/InternalApiTokenFilterIntegrationTest.kt` | B2 | 2h |
| D1 | 運用ドキュメントと API 仕様、CodeBase マップ | doc-sync | `DEPLOYMENT.md`、`README.md`、`kotlin/docs/API.md`、`docs/codebase/backend/routes-map.md`、`docs/codebase/backend/services-map.md` | B5 | 1h |

### テストの指針
- `GuildMemberServiceTest`（`*Test.kt`、DB 不要）: 区切りコメント `// ---- findGuildMembers ----`、名前は `` `メンバー一覧_<条件>で<結果>` ``。
  - ニックネームがあればニックネーム、無ければグローバル名、どちらも無ければユーザー名（空白だけの値は次へ）
  - bot / system は除外
  - `query` はニックネーム・グローバル名・ユーザー名のどれに部分一致しても返り、大文字小文字を無視する
  - 一致なしは空配列
  - `limit` で切られたら `truncated = true`、ちょうどなら false
  - 並びが表示名順・同名は userId 順
  - ソースが null → `GuildNotFound`、ソースが失敗 → `GuildMembersUnavailable`（`assertTrue(result.isFailure)` + `assertInstanceOf`）
- `JdaGuildMemberSourceTest`: `attach` 前は失敗、ギルドなしは null、`isLoaded=false` なら `loadMembers` を呼ぶ・true なら呼ばない、Snapshot への変換（nickname / globalName / isBot / isSystem / effectiveAvatarUrl）、`loadMembers` のタイムアウトで失敗。
- `GuildMemberApiControllerTest`（`@SpringBootTest`、`webAppContextSetup`）: `GuildMemberSource` を `@TestConfiguration` の `@Primary` フェイク Bean か `@MockitoBean` で差し替える。
  - 200: `members[*].userId` が文字列、`displayName`・`avatarUrl` がある、bot が含まれない、`truncated`
  - `?query=` で絞られる
  - 404（ギルドなし）、503（取得不能）、400（`guildId=abc`、`limit=0`、`limit=101`、`query` 101 文字）
  - ギルド ID は他テストと被らない値（例: `70_094_001L` 〜）
- `InternalApiTokenFilterIntegrationTest`: トークン無しの `/api/guilds/70094001/members` が 401。
- DB を使わないテストに `@Transactional` は不要だが、`@SpringBootTest` のコンテキストは DB 接続を要するので、CI と同じく PostgreSQL 起動が前提になる点は既存と同じ。

---

## 5. 検証チェックリスト

規約（`docs/conventions/backend.md`・`testing.md`）由来:
- [ ] `service` / `controller` から JDA を直接 import していない（JDA は `bot` パッケージだけ）
- [ ] コンストラクタ注入 `private val`、`!!` なし、ワイルドカード import を新規に増やさない（新コントローラーで `gg.nikke.raid.bot.dto.*` を使わない）、複数行の引数に末尾カンマ
- [ ] 想定内の失敗は `Result` + `RaidDomainError` のサブクラス。汎用 `Exception` や文字列で返していない
- [ ] 想定外の例外は `logger.error(..., e)` で記録し、レスポンスは汎用メッセージ
- [ ] `service` の public 関数・コントローラーのハンドラーに KDoc（`@param` / `@return` / 失敗時のエラー）
- [ ] 定数は `companion object` の `private const val`（`MEMBER_LOAD_TIMEOUT_SECONDS`、`DEFAULT_LIMIT`、`MAX_LIMIT`、`MAX_QUERY_LENGTH`）
- [ ] テスト名は日本語・バッククォート、ID はテストごとに一意
- [ ] `set -a && . ./.env && set +a && cd kotlin && ./gradlew test --no-daemon` と `cd kotlin && ./gradlew build` が通る

JDA / 性能:
- [ ] `enableIntents(GatewayIntent.GUILD_MEMBERS)` と `setMemberCachePolicy(MemberCachePolicy.ALL)` を設定し、`ChunkingFilter` は既定（NONE）のまま
- [ ] `loadMembers()` は `isLoaded == false` のときだけ、HTTP スレッドで、タイムアウト付きで待つ
- [ ] `JdaGuildMemberSource` に `@ConditionalOnProperty` が無く、`discord.bot.enabled=false` のテストでコンテキストが起動する
- [ ] Intent が OFF のときの起動失敗メッセージに Portal 設定のヒントがある

セキュリティ:
- [ ] エンドポイントが `/api/` 配下にある。トークン無しで 401 になるテストがある
- [ ] 表示名・アバター URL・`query` の中身をログに出していない
- [ ] `query` の長さと `limit` の範囲を検査している（400 のテストあり）
- [ ] bot / system ユーザーが結果に含まれない
- [ ] リクエストごとに `loadMembers` を呼んでいない（2 回目の呼び出しで `loadMembers` が呼ばれないテストあり）

ガードテスト・静的検査は今回追加しないため、プローブ（すり抜け例・通る例）の対象はなし。

人間による確認（本物の Discord が要る。完了条件 1）:
- [ ] Portal で Server Members Intent を ON にした状態で bot が起動し、`/api/guilds/{実ギルド}/members` が bot 以外のメンバーを返す
- [ ] （任意）OFF の状態で起動するとヒント付きのエラーで止まる

ドキュメント（doc-sync、完了条件 3）:
- [ ] `DEPLOYMENT.md` §2 に Server Members Intent の手順、トラブルシューティングに 4014
- [ ] `README.md` に Intent 必須・100 サーバー超の審査の注意
- [ ] `kotlin/docs/API.md` にエンドポイント 4
- [ ] `docs/codebase/backend/routes-map.md` / `services-map.md` を更新

---

## 6. back / front の分担

| サブタスク | 担当 |
|---|---|
| B1〜B5 | backend 開発者（`kotlin/`） |
| — | frontend 開発者: **作業なし**（このタスクでは起動不要） |
| D1 | doc-sync |

---

## 7. 確認したファイル（CodeBase マップの外で開いたもの）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/bot/DiscordBot.kt`（JDABuilder の現状、READY 待ちのタイムアウト）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/controller/RaidApiController.kt`、`dto/ApiResponses.kt`（エラーハンドラーと DTO の書き方）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/config/InternalApiTokenFilter.kt`（保護範囲）
- `kotlin/src/main/kotlin/gg/nikke/raid/bot/service/RaidDomainError.kt`
- `kotlin/src/test/resources/application.properties`（`discord.bot.enabled=false` → テストでは JDA が無い）
- `kotlin/src/test/kotlin/gg/nikke/raid/bot/controller/RaidApiControllerTest.kt`、`config/InternalApiTokenFilterIntegrationTest.kt`（テストの型）
- `DEPLOYMENT.md` §2、`README.md`「Discord Bot の必要権限」（追記箇所）

ドキュメントとコードの食い違い: `docs/codebase/frontend/services-map.md` は `write-route-guard` を「文字列一致」と書いているが、`docs/conventions/testing.md` は AST ベースと書いている（本タスクには影響なし。doc-sync で揃えるとよい）。
