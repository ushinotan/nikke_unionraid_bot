# サービス（frontend の `lib/`）

| ファイル | 主な export | 内容 |
|---|---|---|
| `lib/session.ts` | `SessionData`、`GuildMetadata`、`applyLogin`、`getSessionOptions`、`getSession` | iron-session の設定。`applyLogin` はログイン時にユーザー・ギルド・`loggedInAt` をセッションへ入れる（callback から呼ぶ）。`SessionData` は `userId`、`guildIds`、`guilds`（`owner`、`permissions` 付き）、`selectedGuildId`、`state`、`loggedInAt`（権限を取得した時刻、ms。ログイン時にだけ入れる。無いセッションは特権なし） |
| `lib/backend-client.ts` | `fetchFromBackend`、`handleBackendError`、`validateSnowflakeId`、`validateNumericId` | `BACKEND_URL` に `X-Internal-Api-Token` を付けて呼ぶ。タイムアウトあり、`cache: "no-store"` |
| `lib/permissions.ts` | `ADMINISTRATOR`、`MANAGE_GUILD`、`PRIVILEGE_MAX_AGE_MS`、`MAX_CLOCK_SKEW_MS`、`PermissionCheck`、`isPermissionStale`、`checkPrivilege`、`isPrivileged`、`checkParticipantEdit`、`canEditParticipant` | オーナーか、管理者 / サーバー管理の権限があれば特権ユーザー。`permissions` は 10 進数の非負整数のときだけ判定する。`loggedInAt` から `PRIVILEGE_MAX_AGE_MS`（1 日）を過ぎた、`loggedInAt` が無い、または `MAX_CLOCK_SKEW_MS`（5 分）を超えて未来のセッションは特権なし。`check*` は `allowed` / `stale` / `denied` の理由付きで返し、`is*` / `can*` はその boolean 版（`now` は引数で差し替え可） |
| `lib/raid-authorization.ts` | `authorizeRaidEdit`、`requireRaidEditPermission`、`RaidEditRequirement` | 書き込み系 API Route 用の権限チェック（`privileged` / `participant`）。`check*` の結果が `stale` なら 403 `REAUTH_REQUIRED`、`denied` なら `FORBIDDEN`（再ログインで解決する可能性があるという意味。一般メンバーでも古ければ返り、再ログイン後は `FORBIDDEN` になることがある）。まだどの Route からも使われていない |
| `lib/origin.ts` | `getConfiguredOrigin`、`getPublicOrigin` | リダイレクト先の origin。`APP_BASE_URL` → `x-forwarded-*` → `DISCORD_REDIRECT_URI` の順 |
| `lib/api-types.ts` | `GuildDto`、`RaidSummaryDto`、`ParticipantDto`、`RaidReportDto`、`RaidDetailResponse` など | バックエンドの DTO（`dto/ApiResponses.kt`）に合わせた型 |

## テスト
`web/__tests__/*.test.ts`（`node:test` + tsx、`npm test`）: `backend-client`、`origin`、`permissions`、`proxy`、`raid-authorization`、`session`、`write-route-guard`（書き込みルートが `requireRaidEditPermission` を通るかのガード。文字列一致のため再 export / コメント内の呼び出しは検出できない）。
E2E（Playwright、Chromium）は `web/e2e/*.spec.ts`、モックバックエンドとクッキー生成は `web/e2e/support/`（`npm run test:e2e`。詳しくは `docs/conventions/testing.md`）。
テスト用フィクスチャ・`mockBackend` は `web/__tests__/helpers/raid-edit-fixtures.ts`（`.test.ts` で終わらないので `npm test` では実行されない）。
