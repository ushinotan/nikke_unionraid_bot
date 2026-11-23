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
- 境界値（53bit を超える権限値、不正な形式の ID など）はケースを分けて書く。
