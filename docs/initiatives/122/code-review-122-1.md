## Code Review — 122 — attempt 1 — 2026-11-23

### Verdict: ❌ REJECTED

対象: `web/playwright.config.ts`、`web/e2e/**`、`web/package.json` / `package-lock.json`、`web/.gitignore`、`web/.dockerignore`、`.github/workflows/pr-test.yml`（未コミットの差分）。アプリ本体（`app/`、`lib/`、`proxy.ts`）、`eslint.config.mjs`、`kotlin/`、`docs/` は変更されていない。

---

### 🔴 Critical

#### C1. S2 のブラウザテストが本物の discord.com にアクセスしている（受け入れ条件 2「Discord がなくても動く」とプランの 3.3 S2 に反する）
- 場所: `web/e2e/auth.spec.ts:14-27`（とくに `:15-18` の `page.route("https://discord.com/**", ...)`）
- 原因: Chromium の Playwright では、リダイレクトの途中のリクエストは `page.route` / `context.route` で止められない。`/raids` → 307 → `/api/auth/discord` → 307 → `https://discord.com/...` の 2 回目以降のリダイレクトにはハンドラーが呼ばれない。
- 再現（レビュアーが確認）: 同じリダイレクトの流れをローカルで作って試した。`page.route` のハンドラーは **0 回**しか呼ばれず、ページの本文は本物の Discord の応答（`{"client_id": ["Value \"x\" is not snowflake."]}`）だった。`context.route` でも、`**/api/auth/discord` にルートを張っても同じ結果。今テストが通っているのは、ネットワークがあって本物の discord.com が応答しているからで、オフラインや Discord の障害時には落ちる。15 行目のコメント「本物の Discord には出ない」も事実と合わない。
- 直し方（どちらか）:
  1. ブラウザ側の確認を残す場合は、Chromium の DNS で discord.com を解決できないようにする。例: このテストだけの `test.use({ launchOptions: { args: ["--host-resolver-rules=MAP discord.com ~NOTFOUND"] } })`（describe かファイルの先頭で。ワーカー単位のフィクスチャなので別ワーカーになる）。`page.waitForRequest(r => r.url().startsWith("https://discord.com/api/oauth2/authorize"))` で認可 URL へのリクエストが出たこと（`client_id=e2e-dummy-client-id` と `redirect_uri` を含むこと）を確かめ、`page.goto` の `net::ERR_NAME_NOT_RESOLVED` は catch する。この方法で外に出ずにリクエストを捕まえられることは確認済み。config の `projects[].use` に入れる場合は、トップレベル `use` の `launchOptions.slowMo`（証跡モード）が浅いマージで消えないよう、`launchOptions` を 1 か所にまとめる。
  2. または、ブラウザの半分をやめて、2 回目のリダイレクトも `request.get("/api/auth/discord", { maxRedirects: 0 })` で確かめる（307 で、`location` が `https://discord.com/api/oauth2/authorize?` で始まり、`client_id=e2e-dummy-client-id` を含む）。
  - どちらの場合も、15 行目のコメントを実際の挙動に合わせる。

#### C2. `npm run test:e2e` を一度でも実行すると（成功しても）`npm run lint` が落ちる
- 場所: `web/eslint.config.mjs` の `globalIgnores`（今回の差分には入っていない）
- 原因: HTML レポーターは毎回 `playwright-report/trace/*.js`（トレースビューアー）を出力する。`npm run lint`（`eslint`）は `web/` 全体を対象にしているので、この生成物まで検査する。
- 再現（レビュアーが確認）: `--repeat-each=3` で全件成功したあとに `npm run lint` を実行すると **133 errors / 2822 warnings** で終了コードは非 0。エラーはすべて `playwright-report/trace/` から出ている。`--ignore-pattern 'playwright-report/**' --ignore-pattern 'test-results/**'` を付けると終了コード 0。CI はジョブが分かれているので通るが、ローカルや factory の self-test（E2E → lint の順）では「lint が今までどおり通る」（プラン 7 章）を満たさない。
- 直し方: `web/eslint.config.mjs` の `globalIgnores` に `"playwright-report/**"`、`"test-results/**"`、`"blob-report/**"`、`"e2e-results/**"`、`"playwright/.cache/**"` を足す（`.gitignore` と同じ集合にする）。プランの 2.2 で `eslint.config.mjs` の変更は認められている。

---

### 🟡 Important

#### I1. S4 の「ほかのギルド名が出ない」アサーションは絶対に落ちない
- 場所: `web/e2e/raids.spec.ts:19-20`
- モックの `GET /api/guilds` はギルド名を返さず（`mock-data.ts:36-41`）、BFF はセッションの `guilds` からしか名前を補わない。member のセッションに入っているのは GUILD_A だけなので、`GUILD_B.name` / `GUILD_ERROR.name` は BFF の絞り込みを外しても画面に出ない。P5 で落ちたのは `:18` の combobox のアサーションだけだった。
- 直し方: 画面は名前が無いときに guildId を表示する（`app/raids/page.tsx:147-154` の `getGuildDisplayName`）ので、`getByText(GUILD_B.name)` を `getByText(GUILD_B.id)`（GUILD_ERROR も同じ）にする。そうすれば絞り込みが外れたときに `<option>` のラベルで落ちる。または 2 行を消してコメントを combobox のアサーションに合わせる。

---

### 🟢 Suggestions
- `.github/workflows/pr-test.yml`（web-e2e の upload-artifact）: `path` に `web/e2e-results/` も入れると、失敗時に junit も手に入る。
- `web/playwright.config.ts:11`: `Boolean(process.env.EVIDENCE_MODE)` だと `EVIDENCE_MODE=0` でも証跡モードになる。気になるなら `=== "1"` などにする。
- `next start` を `output: "standalone"` のまま使うと、起動時に `"next start" does not work with "output: standalone"` の警告が出る（動作はする）。プラン 3.1 のとおり、この判断と理由を PR 本文に書く。
- P3 / P4 / P7 で落ちるケースの一部は 30 秒のテストタイムアウト待ちになる（`waitForRequest`、`locator.click`）。必須ではないが、`waitForRequest` に短い `timeout` を付けると失敗が早く分かる。

### 📄 Documentation
- doc-sync はまだ実行されていない（config の TBD とプラン 6 章の docs）。C1・C2 を直したあとに実行する。`docs/conventions/testing.md` の E2E の節には「外部へのリダイレクトは `page.route` では止まらない」ことも書いておくとよい。

---

### プローブの再実行結果（レビュアーが実行。変更はすべて元に戻し、`git diff` と e2e ファイルのハッシュが実行前と同じことを確認済み）
| # | 一時的な変更 | 期待 | 結果 |
|---|---|---|---|
| 基準 | 変更なし | 全件通る | ✅ 12 passed。`--repeat-each=3` で 36 passed |
| P1 | `proxy.ts:46` の所属チェックを `if (false)` に | S6 が落ちる | ✅ S6 だけ落ちた（Expected 403 / Received 200） |
| P2 | `protectedApiPaths` から `/api/guilds` を外す | S3 が落ちる | ✅ S3 の `/api/guilds`・`/api/guilds/{id}/raids`・不正クッキーと、S6 の 4 件が落ちた（401 のはずが 200） |
| P3 | `protectedPaths` を空に | S2 が落ちる | ✅ S2(a)（307 のはずが 200）と S2(b)（`waitForRequest` のタイムアウト）が落ちた |
| P4 | Next の env の `INTERNAL_API_TOKEN` を別の値に | S4 が落ちる | ✅ S4・S5・S6・S7 が落ちた（S7 はギルド一覧の時点で失敗するため。想定どおり） |
| P5 | `app/api/guilds/route.ts:16` の絞り込みを無効に | S4 が落ちる | ✅ S4 が落ちた。ただし落ちたのは combobox のアサーション（Received 1）だけで、名前のアサーションは効いていない（I1） |
| P6 | モックの GUILD_ERROR を 200 `{ raids: [] }` に | S7 が落ちる | ✅ S7 だけ落ちた |
| P7 | フィクスチャのクッキー秘密値を別の値に | S4・S5・S6 が落ちる | ✅ S4・S5・S6・S7 が落ちた（S6 は「GUILD_A は 200」の比較で 401 になって落ちた） |

追加の確認: `npm test` 139 pass、`npm audit --audit-level=high` 0 件、lockfile に増えたのは `@playwright/test` / `playwright` / `playwright-core` だけ。`npm run lint` は E2E を実行する前なら通るが、実行したあとは落ちる（C2）。

### Summary
プランどおりに作られている。定数は `constants.ts` に 1 か所でまとまり、モックは状態を持たず GET 以外を 405 にしている。秘密値はすべてダミーで、`.env*` に勝つよう env を明示している。ポートは 3100 / 18080 で、`reuseExistingServer: false` で fail-closed。CI ジョブは既存のジョブと同じ形で、actions のメジャーバージョンもそろっている。developer が自分でプローブを回して S4 と P6 の空振りを見つけて直したのもよかった。プローブ P1〜P7 はすべて期待どおり落ちた。

一方で、S2 のブラウザテストは本物の Discord に出ていて、受け入れ条件 2 と「本物の Discord には出ない」というコメントに反している（C1）。また、E2E を実行したあとは lint が落ちる（C2）。この 2 点はどちらも frontend の数行で直せる。I1 も同じ修正でまとめて直すとよい。
