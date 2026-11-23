# Tech Lead プラン — #122 Playwright で web の E2E テストを入れる

## エグゼクティブサマリー
| 項目 | 内容 |
|---|---|
| 種別 | story |
| 複雑度 | 未設定（見立て: M） |
| ブランチ | `feature/122` |
| スコープ | frontend のみ（`web/` と `.github/workflows/pr-test.yml`）。`kotlin/` は変更しない |
| 影響範囲 | 新規: `web/playwright.config.ts`、`web/e2e/**`。変更: `web/package.json`、`web/package-lock.json`、`web/.gitignore`、`web/.dockerignore`、（必要なら）`web/eslint.config.mjs`、`.github/workflows/pr-test.yml`。アプリ本体（`app/`、`lib/`、`proxy.ts`）は変更しない |
| 見積もり | 約 1 日（実装 5〜6h + CI 調整 1〜2h） |
| リスク | 中（`output: "standalone"` での `next start`、ポートの衝突、`.env*` の混入、eslint の `use` 誤検知。下の「リスク」を参照） |
| ブロッカー | なし（`depends_on` なし） |
| 受け入れ条件の絞り込み | なし（7 シナリオすべて対象）。解釈は 1 点だけ: 「bot API がエラー」はレイド一覧 API（`/api/guilds/{guildId}/raids`）の 500 で確かめる（理由は 3.3） |

---

## 1. ブループリントの要約（Issue 本文より）
`web/` には `node:test` の単体テストしかない。Playwright を入れて、ログイン後の主な画面遷移と権限まわりをブラウザで確かめる。Discord OAuth・bot（Kotlin）・DB は本物を使わない。
- ログイン: テスト用の `SESSION_SECRET` で iron-session のクッキーを作り、ブラウザに直接セットする
- bot API: `BACKEND_URL` をモックサーバーに向け、固定のレスポンスを返す

### 受け入れ条件
- [ ] `cd web && npm run test:e2e` で、下の 7 シナリオがローカルで通る
- [ ] Discord・bot・DB がなくても E2E が動く
- [ ] PR の CI で E2E が動き、失敗時にトレースが artifact に残る
- [ ] `docs/factory.config.md` の E2E 関連の TBD が埋まっている（doc-sync が書く。値は 6 章）

### シナリオ（Issue の初期範囲。すべて対象）
| # | シナリオ |
|---|---|
| S1 | トップページ（`/`）が表示され、`/raids` へのリンクがある |
| S2 | 未ログインで `/raids` を開くと `/api/auth/discord` にリダイレクトされる |
| S3 | 未ログインで `/api/guilds` などの保護された API を呼ぶと 401 が返る |
| S4 | ログイン済みで `/raids` を開くと、ギルドとレイド一覧が表示される |
| S5 | レイド一覧からレイド詳細（`/raids/[id]`）に遷移し、レイド名が表示される |
| S6 | 所属していないギルドの `/api/guilds/{guildId}/raids` は 403 が返る |
| S7 | bot API がエラーを返したときに、エラー表示と再読み込みボタンが出る |

編集系 UI（#97 / #98 / #104）の出し分けは対象外（Issue でも別 ISSUE と明記）。ただしクッキーヘルパーには特権ユーザーのペルソナを今から用意する。

---

## 2. 影響マップ

### 2.1 テスト対象（変更しない。CodeBase と実コードで確認済み）
| 対象 | ファイル | E2E に関係する挙動 |
|---|---|---|
| トップ | `web/app/page.tsx` | `<Link href="/raids">レイド一覧を見る</Link>` |
| レイド一覧 | `web/app/raids/page.tsx` | `GET /api/guilds` と `GET /api/session/selected-guild` を並行で呼び、`POST /api/session/selected-guild` のあと `GET /api/guilds/{id}/raids`。ギルドが 1 件なら `<h1>` にギルド名、2 件以上なら `<select id="guild-select">`。レイド名は `<Link href="/raids/{id}">`。レイド一覧の取得に失敗すると「エラー」「レイド一覧の取得に失敗しました」と `再読み込み` ボタン（`secondaryButtonClass`）。ギルド一覧の取得失敗でも `再読み込み`（`primaryButtonClass`） |
| レイド詳細 | `web/app/raids/[id]/page.tsx` | `GET /api/raids/{id}`。`<h1>{raid.raidName}</h1>`。403 なら「このレイドにアクセスする権限がありません」 |
| proxy | `web/proxy.ts` | 保護対象 `/raids`、`/api/guilds`、`/api/raids`、`/api/session`。未ログインなら画面は `getPublicOrigin` + `/api/auth/discord` へ 307、API は 401 `{ error: "Unauthorized" }`。`/api/guilds/{id}...` はセッションの `guildIds` に無ければ 403 |
| OAuth 開始 | `web/app/api/auth/discord/route.ts` | `DISCORD_CLIENT_ID`（モジュール読み込み時に評価）が無いと 500。あれば `https://discord.com/api/oauth2/authorize?...` へリダイレクト |
| BFF | `web/app/api/guilds/route.ts`、`guilds/[guildId]/raids/route.ts`、`raids/[raidId]/route.ts` | サーバー側で `fetchFromBackend` から bot を呼ぶ。`/api/guilds` はセッションの `guildIds` で絞り込み、名前はセッションの `guilds` から補う |
| バックエンド呼び出し | `web/lib/backend-client.ts` | `BACKEND_URL`（モジュール読み込み時、既定 `http://localhost:8080`）、`X-Internal-Api-Token` は呼び出しのたびに `INTERNAL_API_TOKEN` から付ける。5xx は `handleBackendError` が `BACKEND_ERROR` にする |
| セッション | `web/lib/session.ts` | Cookie `nikke_raid_session`、`password: SESSION_SECRET`、`secure: NODE_ENV==="production" && getConfiguredOrigin() が https`、`sameSite: "lax"` |
| 型 | `web/lib/api-types.ts` | `GuildsResponse`、`RaidsResponse`、`RaidDetailResponse`。モックのデータはこの型に `satisfies` で合わせる |

**ポイント:** bot の呼び出しはブラウザではなく **Next.js サーバー**から出るので、Playwright の `page.route()` では止められない。モックは **本物の HTTP サーバー**にして `BACKEND_URL` を向ける。

### 2.2 新規・変更ファイル
| ファイル | 種別 | 内容 |
|---|---|---|
| `web/playwright.config.ts` | 新規 | 設定（3.1） |
| `web/e2e/support/constants.ts` | 新規 | ポート、テスト用の秘密値、ギルド ID・レイド ID・名前。設定・モック・スペックで共有し、二重に定義しない |
| `web/e2e/support/mock-backend.ts` | 新規 | bot API のモックサーバー（`node:http`、3.2） |
| `web/e2e/support/mock-data.ts` | 新規 | 固定のレスポンス（`import type` で `lib/api-types.ts` に合わせる） |
| `web/e2e/support/session.ts` | 新規 | ペルソナとセッションクッキーを作る処理（3.4） |
| `web/e2e/support/fixtures.ts` | 新規 | `test.extend` で `loginAs(persona)` を用意する |
| `web/e2e/public.spec.ts` | 新規 | S1 |
| `web/e2e/auth.spec.ts` | 新規 | S2、S3、S6 |
| `web/e2e/raids.spec.ts` | 新規 | S4、S5、S7 |
| `web/package.json` / `package-lock.json` | 変更 | `@playwright/test` を devDependencies に追加し、`"test:e2e": "playwright test"` を足す |
| `web/.gitignore` | 変更 | `/test-results/`、`/playwright-report/`、`/blob-report/`、`/e2e-results/`、`/playwright/.cache/` |
| `web/.dockerignore` | 変更 | 上と同じものに加えて `e2e`、`playwright.config.ts`（本番イメージに入れない） |
| `web/eslint.config.mjs` | 必要なら変更 | 3.6 の誤検知が出たときだけ、`e2e/**` に限って `react-hooks/rules-of-hooks` を切る |
| `.github/workflows/pr-test.yml` | 変更 | `web-e2e` ジョブを追加（3.5） |

backend・DB・マイグレーション・認可・スコープ・オブザーバー: **影響なし**（本体のコードを変えないため）。

---

## 3. 技術的なやり方

### 3.1 `playwright.config.ts`
- `testDir: "./e2e"`、`testMatch: "**/*.spec.ts"`（`node:test` の `__tests__/**/*.test.ts` とは重ならない）
- `fullyParallel: true`、`forbidOnly: !!process.env.CI`、`retries: process.env.CI ? 1 : 0`、`workers: process.env.CI ? 2 : undefined`
- `outputDir: "test-results"`
- `reporter`: `[["list"], ["html", { outputFolder: "playwright-report", open: "never" }], ["junit", { outputFile: "e2e-results/junit.xml" }]]`（html と junit の出力先は `outputDir` の外にする。Playwright は実行の最初に `outputDir` を消すため）
- `use`:
  - `baseURL: http://localhost:${E2E_PORT}`、`locale: "ja-JP"`、`timezoneId: "Asia/Tokyo"`
  - 普段: `trace: "retain-on-failure"`、`screenshot: "only-on-failure"`、`video: "retain-on-failure"`
  - `EVIDENCE_MODE`（`config.evidence.mode_var`）が入っていれば `trace` / `screenshot` / `video` を `"on"`、`viewport: { width: 1920, height: 1080 }`、`launchOptions.slowMo: Number(process.env.SLOW_MO ?? 500)`（`config.evidence.slowmo_var` / `slowmo` / `resolution`）
- `projects`: Chromium だけ（`devices["Desktop Chrome"]`）
- **ポート**: Next は `E2E_PORT`（既定 **3100**）、モックは `E2E_BACKEND_PORT`（既定 **18080**）。docker のスタック（web 3000 / bot 8080）が別の worktree で動いていても衝突しないようにする
- `webServer` は配列で 2 つ:
  1. モック: `command: "node --import tsx e2e/support/mock-backend.ts"`（`tsx` は既存の devDependency）、`url: http://127.0.0.1:${E2E_BACKEND_PORT}/health`
  2. Next: `command: "npm run build && npm run start -- --port ${E2E_PORT}"`、`url: ${baseURL}`、`timeout: 180_000`
  - どちらも `reuseExistingServer: false`。別の環境変数で起動済みのサーバーに黙ってつながると、結果を信用できなくなるため。ポートが使用中ならエラーで止まり、`E2E_PORT` で変えられる
  - Next の `env` には次を**すべて明示的に**入れる。`.env*` に本物の値があっても、Next はすでに入っている `process.env` を上書きしないので、明示した値が勝つ:
    - `SESSION_SECRET`: 32 文字以上のダミー（例 `e2e-session-secret-0123456789abcdef0123`）
    - `BACKEND_URL`: `http://127.0.0.1:${E2E_BACKEND_PORT}`
    - `INTERNAL_API_TOKEN`: `e2e-internal-token`（モックが照合する）
    - `APP_BASE_URL`: baseURL（リダイレクト先を決まった値にする。http なので Secure Cookie にならない）
    - `DISCORD_REDIRECT_URI`: `${baseURL}/api/auth/discord/callback`
    - `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET`: ダミー（`/api/auth/discord` が 500 ではなく本番と同じく discord.com へリダイレクトするように）
  - 環境変数はビルド時と起動時の両方にかかる（`command` は 1 つのシェルなので）。`BACKEND_URL` / `INTERNAL_API_TOKEN` は `NEXT_PUBLIC_` ではないのでビルドで埋め込まれないが、念のため両方で同じ値にしておく
- **`next start` と `output: "standalone"`**: `next.config.ts` は `output: "standalone"`。Next はこの組み合わせに警告を出すが、起動はできるはず。**実装前に `web/node_modules/next/dist/cli/next-start.js` と `node_modules/next/dist/docs/` を確認すること**（`web/AGENTS.md`）。起動できないときの代わりは `node .next/standalone/server.js` だが、`.next/static` と `public` のコピーが必要になる（`web/Dockerfile` と同じ形）。この場合は `PORT` / `HOSTNAME` を env で渡す。どちらにしたかと理由を PR に書く
- `next dev` は使わない（本番に近い動きで確かめるため。また `next dev` は `web/AGENTS.md` を書き換える）

### 3.2 bot API のモックサーバー（`e2e/support/mock-backend.ts`）
- `node:http` だけで書く（依存を増やさない）。待ち受けは `127.0.0.1:${E2E_BACKEND_PORT}`。`SIGTERM` / `SIGINT` で閉じる
- **状態を持たない**。応答はリクエストのパスとギルド ID だけで決まるので、並列で動かしても互いに影響しない
- ルート（bot の実 API に合わせる。`kotlin/docs/API.md` と `lib/api-types.ts`）:
  | パス | 応答 |
  |---|---|
  | `GET /health` | 200（認証なし。bot と同じ） |
  | `/api/**` で `X-Internal-Api-Token` が `INTERNAL_API_TOKEN` と違う | 401 `{ error: "UNAUTHORIZED", message }`（BFF がトークンを付けていることも確かめられる） |
  | `GET /api/guilds` | `{ guilds: [GUILD_A, GUILD_B, GUILD_ERROR] }`（BFF の絞り込みを通るのは、セッションで所属しているものだけ） |
  | `GET /api/guilds/{GUILD_A}/raids` | 2 件（例 `id: 101` 「E2E レイド Alpha-1」、`id: 102` 「E2E レイド Alpha-2」） |
  | `GET /api/guilds/{GUILD_B}/raids` | 1 件（proxy が 403 で止めるので、本来は呼ばれない） |
  | `GET /api/guilds/{GUILD_ERROR}/raids` | **500** `{ error: "INTERNAL_ERROR", message: "mock failure" }` |
  | `GET /api/raids/101`、`/102` | `RaidDetailResponse`（`reports` を数件、`participants: []`） |
  | `GET /api/raids/{GUILD_B のレイド}` | 詳細（BFF が 403 にする。おまけのシナリオ用） |
  | その他の GET | 404 `{ error: "NOT_FOUND", message }` |
  | GET 以外 | 405（E2E から bot への書き込みが起きないことの保険） |
- データは `e2e/support/mock-data.ts` に置き、`satisfies GuildsResponse` などで型をチェックする（`tsconfig` の `include` が `**/*.ts` なので、`next build` の型チェックでもずれを検出できる）
- 日付は固定の過去日にする。画面の日付（`formatDateTime`）や「進行中」バッジ（`isRaidActive` は現在時刻に依存）はアサーションに使わない

### 3.3 シナリオの実装
| # | スペック | ペルソナ | やり方とアサーション |
|---|---|---|---|
| S1 | `public.spec.ts` | 未ログイン | `page.goto("/")`、`getByRole("heading", { level: 1 })` が見える。`getByRole("link", { name: "レイド一覧を見る" })` の `href` が `/raids` |
| S2 | `auth.spec.ts` | 未ログイン | (a) `request.get("/raids", { maxRedirects: 0 })` が 307 で、`location` が `${baseURL}/api/auth/discord`。(b) ブラウザ: `page.route("https://discord.com/**", r => r.fulfill({ status: 200, body: "discord stub" }))` で外部へ出ないようにし、`page.goto("/raids")` のあと `/api/auth/discord` へのリクエストが起きたことと、最終 URL が discord.com の authorize であることを確かめる。**本物の Discord には出ない** |
| S3 | `auth.spec.ts` | 未ログイン / 不正なクッキー | `/api/guilds`、`/api/guilds/{GUILD_A}/raids`、`/api/raids/101`、`/api/session/selected-guild` がそれぞれ 401 で、本文の `error` が `"Unauthorized"`。`nikke_raid_session=invalid` でも 401 |
| S4 | `raids.spec.ts` | 一般メンバー（`GUILD_A` だけに所属） | `page.goto("/raids")`、`<h1>` が `GUILD_A` の名前（セッションの `guilds[].name`）、2 件のレイド名のリンクが見える。`GUILD_B` / `GUILD_ERROR` の名前は出ない（BFF の絞り込みも確かめる） |
| S5 | `raids.spec.ts` | 一般メンバー | S4 の画面からレイド名のリンクをクリック、`toHaveURL(/\/raids\/101$/)`、`getByRole("heading", { level: 1, name: "E2E レイド Alpha-1" })` |
| S6 | `auth.spec.ts` | 一般メンバー | `page.request.get("/api/guilds/{GUILD_B}/raids")`（`context` のクッキーを共有する）が 403、`error: "Forbidden"`。比較のため `GUILD_A` は 200（クッキーが効いていることを示し、401 で偶然通る形にしない） |
| S7 | `raids.spec.ts` | エラー用メンバー（`GUILD_ERROR` だけに所属） | `page.goto("/raids")`、「レイド一覧の取得に失敗しました」と `getByRole("button", { name: "再読み込み" })` が見える |

- **S7 の解釈**: `/api/guilds` は BFF がユーザー情報を付けずに呼ぶので、状態を持たないモックではユーザーごとに失敗させられない。ギルド ID で決まるレイド一覧の 500 なら並列でも安定する。これで「bot API エラー → エラー表示と再読み込みボタン」を満たす（ギルド一覧の失敗表示は対象外。必要になったら、モックに状態を持たせて `workers: 1` のスペックに分ける）。
- 任意（時間があれば）: 一般メンバーで `/raids/{GUILD_B のレイド}` を開くと「このレイドにアクセスする権限がありません」（BFF の 403）。
- セレクタは role とテキストで書く。`data-testid` を足すためにアプリのコードを変えない。

### 3.4 セッションクッキーのヘルパー（`e2e/support/session.ts`、`fixtures.ts`）
- `sealData(session, { password: E2E_SESSION_SECRET })`（`iron-session`。`web/__tests__/proxy.test.ts` の `sessionCookie` と同じやり方）でクッキーの値を作る。秘密値は `constants.ts` から取り、`playwright.config.ts` の webServer の env と**同じ定数**を使う
- ペルソナ（`SessionData` 型、`import type` で `@/lib/session` から。相対パスでもよい）:
  - `anonymous`: クッキーを付けない
  - `member`: `{ userId, guildIds: [GUILD_A], guilds: [{ id, name, owner: false, permissions: "0" }], loggedInAt: Date.now() }`
  - `privileged`: 同じ形で `permissions: "8"`（管理者）。**`loggedInAt` はクッキーを作るときの `Date.now()`** にする（`__tests__` のフィクスチャの固定値 `NOW` を使うと、`PRIVILEGE_MAX_AGE_MS` を過ぎて特権なしになる）。今回のシナリオでは使わないが、#97 / #98 / #104 の出し分けシナリオで使う
  - `errorMember`: `GUILD_ERROR` だけに所属
- `fixtures.ts`: `test.extend<{ loginAs: (p: Persona) => Promise<void> }>` で `context.addCookies([{ name: "nikke_raid_session", value, url: baseURL, httpOnly: true, sameSite: "Lax" }])`
- `__tests__/helpers/raid-edit-fixtures.ts` は import しない（固定時刻や凍結は単体テスト向けのため）。ID の値はそろえてもよい

### 3.5 CI（`.github/workflows/pr-test.yml`）
新しく `web-e2e` ジョブを作る（既存の `web` ジョブに入れると lint / test / build と直列になって遅くなり、artifact も分けにくいため）。
- `if: ${{ github.event.pull_request.draft == false }}`、`runs-on: ubuntu-latest`、`defaults.run.working-directory: web`
- 手順: checkout → `../scripts/web-node-version.sh` で Node のバージョンを決める（`web` ジョブと同じ） → `actions/setup-node`（npm キャッシュ） → `npm ci` → `npx playwright install --with-deps chromium` → `npm run test:e2e`（`CI=true` は Actions が入れる）
- 失敗時の artifact: `actions/upload-artifact`（メジャーはリポジトリのほかの actions と同じく最新のものにする）を `if: ${{ failure() }}` で、`path: web/test-results/`、`web/playwright-report/`、`retention-days: 7`、`name: playwright-report`
- 任意: `~/.cache/ms-playwright` を Playwright のバージョンをキーにキャッシュする
- `permissions: contents: read` は今のままでよい。秘密値は使わない（全部ダミー）
- DB・docker・Discord は使わない（受け入れ条件 2）

### 3.6 リスクと対策
| リスク | 対策 |
|---|---|
| `output: "standalone"` で `next start` が動かない / 警告が出る | 3.1 のとおり先に確認する。だめなら `node .next/standalone/server.js` に切り替える |
| ローカルでポートが衝突する（docker の web 3000 / bot 8080） | 既定を 3100 / 18080 にし、`E2E_PORT` / `E2E_BACKEND_PORT` で変えられるようにする |
| ローカルの `.env*` の値（https の `APP_BASE_URL` など）が混ざり、Secure Cookie になってクッキーが送られない | 3.1 の env をすべて明示する |
| `eslint-plugin-react-hooks` が Playwright のフィクスチャ引数 `use(...)` を React の `use` フックと誤認する（`rules-of-hooks`） | 引数名を変える（例 `async ({ context }, provide) => { await provide(fn) }`）か、`e2e/**` に限ってルールを切る。どちらにしたかを PR に書く |
| `next build` の型チェックが `e2e/**`・`playwright.config.ts` も対象にする（`tsconfig` の `**/*.ts`） | `@playwright/test` は devDependency なので型は解決する。`any` は使わない |
| Docker イメージに E2E のコードが入る | `.dockerignore` に入れる。`web-docker` ジョブがそのまま通ることを確かめる |
| バージョン | `@playwright/test` の最新安定版を入れる（`npm i -D @playwright/test`）。開発依存なので `npm audit --omit=dev` には影響しない。`npm audit` の high 以上が増えていないかは確認する |
| フレーク | 状態を持たないモック、web-first assertion（`toBeVisible` / `toHaveURL`）、`waitForTimeout` は使わない |
| セキュリティ | 秘密値はダミーだけ。ヘルパーは `e2e/` の中だけで、本番のバンドルに入らない（`app/` や `lib/` から import しない）。トークン・クッキーをログに出さない |

---

## 4. サブタスク
すべて **frontend**（developer 1 人。backend の developer は不要）。

| ID | 内容 | stack | 作成・変更するファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| F1 | `@playwright/test` を追加、`test:e2e` スクリプト、`.gitignore` / `.dockerignore` | frontend | `web/package.json`、`package-lock.json`、`.gitignore`、`.dockerignore` | - | 0.5h |
| F2 | 共有の定数と、モックのデータ・サーバー | frontend | `web/e2e/support/{constants,mock-data,mock-backend}.ts` | F1 | 1.5h |
| F3 | セッションクッキーのヘルパーとフィクスチャ | frontend | `web/e2e/support/{session,fixtures}.ts` | F1 | 1h |
| F4 | `playwright.config.ts`（webServer 2 つ、env、evidence の切り替え）。standalone の確認 | frontend | `web/playwright.config.ts` | F2 | 1h |
| F5 | スペック S1〜S7 | frontend | `web/e2e/{public,auth,raids}.spec.ts` | F3, F4 | 1.5h |
| F6 | lint の誤検知が出れば対処 | frontend | `web/eslint.config.mjs`（必要なときだけ） | F5 | 0.25h |
| F7 | CI の `web-e2e` ジョブと artifact | frontend | `.github/workflows/pr-test.yml` | F5 | 1h |
| D1 | （doc-sync）config の TBD と docs | docs | 6 章 | F7 | doc-sync |

## 5. バック / フロントの分担
- **backend**: なし
- **frontend**: F1〜F7（`.github/workflows/pr-test.yml` を含む）

---

## 6. doc-sync が書く値（developer は `docs/factory.config.md` を変えない）
`docs/factory.config.md`:
- `stack.frontend.e2e`: `"Playwright (@playwright/test, Chromium) — web/e2e/*.spec.ts、cd web && npm run test:e2e。bot API は e2e/support/mock-backend.ts、ログインは iron-session のクッキーを直接セット"`
- `evidence.artifacts.videos`: `web/test-results`（テストごとのサブフォルダに `*.webm`。普段は失敗したときだけ、`EVIDENCE_MODE` を入れると全部）
- `evidence.artifacts.screenshots`: `web/test-results`（`*.png`。同じ条件）
- `evidence.artifacts.frontend_result`: `web/e2e-results/junit.xml`（HTML レポートは `web/playwright-report`）
- 任意: `tests.layout.frontend_e2e: 'web/e2e/*.spec.ts'`

ほかの docs:
- `docs/conventions/testing.md`: 「frontend（E2E / Playwright）」の節（実行コマンド、モックとペルソナの足し方、ポートと env、`EVIDENCE_MODE`、本物の外部サービスに出ないこと、アプリのコードに `data-testid` を足さず role で選ぶこと）
- `docs/codebase/frontend/services-map.md` の「テスト」と `docs/codebase/overview.md` の「スタック」に E2E を追記

---

## 7. 確認用チェックリスト

### 動作
- [ ] `cd web && npm run test:e2e` で S1〜S7 がすべて通る（docker のスタックを止めた状態と、動かしている状態の両方で）
- [ ] DB・bot・Discord なしで動く（モックのポートだけを使う。discord.com へのリクエストは `page.route` で止めている）
- [ ] `npm test`（node:test）、`npm run lint`、`npm run build` が今までどおり通る
- [ ] `EVIDENCE_MODE=1 npm run test:e2e` で `web/test-results` に動画とスクリーンショットが出て、`web/e2e-results/junit.xml` ができる
- [ ] Docker イメージに `e2e/` が入らない（`web-docker` ジョブが通る）
- [ ] CI の `web-e2e` が通る。わざと失敗させたときに `playwright-report` の artifact（trace.zip を含む）が残ることを一度確かめ、結果を PR に書く（確かめたあと元に戻す）

### テストが空振りしていないかのプローブ（developer が実行し、code-reviewer がもう一度実行する。結果を PR に書く。変更は確認後に戻す）
E2E は静的検査ではないが、権限まわりのテストなので、「壊すと落ちる」ことを確かめる。
| # | 一時的な変更 | 期待 |
|---|---|---|
| P1 | `proxy.ts` のギルド所属チェック（403）を外す | S6 が**落ちる**（モックの `GUILD_B` が 200 を返すため） |
| P2 | `proxy.ts` の `protectedApiPaths` から `/api/guilds` を外す | S3 が**落ちる** |
| P3 | `proxy.ts` の `protectedPaths` を空にする | S2 が**落ちる** |
| P4 | Next の env の `INTERNAL_API_TOKEN` をモックと違う値にする | S4 が**落ちる**（モックが 401 → 一覧がエラー） |
| P5 | `/api/guilds` の BFF の `guildIds` による絞り込みを外す | S4 が**落ちる**（`GUILD_B` が見えて 2 件以上になり `<select>` に変わる。または「GUILD_B の名前が出ない」というアサーションが落ちる） |
| P6 | モックの `GUILD_ERROR` を 200 にする | S7 が**落ちる** |
| P7 | クッキーの秘密値を webServer と違う値にする | S4・S5・S6 が**落ちる**（未ログイン扱い。S6 は「GUILD_A は 200」の比較で、401 が偶然通ることを防いでいる） |
| 通るはず | 変更なし（2 回続けて、`--repeat-each=3` で並列実行） | すべて通る（フレークしない） |

### 規約（`docs/conventions/frontend.md` / `testing.md`）
- [ ] TypeScript strict、`any` を使わない、型だけの import は `import type`、ダブルクォート・セミコロン・インデント 2 スペース、ファイル名は kebab-case
- [ ] テスト名は日本語で「条件なら結果」の形（例 `"未ログインで /raids を開くとログインへリダイレクトする"`）
- [ ] 定数（秘密値・ポート・ID）は `e2e/support/constants.ts` の 1 か所で定義し、config・モック・スペックがそこから import する
- [ ] アプリ本体（`app/`、`lib/`、`proxy.ts`）を変えていない
- [ ] モックは GET 以外を 405 にする（E2E から書き込みが起きない）
- [ ] トークン・クッキー・セッションの中身をログに出さない
