## Code Review — 122 — attempt 2 — 2026-11-23

### Verdict: ⚠️ APPROVED WITH NOTES

対象: 未コミットの差分（attempt 1 からの追加は `web/eslint.config.mjs`、`web/playwright.config.ts`、`web/e2e/auth.spec.ts`、`web/e2e/raids.spec.ts`、`.github/workflows/pr-test.yml`）。アプリ本体（`app/`、`lib/`、`proxy.ts`）、`kotlin/`、`docs/`（このレビューファイルを除く）は変わっていない。

---

### attempt 1 の指摘への対応
| 指摘 | 対応 | 確認結果 |
|---|---|---|
| C1 S2 が本物の discord.com に出ていた | `playwright.config.ts:46-50` で `--host-resolver-rules=MAP discord.com ~NOTFOUND`。`auth.spec.ts:14-26` は `waitForRequest` で認可 URL を待ち、goto の失敗は catch する | ✅ 外に出ていないことを確認した（下の「外部への通信の確認」） |
| C2 E2E のあと lint が落ちる | `eslint.config.mjs` の `globalIgnores` に Playwright の出力 5 つを追加 | ✅ E2E のあとの `npm run lint` は終了コード 0 |
| I1 S4 の名前のアサーションが効いていない | `raids.spec.ts:20-21` を `getByText(GUILD_B.id)` / `getByText(GUILD_ERROR.id)` に変更 | ✅ combobox のアサーションを一時的に外して P5 を当てると、`getByText('900000000000000002')` で落ちる。ID のアサーションだけでも効いている |
| 🟢 junit を artifact に | `pr-test.yml` の `path` に `web/e2e-results/` を追加 | ✅ |
| 🟢 `EVIDENCE_MODE=0` | `playwright.config.ts:11-12` で `"0"` / `"false"` を除外 | ✅ |

attempt 1 の C2 の補足（レビュアーの訂正）: attempt 1 で「成功した実行でも `playwright-report/trace/` ができる」と書いたが、これは直前の失敗した実行の残りだった。今回、全件成功した実行のあとには `playwright-report/trace/` は無かった。トレースビューアーの JS が出力されるのはトレースが残ったとき（失敗したとき）で、修正が必要だったことに変わりはない。

### 外部への通信の確認（C1）
- `npx playwright test e2e/auth.spec.ts -g "Discord" --trace on` を実行し、trace.zip の `*.network` を調べた。
  - 通信先のホストは `http://localhost:3100`（307 が 2 回）と `https://discord.com` の 1 件だけ。
  - discord.com へのリクエスト（`client_id=e2e-dummy-client-id&redirect_uri=http%3A%2F%2Flocalhost%3A3100%2F...`）は `status: -1`、`ERR_NAME_NOT_RESOLVED` で、応答は無い。名前解決の段階で止まっていて、discord.com には出ていない。
- 設定はトップレベルの `use.launchOptions` にあり、プロジェクトの `use`（`devices["Desktop Chrome"]` と viewport）では上書きされない。証跡モードの `slowMo` も同じオブジェクトにまとまっている。

### standalone の警告について（developer のメモとの食い違い）
- 警告は出ている。今回の基準実行でも、出力の 1 行目に次の行があった（`/tmp/claude-1000/-home-ushinotan-workspace-nikke-unionraid-bot--claude-worktrees-factory-dev-environment-e9fe28/b8f17da8-f9a1-4b56-abb1-21e84ec53006/scratchpad/out-base2.txt:1`）。
  `[WebServer] ⚠ "next start" does not work with "output: standalone" configuration. Use "node .next/standalone/server.js" instead.`
- 警告は出るが、`next start` は起動して 12 件すべてが通る。PR 本文には「`output: "standalone"` のまま `next start` を使う。起動時に Next が上の警告を出すが、E2E は動く（standalone の `server.js` を使わないのは `.next/static` と `public` のコピーが要らないため）」のように書くのが正確。developer が警告を見なかったのは、出力の最初の 1 行だけなので見落とした可能性が高い。

---

### 🔴 Critical
なし

### 🟡 Important
なし

### 🟢 Suggestions
- `web/e2e/auth.spec.ts:25`: `client_id` は `.not.toBeNull()` で確かめているだけ。`.toBe("e2e-dummy-client-id")` にして、`redirect_uri` が `${E2E_BASE_URL}/api/auth/discord/callback` であることも確かめると、OAuth 開始ルートの env の取り違えも検出できる（必須ではない）。
- `web/e2e/auth.spec.ts:22`: `.catch(() => undefined)` は名前解決以外の失敗も握りつぶす。`waitForRequest` が実際の判定をしているので問題はないが、`/net::ERR_NAME_NOT_RESOLVED/` だけを許すようにすると、テストの意図がはっきりする。
- P3 と P4 / P7 の一部は、30 秒のタイムアウトを待ってから落ちる（attempt 1 と同じ。必須ではない）。

### 📄 Documentation
- doc-sync はまだ実行されていない。プラン 6 章の値（config の TBD）と `docs/conventions/testing.md` の E2E の節を書く。その節に「外部へのリダイレクトは `page.route` では止まらないので、`--host-resolver-rules` で名前解決を止めている」と書いておくとよい。

---

### プローブの再実行結果（レビュアーが実行。変更はすべて元に戻し、`git diff` と e2e ファイルのハッシュが実行前と同じことを確認済み）
| # | 一時的な変更 | 期待 | 結果 |
|---|---|---|---|
| 基準 | 変更なし | 全件通る | ✅ 12 passed。`--repeat-each=3` で 36 passed |
| P1 | `proxy.ts:46` の所属チェックを `if (false)` に | S6 が落ちる | ✅ S6 だけ落ちた（Expected 403 / Received 200） |
| P2 | `protectedApiPaths` から `/api/guilds` を外す | S3 が落ちる | ✅ S3 の `/api/guilds`・`/api/guilds/{id}/raids`・不正クッキー（いずれも 401 のはずが 200）と、S6（403 のはずが 200）の 4 件が落ちた |
| P3 | `protectedPaths` を空に | S2 が落ちる | ✅ S2(a)（307 のはずが 200）と S2(b)（認可 URL へのリクエストが起きず、タイムアウト）が落ちた |
| P4 | Next の env の `INTERNAL_API_TOKEN` を別の値に | S4 が落ちる | ✅ S4・S5・S6・S7 が落ちた |
| P5 | `app/api/guilds/route.ts:16` の絞り込みを無効に | S4 が落ちる | ✅ S4 だけ落ちた。combobox のアサーションを外して当てても、`getByText(GUILD_B.id)` で落ちる |
| P6 | モックの GUILD_ERROR を 200 `{ raids: [] }` に | S7 が落ちる | ✅ S7 だけ落ちた |
| P7 | フィクスチャのクッキー秘密値を別の値に | S4・S5・S6 が落ちる | ✅ S4・S5・S6・S7 が落ちた |

追加の確認: E2E のあとの `npm run lint` は終了コード 0、`npm test` は 139 pass。終了後に 3100 / 18080 番ポートは空いている。

### Summary
attempt 1 の Critical 2 件と Important 1 件はすべて直り、確認方法も筋が通っている。discord.com へのリクエストは名前解決の段階で止まり、外部には出ていないことを trace で確かめた。プローブ P1〜P7 はすべて期待どおりに落ち、空振りしているアサーションも残っていない。この段階で残っている指摘は任意の改善だけ。次は、オーケストレーターが stack reviewer（`typescript-reviewer`、テストの認可まわりを見るなら `security-reviewer`）を起動し、そのあと doc-sync を実行する。PR 本文の standalone の記述は上のとおりに直す。

---

## スタックレビュアーの結果（オーケストレーターが追記）

実行したのは typescript-reviewer と security-reviewer。*.tsx / *.kt / SQL の変更は無いので、react / kotlin / database のレビュアーは対象外。CRITICAL / HIGH は 0 件のため、差し戻しにはしない。MEDIUM / LOW は最終レポートに 🟡 として残す。

| # | 重大度 | 指摘 | 場所 | 指摘元 |
|---|---|---|---|---|
| 1 | MEDIUM | レイド詳細 API（`/api/raids/{raidId}`）で他ギルドを参照したときの 403 が検証されていない。`RAID_B1` は用意してあるのに使っていない。選択ギルドを他ギルドに変えたときの拒否も検証していない | web/e2e/auth.spec.ts | TS + security |
| 2 | MEDIUM | `privileged` ペルソナを使うテストが無い。使うか、削除する | web/e2e/support/session.ts:35 | TS + security |
| 3 | MEDIUM | CI の `retries: 1` で、不安定なテストも成功扱いになる。`retries: 0` にするか `failOnFlakyTests` を使う | web/playwright.config.ts:30 | TS |
| 4 | MEDIUM | `web-e2e` ジョブに `timeout-minutes` が無い。upload-artifact に `if-no-files-found: ignore` を付ける | .github/workflows/pr-test.yml | security |
| 5 | LOW | `E2E_PORT` が空文字や数値以外のとき、ポート 0 や NaN になる | web/e2e/support/constants.ts:4-5 | TS |
| 6 | LOW | モックサーバーに listen エラーの処理と `closeAllConnections` が無い | web/e2e/support/mock-backend.ts:65-71 | TS |
| 7 | LOW | S2(b) で、名前解決のブロックが効いていること（ERR_NAME_NOT_RESOLVED）と `client_id` / `redirect_uri` の値を確認していない | web/e2e/auth.spec.ts:14-26 | TS + factory |
| 8 | LOW | `response.json()` の戻り値が any のまま。`location` が無いときに TypeError になる | web/e2e/auth.spec.ts | TS |
| 9 | LOW | ローカルの test:e2e が `.next` を上書きする（FYI） | web/playwright.config.ts:70 | TS |
| 10 | LOW | actions を SHA ではなくタグで固定している（既存ジョブと同じ）。`@playwright/test` は caret 指定。Next のサーバー側からの外部通信は遮断していない（いまは発生しない） | — | security |
