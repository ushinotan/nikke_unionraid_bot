# Tech Lead 計画 — #75 [Security] 開発依存の脆弱性(zod / minimatch)を解消する

## 概要

| 項目 | 内容 |
|---|---|
| 種別 | bug（セキュリティ、依存更新） |
| 複雑さ | 小（コード変更なし。`web/package.json` と `web/package-lock.json` だけ） |
| ブランチ | `bugfix/75` |
| スコープ | frontend（`web/`）のみ。開発依存（ESLint まわり）だけで、本番イメージ（`output: "standalone"`）には入らない |
| 見積もり | 1〜2 時間（overrides を試して lint を確かめる時間を含む） |
| リスク | 低（zod 更新、typescript-estree の minimatch 10→11）／中（minimatch 3→11 の強制。ESLint プラグインの実行時エラーの恐れ） |
| ブロッカー | なし（`depends_on` なし） |
| コミット種別 | `chore🔒(web): ...`（`docs/conventions/general.md` のセキュリティ・依存更新） |

## 1. 診断

### 症状と期待

- 症状: `cd web && npm audit` で high 1 件（zod）、moderate 16 件（すべて minimatch 起点）が出る。
- 期待: high 以上が 0 件。moderate が残るなら理由と追跡先が Issue に記録されている。

### 再現

```
cd web && npm ci && npm audit
```

### 原因（`web/package-lock.json` の解決結果）

`web/package.json` の直接依存は `eslint: ^10` と `eslint-config-next: 16.4.6`（`next` と同じ版で固定）。脆弱な版は次の経路で入っている。

| パッケージ | 解決版 | lock の位置 | 要求元と範囲 | 判定 |
|---|---|---|---|---|
| zod | 4.6.5 | `web/package-lock.json:7363` | `eslint-plugin-react-hooks@7.1.1` の `"zod": "^3.25.0 \|\| ^4.0.0"`（`:4110`）、`zod-validation-error` の peer（`:7383`） | **脆弱**（<4.6.6）。semver 範囲内で 4.6.6 以上にできる |
| minimatch | 3.1.5 | `web/package-lock.json:4037`（`eslint-config-next/node_modules/minimatch`） | `eslint-plugin-import@2.32.0`（`:3936`）、`eslint-plugin-jsx-a11y@6.10.2`（`:3979`）、`eslint-plugin-react@7.37.5`（`:4007`）がいずれも `^3.1.2` | **脆弱**（<11.0.1）。範囲内に修正版はない |
| minimatch | 10.2.6 | `web/package-lock.json:5689`（ルートの `node_modules/minimatch`） | `@typescript-eslint/typescript-estree@8.70.1` の `^10.2.2`（`:2417`）。`typescript-eslint` 経由 | **脆弱**（<11.0.1）。Issue 本文の表には版が出ていないが、経路の「typescript-eslint」はこれ |
| minimatch | 11.0.9 | `:4152`（`eslint/node_modules`）、`:837`（`@eslint/config-array/node_modules`） | `eslint@10.15.0`、`@eslint/config-array@0.23.6` の `^11.0.5` | 修正済み。対応不要 |

つまり minimatch の残りは「3.x 系（eslint-config-next 配下の 3 プラグイン）」と「10.x 系（typescript-eslint）」の 2 系統。ESLint 本体はすでに対応済み。

## 2. 修正計画（最小限）

変更するファイルは `web/package.json` と `web/package-lock.json` だけ。アプリのコードと ESLint の設定（`web/eslint.config.mjs`）は変えない。

### ステップ 1: zod（必須、低リスク）

```
cd web && npm update zod
```

- `package.json` は変えず、lock の `node_modules/zod` だけを 4.6.6 以上にする（`npm audit fix` でも可）。
- **`npm audit fix --force` は使わない**（eslint@4.0.0 / eslint-config-next@12.0.4 への大幅ダウングレードになる）。
- 確認: `npm ls zod` が 4.6.6 以上の 1 本だけになること。

### ステップ 2: 上流の修正版を確かめる（overrides より先に）

- `npm view @typescript-eslint/typescript-estree@latest dependencies.minimatch`、`npm view typescript-eslint version` で、minimatch `^11` を要求する typescript-eslint が出ていないか確認する。出ていれば `npm update typescript-eslint @typescript-eslint/typescript-estree`（eslint-config-next の `typescript-eslint: ^8.56.0` の範囲内）で解決する。
- `npm view eslint-plugin-import@latest dependencies.minimatch`、`eslint-plugin-jsx-a11y`、`eslint-plugin-react` も同様に確認する。範囲内（`^2.32.0` / `^6.10.0` / `^7.37.0`）で minimatch を上げた版があれば `npm update` で取り込む。
- **`eslint-config-next` は単独で上げない**。`next: 16.4.6` と同じ版で固定している方針のため、上げるなら next と揃える別 Issue にする。
- 結果（上流の版と minimatch の要求範囲）は PR 本文に書く。Issue への記録の材料になる。

### ステップ 3: 上流が未対応なら overrides（2 系統に分けて試す）

`web/package.json` に対象を絞った `overrides` を追加する。グローバルの `"minimatch": "..."` ではなく、要求元ごとに書く（ESLint 本体の 11.0.9 に触れず、どれを強制したかが残るようにする）。

**3-a: typescript-eslint の 10.x → 11（リスク低）**

```json
"overrides": {
  "@typescript-eslint/typescript-estree": { "minimatch": "^11.0.9" }
}
```

- 10→11 は名前付きエクスポート（`import { minimatch } from "minimatch"`）の形が同じで、ESLint 本体もすでに 11 を使っているため互換の見込みが高い。

**3-b: eslint-config-next 配下の 3.x → 11（リスク中、壊れるならリスク受容）**

```json
"overrides": {
  "@typescript-eslint/typescript-estree": { "minimatch": "^11.0.9" },
  "eslint-plugin-import":   { "minimatch": "^11.0.9" },
  "eslint-plugin-jsx-a11y": { "minimatch": "^11.0.9" },
  "eslint-plugin-react":    { "minimatch": "^11.0.9" }
}
```

- 注意点: minimatch 3 は `module.exports = minimatch`（関数そのもの）だが、5 以降の CJS は `{ minimatch, Minimatch, ... }` のオブジェクトを返す。プラグインが `const minimatch = require("minimatch"); minimatch(path, pattern)` や Babel の default import で呼んでいると `TypeError: minimatch is not a function` になる。
- しかも minimatch を使うのは一部のルールのオプション（例: `import/no-extraneous-dependencies` の `packageDir`/globs、`import/order` の `pathGroups`、`import/no-restricted-paths`、`react` の一部ルール、`jsx-a11y` の設定まわり）だけで、**`npm run lint` が通っても壊れていないとは限らない**。そのため次の確認を行う（Bash で実行。コードは変えない）。
  1. `grep -rn "require(['\"]minimatch['\"])\|from ['\"]minimatch['\"]" node_modules/eslint-config-next/node_modules/eslint-plugin-{import,jsx-a11y,react}/lib` で読み込み方と呼び出し方を確認する。
  2. `node -e "const m=require('<上記パス>/node_modules/minimatch'); console.log(typeof m, typeof m.default, typeof m.minimatch)"` で、override 後の minimatch が各プラグインの呼び方に合うか確認する（`typeof m === "function"` か、default import なら `typeof m.default === "function"` が必要）。
  3. 呼び方が合わないプラグインが 1 つでもあれば、**3-b は採用せず 3-a だけにして、3.x の残存をリスク受容とする**。
- 呼び方が合う場合でも `npm run lint` が今と同じ結果（新しいエラー・クラッシュなし）であることを必須とする。

### ステップ 4: リスク受容を記録する材料（3-b を見送る場合）

PR 本文に次をまとめる（Issue への記録はオーケストレーターまたは人間が行う。開発者はトラッカーを直接触らない）。

- 残る advisory: GHSA-g3hv-75wg-m249（minimatch <11.0.1、moderate）
- 経路: `eslint-config-next@16.4.6` → `eslint-plugin-import@2.32.0` / `eslint-plugin-jsx-a11y@6.10.2` / `eslint-plugin-react@7.37.5` → `minimatch@3.1.5`
- 受容の理由: 開発依存のみ（standalone の本番イメージに入らない）、入力は自リポジトリの lint 設定とファイルパスだけで外部入力がない、minimatch 3→11 の強制はプラグインの実行時エラーを招く（確認結果を添える）
- 追跡先: 各プラグインの上流リポジトリの該当 Issue / PR（`npm view <pkg> repository.url` で確認し、URL を書く。見つからなければ「上流 Issue なし」と明記）と、eslint-config-next の更新

## 3. 副作用のリスク

| 観点 | 内容 |
|---|---|
| 本番 | なし。対象はすべて `"dev": true`。`web/Dockerfile` の deps ステージは dev も入れるが、runner は `.next/standalone` だけをコピーする |
| Node.js | minimatch 11.0.9 の engines は `20 \|\| >=22`。`web/Dockerfile` は `node:22.23.5-alpine`、CI もこの版を使う（`scripts/web-node-version.sh`）ので問題なし |
| lock の差分 | `npm update zod` や overrides で関係ない依存まで動かさないこと。lock の差分は zod・minimatch・brace-expansion 系に限られるのが望ましい。広く動いたら原因を確かめる |
| ESLint ルール | 3-b を入れると、minimatch を使うルールのオプションを今後追加したときに初めて壊れる恐れがある（上記の確認で防ぐ） |
| CI | `.github/workflows/pr-test.yml` は `npm audit --omit=dev --audit-level=high` だけを見るので、今回の対象（dev）はもともと CI を落とさない。CI の変更はこの Issue の範囲外 |

## 4. 完了条件（Issue 本文から）

- `cd web && npm audit --audit-level=high` が 0 件で終わる（high 以上が 0）。
- moderate が残る場合は、理由と追跡先が Issue に記録されている（材料は PR 本文に用意する）。

## 5. チェックリスト

- [ ] `npm ls zod` が 4.6.6 以上だけ
- [ ] `npm ls minimatch` の結果を PR に貼る（11 系だけか、3.x / 10.x が残るか）
- [ ] `npm audit` の結果（high 0、moderate の残数）を PR に貼る
- [ ] `npm audit --omit=dev --audit-level=high` が今まで通り通る
- [ ] `npm run lint` が変更前と同じ結果（クラッシュ・新しいエラーなし）
- [ ] `npm test` が通る
- [ ] `npm run build` が通る（lock の変化で本番依存が動いていないことの確認）
- [ ] `package.json` の `eslint` / `eslint-config-next` / `next` の版を変えていない。`npm audit fix --force` を使っていない
- [ ] 3-b を入れた場合、各プラグインの minimatch の呼び方と override 後のエクスポートが合うことを確認した記録がある

## 6. サブタスク

| ID | 内容 | スタック | ファイル | 依存 | 見積もり |
|---|---|---|---|---|---|
| 75-1 | zod を 4.6.6 以上に更新、上流の修正版を確認、minimatch の overrides（3-a、可能なら 3-b）、lint / test / build / audit の確認、PR 本文にリスク受容の材料をまとめる | frontend | `web/package.json`、`web/package-lock.json` | なし | 1〜2h |

- back/front の分担: frontend のみ（backend の作業なし）。
- ドキュメント: `docs/codebase/` に依存の記載はないため更新不要。overrides を入れた場合は理由を PR 本文に書く（`package.json` はコメントを書けないため）。

## 7. 読んだもの

- `docs/factory.config.md`、`docs/codebase/README.md`、`docs/conventions/`（frontend / general の該当箇所）
- CodeBase マップは依存ツリーを扱わないため、`web/package.json`、`web/package-lock.json`、`web/eslint.config.mjs`、`web/Dockerfile`、`.github/workflows/pr-test.yml` を直接読んだ
