# ルート（frontend の API Route / BFF）

`web/app/api/`。セッションは `getSession()`（iron-session、Cookie `nikke_raid_session`）。
バックエンド呼び出しは `fetchFromBackend`、エラーは `handleBackendError`（`lib/backend-client.ts`）。

| メソッド | ルート | ファイル | 内容 |
|---|---|---|---|
| GET | `/api/auth/discord` | `app/api/auth/discord/route.ts` | `state` をセッションに入れて Discord OAuth にリダイレクト |
| GET | `/api/auth/discord/callback` | `app/api/auth/discord/callback/route.ts` | `state` を確認しトークン交換、ユーザーと所属ギルド（権限ビット付き）と `loggedInAt`（現在時刻）を `applyLogin` でセッションに保存 |
| POST | `/api/auth/logout` | `app/api/auth/logout/route.ts` | セッション破棄 |
| GET | `/api/guilds` | `app/api/guilds/route.ts` | bot のギルドのうち、ユーザーが所属するものだけを返す（名前・アイコンをセッションから補う） |
| GET | `/api/guilds/[guildId]/raids` | `app/api/guilds/[guildId]/raids/route.ts` | ギルド ID を検証してレイド一覧を中継 |
| GET | `/api/raids/[raidId]` | `app/api/raids/[raidId]/route.ts` | レイド詳細を取り、そのギルドに所属していなければ 403 |
| GET / POST | `/api/session/selected-guild` | `app/api/session/selected-guild/route.ts` | 選択中のギルドを読み書き |

## proxy（`web/proxy.ts`、旧 middleware）
- 保護対象: 画面 `/raids`、API `/api/guilds`、`/api/raids`、`/api/session`。
- 未ログイン: 画面は `/api/auth/discord` へ 307、API は 401。
- `/api/guilds/{guildId}/...` はセッションの `guildIds` に無いギルドなら拒否。
- matcher は `api/auth`、`_next/static`、`_next/image`、`favicon.ico` を除く全パス。

## 書き込み系を追加するとき
`lib/raid-authorization.ts` の `requireRaidEditPermission` を必ず通す。`targetUserId` は実際に書き込むレコードの持ち主から取り、
クライアントから来たギルド ID などで判定しない（ファイル先頭のコメント参照）。
権限情報は `loggedInAt` から 1 日（`PRIVILEGE_MAX_AGE_MS`）で失効し、期限切れで拒否した操作は 403 `REAUTH_REQUIRED`（再ログインで解決する可能性あり）になる。
- `web/__tests__/write-route-guard.test.ts` が `app/` 配下の route handler を走査し、`POST/PUT/PATCH/DELETE` のハンドラーごとに、
  `requireRaidEditPermission(` を呼んで `ok` を確かめてからバックエンドを呼んでいるか検査する。ハンドラーは `export async function POST` の形で書く（`export const` や `export { x as POST }` は失敗になる）。
  バックエンドに書き込まないハンドラーは同テスト内の許可リストにメソッド単位で理由付きで追加する（現在は `auth/logout` の POST、`session/selected-guild` の POST）。
- ルートのテストでは 本人・本人以外の一般メンバー・特権・所属外 の 4 パターンと「403 のときバックエンドへ書き込みが転送されない」ことを確かめる（フィクスチャは `web/__tests__/helpers/raid-edit-fixtures.ts`。詳しくは `docs/conventions/testing.md`）。
