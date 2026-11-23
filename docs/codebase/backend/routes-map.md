# ルート（backend）

## REST API
`controller/RaidApiController.kt`（`/api`）、`controller/GuildMemberApiController.kt`（`/api`）、`controller/HealthController.kt`。レスポンス DTO は `dto/ApiResponses.kt`、変換は `dto/DtoMappers.kt`。
詳しい仕様は `kotlin/docs/API.md`。

| メソッド | ルート | 認証 | 処理 |
|---|---|---|---|
| GET | `/health` | なし | 死活確認 |
| GET | `/api/guilds` | `InternalApiTokenFilter` | `GuildRepository.findAllGuilds` → `GuildsResponse` |
| GET | `/api/guilds/{guildId}/raids` | 同上 | `UnionRaidRepository.findUnionRaidsByGuildId` → `RaidsResponse` |
| GET | `/api/guilds/{guildId}/members?query=&limit=` | 同上 | `GuildMemberService.findGuildMembers` → `GuildMembersResponse`（`members[].userId` は文字列、`truncated`。query ≤100 文字は trim 前に検査、limit 1〜100 既定 100。400 / 404 `GuildNotFound` / 503 `GuildMembersUnavailable` / 500） |
| GET | `/api/raids/{raidId}` | 同上 | レイド + 参加者 + 3凸報告 → `RaidDetailResponse`（無ければ 404） |

- パスの型が合わないと 400、その他の例外は 500（`ErrorResponse`）。
- 書き込み系 API はまだ無い（順位・パーセンテージ、参加者・3T 報告の更新 API は Issue #105 / #96 で予定）。

## Discord（JDA）
コマンド定義は `bot/CommandRegistrar.kt`（`DEV_GUILD_ID` があればそのギルドに即時登録、無ければグローバル登録）。
処理は `bot/UnionRaidEventListener.kt`。

| 種類 | 名前 / ID | 処理 |
|---|---|---|
| スラッシュコマンド | `/レイド作成`（`期間時間`: 整数、既定 24） | モーダル `raid_start_modal_{duration}` を表示 → 開始時刻（`YYYY-MM-DD HH:MM` JST）を受けて `RaidDomainService.createRaid` |
| スラッシュコマンド | `/レイド終了`（`順位` または `パーセンテージ`） | `RaidDomainService.finishRaid` → 集計を送信 |
| ボタン | `raid_report` | 難易度選択メニュー `difficulty_select_{raidId}` を表示 → `reportThreeTurn` |
| ボタン | `raid_report_cancel` | 取り消しメニュー `cancel_report_select_{raidId}` を表示 → `cancelThreeTurnReports` |
| イベント | `onGuildJoin` / `onGuildLeave` | `guilds` に追加 / 削除 |

ボタンから raidId を取るときは、通知 Embed の `レイドID` フィールドを読む。
