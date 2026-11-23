# 画面（frontend）

App Router（`web/app/`）。`/raids` 配下は `proxy.ts` が未ログインを `/api/auth/discord` にリダイレクトする。
画面はクライアントコンポーネント（`"use client"`）で、データは API Route を `fetch` して取る。

| ルート | ファイル | 内容 |
|---|---|---|
| `/` | `app/page.tsx` | トップ。`/raids` へのリンク |
| `/raids` | `app/raids/page.tsx` | ギルドを選び（`/api/guilds`、選択は `/api/session/selected-guild` に保存）、レイド一覧を表示（`/api/guilds/{guildId}/raids`）。`StatusBadge`、`RankBadge` を使う |
| `/raids/[id]` | `app/raids/[id]/page.tsx` | レイド詳細（`/api/raids/{raidId}`）。3凸報告を報告日時順に並べ、メンバーごとに難易度の進み具合を集計（`aggregateMembers`） |

- レイアウトは `app/layout.tsx`、スタイルは `app/globals.css`（Tailwind 4、ライトテーマ）。
- 編集 UI はまだ無い（参加者・3T 報告: Issue #97、代理登録: #98、順位・パーセンテージ: #106）。
