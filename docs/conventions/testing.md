# テスト

- **必須**: バグ修正には、そのバグを再現するテストを付ける。
- **必須**: 業務ルール（`service`）、権限判定（`web/lib/permissions.ts`, `raid-authorization.ts`）、時刻の変換を変えたらテストを足す。
- **必須**: CI（`.github/workflows/pr-test.yml`）と同じコマンドが手元で通ることを確かめてから PR を出す。

## backend（JUnit 5）

実行には `init.sql` を流した PostgreSQL が要ります。

```bash
docker compose up -d db
```

```bash
set -a && . ./.env && set +a && cd kotlin && ./gradlew test --no-daemon
```

- 置き場所は本体と同じパッケージ（`kotlin/src/test/kotlin/gg/nikke/raid/bot/<層>/`）。
- ファイル名: DB を使うリポジトリのテストは `*IntegrationTest.kt`、それ以外は `*Test.kt`。
- DB を使うテストは次の形にする。`@Transactional` で各テストの終わりにロールバックされる。

  ```kotlin
  @SpringBootTest
  @Transactional
  @TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
  open class FooRepositoryIntegrationTest(
      private val fooRepository: FooRepository,
  ) { ... }
  ```

- テストメソッド名はバッククォートで囲んだ日本語にする。サービスのテストは `` `<操作>_<条件>で<結果>` `` の形（例: `` `レイド作成_期間が59分のときDurationTooShortエラーになる` ``）。
- 1 つのクラスの中でテスト対象のメソッドごとに区切りコメント（`// ---- createRaid ----`）を入れる。
- **必須**: テストで使うギルド ID・チャンネル ID は、テストごとにほかと被らない値にする（例: `70_000_001L`, `70_000_002L`）。ロールバックされても、並びや後片付けの失敗で干渉しないようにするため。
- **必須**: 現在時刻に依存するテストは `now` を引数で渡して固定する。`Thread.sleep` で待たない。
- 失敗の検証は `assertTrue(result.isFailure)` と `assertInstanceOf(RaidDomainError.Xxx::class.java, result.exceptionOrNull())` を組み合わせる。

## frontend（`node:test`）

```bash
cd web && npm test
```

```bash
cd web && npm run lint && npm run build
```

- 置き場所は `web/__tests__/<対象モジュール名>.test.ts`。
- `node:test` の `describe` / `it` と `node:assert` を使う。テストケース名は日本語で「条件なら結果」の形（例: `"オーナーなら true"`）。
- テストデータは小さなヘルパー関数で組み立てる（例: `sessionWith(guild)`）。
- **推奨**: テストしたい処理は Next.js の Request / Response に依存しない純粋関数として `lib/` に切り出す（例: `authorizeRaidEdit` を `requireRaidEditPermission` から分けている）。
- 共有フィクスチャは `web/__tests__/helpers/` に置く（`.test.ts` で終わらせない）。`raid-edit-fixtures.ts` に 本人（`selfMember`）/ 本人以外の一般メンバー（`otherMember`）/ 特権（`privilegedMember`、`ownerMember`）/ 所属外（`outsider`）のセッション（凍結済み。`loggedInAt` は固定時刻 `NOW`。期限切れの確認には `EXPIRED` を使う）と `mockBackend` がある。`mockBackend` は `global.fetch` を差し替えるので、`afterEach` で必ず元に戻す。`GET /api/raids/{id}` は `setRaid` のレイドと ID が一致したときだけ返し、それ以外の応答は `respond(method, path, { status, body })` で決める（例: 参加者レコードの持ち主を他人にして 403 を確かめる）。
- 書き込みルートのテストは 本人・本人以外の一般メンバー・特権・所属外 の 4 パターンを書き、403 のときバックエンドへ書き込みが呼ばれていない（`writes` が空）ことも確かめる。
- `write-route-guard.test.ts` は `app/` 配下の `route.(ts|tsx|js|jsx|mjs|mts)` を TypeScript の AST で検査する。
  - 書き込みメソッドのハンドラーは `export async function` の形で書き、ハンドラー直下に `const auth = await requireRaidEditPermission(...)` と、その直後の `if (!auth.ok) return auth.response;` を置く（入れ子の関数・try の中・文字列の中は数えない）。
  - ガードより前に呼べるのは `getSession`、ID の検証、入力の読み取り（`request.json()`、`params` など）、GET の `fetchFromBackend`（レコードの持ち主を調べるため）だけ。読み取り用の lib ヘルパーを足すときは、テスト内の `SAFE_CALLS_BEFORE_GUARD` に追加する。
  - 書き込みメソッドのハンドラーの外（GET ハンドラーやモジュール直下の関数）で、GET 以外の `fetchFromBackend` / `fetch` を呼ぶと失敗する（文字列リテラルの `https://` URL への `fetch` は外部 API として除く）。
  - 許可リストはメソッド単位で、許可リストのファイルは `fetch` / `fetchFromBackend` を呼べず、lib からは決められた名前（`getSession` など）しか import できない。
  - `"use server"` は検査の対象外なので、見つけたら失敗にしている。
- ガードテストが分かるのは「形」だけで、次のことは分からない。ここはルートごとのテスト（上の 4 パターンと、403 のとき `writes` が空であること）で担保する。ガードテストが通っても、ルートテストは省略しない。
  - ガードの後の処理が正しいか（`targetUserId` をレコードの持ち主から取っているか、転送先の raidId に `auth.raid.id` を使っているか など）
  - GET ハンドラーやモジュール直下の関数から、lib ヘルパー経由で書き込んでいないか
  - `page.tsx` などの form action
- 境界値（53bit を超える権限値、不正な形式の ID など）はケースを分けて書く。
