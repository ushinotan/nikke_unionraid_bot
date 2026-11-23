## Code Review — 109 — attempt 1 — 2026-11-23

### Verdict: ⚠️ APPROVED WITH NOTES

対象差分: `web/__tests__/write-route-guard.test.ts`（新規, F1）、`web/__tests__/helpers/raid-edit-fixtures.ts`（新規, F2）、`web/__tests__/raid-authorization.test.ts`（F2 へ移行）。本番コードの変更なし。

### 🔴 Critical

なし。

- 本番コード（`lib/`、`app/`）に変更なし。秘密情報なし、パッケージ追加なし、コミットなし（未追跡のまま）、保護ブランチではない（`feature/109`）。
- `import type` を使っており、`any` はなく、ダブルクォートとセミコロンもそろっている。テスト名は日本語の「条件なら結果」形式。

### 🟡 Important

なし。

### 🟢 Suggestions（ブロックしない。負債として扱う）

1. `web/__tests__/helpers/raid-edit-fixtures.ts:66` — 型名 `RecordedRaid` は「記録されたもの」と読めるが、実際は「GET で返すレイド」を表している。`BackendRaid` などへの改名を勧める。また、この型は 62 行目の `BackendMock` で先に使われているので、`BackendMock` より前に宣言したほうが読みやすい。
2. `web/__tests__/raid-authorization.test.ts:3-14` — `privilegedMember as privileged`、`selfMember as general` という別名 import で差分は小さくなっているが、フィクスチャの名前とテスト内の名前がずれる。#96 / #104 のルートテストでも同じ名前を使うなら、ここでもフィクスチャ名をそのまま使うほうが一貫する。
3. `web/__tests__/write-route-guard.test.ts:16,39` — F1 は文字列で検出しているため、`export { handler as POST }` や `export { POST } from "..."` の形は書き込みルートとして見つからない。また、コメント内の `requireRaidEditPermission(` でも合格してしまう。計画（§3 の「限界」）で受け入れ済みの制約だが、テスト冒頭のコメントか doc-sync で明記しておくと、#96 / #104 の実装者に伝わる。
4. `web/__tests__/helpers/raid-edit-fixtures.ts:83` — `body` には `init.body` をそのまま記録している（`Request` オブジェクトで呼んだ場合の body は記録されない）。今の `lib/api.ts` が使う `fetch(url, init)` 形式なら問題はない。JSON 文字列のまま記録される点を JSDoc に書いておくと、ルートテストで使いやすい。
5. （既存の問題、本差分の範囲外）`web/package.json:10` の `__tests__/**/*.test.ts` はクォートされていないので、`sh` がパターンを展開する。今は `helpers/` に `.test.ts` が無いのでパターンがそのまま node に渡り、node 22 の glob で全件が実行されている。しかし将来 `__tests__/<subdir>/*.test.ts` を置くと、`sh` がそのサブディレクトリ分だけに展開してしまい、直下のテストが実行されなくなる。サブディレクトリを導入したので、別 Issue でパターンをクォートすることを勧める。

### 📄 Documentation

- D1（`docs/codebase/frontend/routes-map.md`、`services-map.md`、`docs/conventions/testing.md`、`docs/conventions/frontend.md`）はまだ反映されていない（`doc_sync_order: after_self_test` のため想定どおり）。dev-doc-sync の実行が必要。特に以下を入れること。
  - 「書き込み系を追加するとき」の節に、F1 の存在と許可リストの運用（理由付きで登録する）を書く。
  - ルートテストは F2 の 4 ペルソナで書き、「403 のとき `writes` が空である」ことも確かめる。
  - `route.ts` は薄くし、本体は `session` を引数に取る関数に切り出す。
  - 上記 Suggestion 3 の F1 の制約。

### 検証（レビュアーが実施）

- `cd web && npm test`: 104 件成功、0 件失敗。`npx eslint __tests__` もエラーなし。
- `raid-authorization.test.ts` のケース数は移行前後とも 15 件で、減っていない。
- 開発者が実施していなかった F1 の失敗確認を、作業ツリーの外（scratchpad）にテストファイルと擬似の `app/api` を置いて行った。リポジトリは変更していない。
  - ガードの無い `export const DELETE` → 1 件目のテストが失敗し、該当パスが表示された。
  - 許可リストのファイルを削除 → 2 件目が失敗。
  - 許可リストのファイルが `GET` だけになる → 3 件目が失敗。
  - `GET` だけのルート → 対象外。
  - `requireRaidEditPermission(` を呼ぶ `DELETE` → 合格。
- `npx tsc --noEmit` の `app/layout.tsx` の `LayoutProps` エラーは、変更していないファイルのもの。Next.js が生成する型（`.next/types`）が無いことによるもので、本差分とは関係ない。

### Summary

計画の F1 / F2 を過不足なく実装している。F1 は計画のチェックリスト（未ガードで失敗、許可リストの陳腐化検出、GET だけのルートは対象外）をすべて満たし、失敗メッセージも次に何をすればよいかが分かる。F2 はケース数を減らさずに既存テストを移行し、`writes` の記録によって「403 時にバックエンドへ転送されていない」ことを検証できるようにしている。これは #96 / #104 のルートテストの土台として適切。ヘルパーは `.test.ts` で終わらないので、テストとして実行されない。指摘は命名と制約の明記だけで、ブロックするものはない。残りは D1（doc-sync）。
