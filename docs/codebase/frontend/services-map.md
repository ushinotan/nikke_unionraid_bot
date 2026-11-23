# サービス（frontend の `lib/`）

| ファイル | 主な export | 内容 |
|---|---|---|
| `lib/session.ts` | `SessionData`、`GuildMetadata`、`getSessionOptions`、`getSession` | iron-session の設定。`SessionData` は `userId`、`guildIds`、`guilds`（`owner`、`permissions` 付き）、`selectedGuildId`、`state`、`loggedInAt`（権限を取得した時刻、ms。ログイン時にだけ入れる。無いセッションは特権なし） |
| `lib/backend-client.ts` | `fetchFromBackend`、`handleBackendError`、`validateSnowflakeId`、`validateNumericId` | `BACKEND_URL` に `X-Internal-Api-Token` を付けて呼ぶ。タイムアウトあり、`cache: "no-store"` |
| `lib/permissions.ts` | `ADMINISTRATOR`、`MANAGE_GUILD`、`PRIVILEGE_MAX_AGE_MS`、`isPermissionStale`、`isPrivileged`、`canEditParticipant` | オーナーか、管理者 / サーバー管理の権限があれば特権ユーザー。`permissions` は 10 進数の非負整数のときだけ判定する。`loggedInAt` から `PRIVILEGE_MAX_AGE_MS`（1 日）を過ぎた、または `loggedInAt` が無いセッションは特権なし（`now` は引数で差し替え可） |
| `lib/raid-authorization.ts` | `authorizeRaidEdit`、`requireRaidEditPermission`、`RaidEditRequirement` | 書き込み系 API Route 用の権限チェック（`privileged` / `participant`）。権限情報が古くて特権として扱えないときは 403 `REAUTH_REQUIRED`（再ログインで解決する可能性があるという意味。一般メンバーでも古ければ返り、再ログイン後は `FORBIDDEN` になることがある）。まだどの Route からも使われていない |
| `lib/origin.ts` | `getConfiguredOrigin`、`getPublicOrigin` | リダイレクト先の origin。`APP_BASE_URL` → `x-forwarded-*` → `DISCORD_REDIRECT_URI` の順 |
| `lib/api-types.ts` | `GuildDto`、`RaidSummaryDto`、`ParticipantDto`、`RaidReportDto`、`RaidDetailResponse` など | バックエンドの DTO（`dto/ApiResponses.kt`）に合わせた型 |

## テスト
`web/__tests__/*.test.ts`（`node:test` + tsx、`npm test`）: `backend-client`、`origin`、`permissions`、`proxy`、`raid-authorization`、`session`。
