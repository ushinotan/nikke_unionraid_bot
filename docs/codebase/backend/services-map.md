# サービス（backend）

パッケージのルートは `kotlin/src/main/kotlin/gg/nikke/raid/bot/`。

## ドメインサービス
`service/RaidDomainService.kt`（`@Service`）。Discord リスナーとスケジューラーから呼ばれる。

| メソッド | 内容 |
|---|---|
| `createRaid` | 期間 1 時間以上、進行中レイドが無いことを確認して作成 |
| `finishRaid` | 順位かパーセンテージ（どちらか一方、範囲チェックあり）を記録して終了 |
| `reportThreeTurn` | 3凸報告を保存（`raid_participants` には書かない） |
| `ensureRaidActive` | レイドが存在し、終了していないことを確認 |
| `hasReported` / `findReportedDifficulties` | 報告済みの難易度を調べる |
| `cancelThreeTurnReports` | 指定した難易度の報告を削除 |
| `aggregateRaidResults` | 終了時の集計（`RaidAggregation`） |

エラーは `service/RaidDomainError.kt`: `GuildNotFound`、`GuildMembersUnavailable`（メンバー一覧用）、`DurationTooShort`、`ActiveRaidAlreadyExists`、`RankingAndPercentageBothSpecified`、`RaidNotFound`、`RaidAlreadyFinished`、`NoDifficultySelected`、`RankingOutOfRange`、`PercentageOutOfRange`。

## ギルドメンバー一覧
- `service/GuildMemberService.kt`（`@Service`）: `findGuildMembers(guildId, query, limit)`。bot / system 除外、名前（ニックネーム・グローバル名・ユーザー名）の部分一致（trim・小文字化）、`displayName` + `userId` で整列、`limit` で切り `truncated` を返す。
- `service/GuildMemberSource.kt`: JDA 依存を隠すポート（`findMembers` は bot 不参加なら `null`、取得不能なら `GuildMembersUnavailable`）と `GuildMemberSnapshot`。`service/GuildMember.kt`: `GuildMember` / `GuildMemberPage`。
- `bot/JdaGuildMemberSource.kt`: JDA のメンバーキャッシュを使う実装。未ロードなら `loadMembers` をギルド単位で single-flight にまとめ、10 秒でタイムアウト。`DiscordBot` が READY 後に `attach`、終了時に `detach`。

## リポジトリ（Komapper）
`repository/`。`repository/KomapperMeta.kt` がメタモデルをまとめる。

| クラス | 主なメソッド |
|---|---|
| `GuildRepository` | `findAllGuilds`、`findGuildById`、`insertGuild`、`deleteGuildById` |
| `UnionRaidRepository` | `findUnionRaidsByGuildId`、`findActiveUnionRaidByGuildId`、`findUnionRaidById`、`findNotifiableActiveUnionRaids`、`findUnfinishedUnionRaids`、`insertUnionRaid`、`clearNotifyTime`、`markNotificationSent`、`finishUnionRaid` |
| `RaidParticipantRepository` | `findParticipantsByRaidId`、`insertOrUpdateParticipant`（upsert。今は本体コードから呼ばれていない） |
| `RaidReportRepository` | `findRaidReport`、`findAllRaidReportsByRaidId`、`findRaidReportsByRaidId`、`findRaidReportsByRaidIdAndUserId`、`deleteRaidReports`、`saveRaidReport` |

## 設定・共通
- `config/AppConfig.kt`（`app.*`: タイムゾーン、内部 API トークン）、`config/DiscordConfig.kt`（`discord.*`: トークン、開発ギルド ID）。
- `config/InternalApiTokenFilter.kt`: `/api/**` のトークン認証。servletPath + pathInfo で判定する。
- `util/TimeUtils.kt`: `utcNow`、`localNow`、`ensureUtc`、`secondsUntil`。
- `bot/DiscordBot.kt`: JDA の起動（`GUILD_MEMBERS` Intent 有効、`MemberCachePolicy.ALL`。Intent が OFF だと起動失敗し、メッセージに Server Members Intent の確認を促す）、コマンド登録、スケジュールの再開。

## テスト
`kotlin/src/test/kotlin/gg/nikke/raid/bot/` にパッケージごと（`bot`、`config`、`controller`、`repository`、`service`、`util`）。
`*IntegrationTest` は実際の PostgreSQL（`init.sql` 適用済み）に接続する。共通の基底クラスや fixture は無い。
