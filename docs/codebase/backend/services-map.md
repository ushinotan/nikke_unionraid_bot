# サービス（backend）

パッケージのルートは `kotlin/src/main/kotlin/gg/nikke/raid/bot/`。

## ドメインサービス
`service/RaidDomainService.kt`（`@Service`）。Discord リスナーとスケジューラーから呼ばれる。

| メソッド | 内容 |
|---|---|
| `createRaid` | 期間 1 時間以上、進行中レイドが無いことを確認して作成 |
| `finishRaid` | 順位かパーセンテージ（どちらか一方、範囲チェックあり）を記録して終了 |
| `reportThreeTurn` | `@Transactional`。レイド行をロックし、3凸報告を保存して、未参加なら score 0 で自動参加（`insertParticipantIfAbsent`。既存参加者の `username`・`score`・`joined_at` は変えない）。`ThreeTurnReportResult(alreadyReported, participantRegistered)` |
| `ensureRaidActive` | レイドが存在し、終了していないことを確認 |
| `hasReported` / `findReportedDifficulties` | 報告済みの難易度を調べる |
| `cancelThreeTurnReports` | `@Transactional`。終了済みレイドは不可。実際に報告済みの難易度だけ削除し、取り消し後に `is_3t=1` の報告が 0 件で、かつ実際に取り消した場合のみ参加者も削除（手動登録・スコアも消える）。`ThreeTurnCancelResult(cancelled, participantRemoved)` |
| `cancelParticipation` | `@Transactional`。参加者行と全難易度の報告を削除（冪等）。終了済みかどうかは判定しない。`ParticipationCancelResult(participantRemoved, removedReportCount)` |
| `aggregateRaidResults` | 終了時の集計（`RaidAggregation`） |

結果型は `service/ThreeTurnResults.kt`。

### 参加者・報告のロック手順
- 参加者・報告への書き込みは必ず `RaidDomainService` 経由で行い、最初に `union_raids` を `FOR UPDATE` でロックする（`UnionRaidRepository.findUnionRaidByIdForUpdate`、private の `lockRaid` / `lockActiveRaid`）。順序は `union_raids` → `raid_reports` → `raid_participants`。
- リポジトリの書き込みメソッド（`saveRaidReport`、`deleteRaidReports`、`deleteRaidReportsByRaidIdAndUserId`、`insertOrUpdateParticipant`、`insertParticipantIfAbsent`、`deleteParticipant`）は `repository/TransactionGuard.kt` の `requireActiveTransaction` で守られ、トランザクション外からは `IllegalStateException`。

### #91 Web 側への引き継ぎ
- BFF は `cancelParticipation` を呼ぶ前に「本人か特権ユーザーか」と「終了済みレイドの扱い」を必ず確認する（サービス側は判定しない）。
- `insertOrUpdateParticipant` は呼び出し元が無く、ガード付き。手動参加・スコア更新には、ロック付きの `@Transactional` メソッドを `RaidDomainService` に追加して使うこと。

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
| `UnionRaidRepository` | `findUnionRaidsByGuildId`、`findActiveUnionRaidByGuildId`、`findUnionRaidById`、`findNotifiableActiveUnionRaids`、`findUnfinishedUnionRaids`、`insertUnionRaid`、`clearNotifyTime`、`markNotificationSent`、`finishUnionRaid`、`findUnionRaidByIdForUpdate`（`FOR UPDATE`。トランザクション内で使う） |
| `RaidParticipantRepository` | `findParticipantsByRaidId`、`insertOrUpdateParticipant`（upsert。呼び出し元なし）、`insertParticipantIfAbsent`（ON CONFLICT DO NOTHING、挿入行数を返す）、`deleteParticipant`。書き込みはトランザクション必須 |
| `RaidReportRepository` | `findRaidReport`、`findAllRaidReportsByRaidId`、`findRaidReportsByRaidId`、`findRaidReportsByRaidIdAndUserId`、`deleteRaidReports`、`deleteRaidReportsByRaidIdAndUserId`、`saveRaidReport`（書き込みはトランザクション必須） |

## 設定・共通
- `config/AppConfig.kt`（`app.*`: タイムゾーン、内部 API トークン）、`config/DiscordConfig.kt`（`discord.*`: トークン、開発ギルド ID）。
- `config/InternalApiTokenFilter.kt`: `/api/**` のトークン認証。servletPath + pathInfo で判定する。
- `util/TimeUtils.kt`: `utcNow`、`localNow`、`ensureUtc`、`secondsUntil`。
- `bot/DiscordBot.kt`: JDA の起動（`GUILD_MEMBERS` Intent 有効、`MemberCachePolicy.ALL`。Intent が OFF だと起動失敗し、メッセージに Server Members Intent の確認を促す）、コマンド登録、スケジュールの再開。

## テスト
`kotlin/src/test/kotlin/gg/nikke/raid/bot/` にパッケージごと（`bot`、`config`、`controller`、`repository`、`service`、`util`）。
`*IntegrationTest` は実際の PostgreSQL（`init.sql` 適用済み）に接続する。共通の基底クラスや fixture は無い。
