package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.util.TimeUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime

/**
 * レイド管理のドメインサービスクラス。
 * ギルド内のレイド管理、レイド結果の集計、および関連する操作を提供します。
 *
 * このクラスは、他のリポジトリとやり取りする責務を持ち、レイドの作成、完了、報告、および結果の集計を行います。
 */
@Service
class RaidDomainService(
    private val unionRaidRepository: UnionRaidRepository,
    private val raidReportRepository: RaidReportRepository,
) {

    companion object {
        private val MIN_RAID_DURATION = Duration.ofHours(1)
        private const val MIN_RANKING = 1
        private val MAX_PERCENTAGE = BigDecimal("100")
    }

    /**
     * 新しいユニオンレイドを作成します。
     *
     * @param guildId レイドを作成するギルドのID
     * @param raidName レイドの名前
     * @param startTime レイドの開始時刻
     * @param endTime レイドの終了時刻
     * @param notifyTime レイド通知のための時刻（省略可能）
     * @param channelId 通知やアクションに使用するチャンネルのID
     * @param now 処理時の現在時刻（省略可能、デフォルトは現在のUTC時刻）
     * @return 作成されたユニオンレイドを含む `Result`。失敗した場合はエラー情報を含みます。
     */
    fun createRaid(
        guildId: Long,
        raidName: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime,
        notifyTime: OffsetDateTime? = null,
        channelId: Long,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<UnionRaid> {
        if (Duration.between(startTime, endTime) < MIN_RAID_DURATION) {
            return Result.failure(RaidDomainError.DurationTooShort)
        }

        if (unionRaidRepository.findActiveUnionRaidByGuildId(guildId) != null) {
            return Result.failure(RaidDomainError.ActiveRaidAlreadyExists)
        }

        val raid = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = raidName,
                startTime = startTime,
                endTime = endTime,
                notifyTime = notifyTime,
                channelId = channelId,
            )
        )
        return Result.success(raid)
    }

    /**
     * 指定されたレイドを終了します。
     *
     * @param raidId 終了するレイドのID
     * @param ranking 終了時の順位（任意）。1以上で指定。percentageと同時指定不可
     * @param percentage 終了時の上位パーセンテージ（任意）。0より大きく100以下。rankingと同時指定不可
     * @param now 現在時刻（デフォルト値はUTC現在時刻）
     * @return 更新されたレコード数を成功値として返却。失敗時はエラーを含むResultオブジェクトを返却
     */
    fun finishRaid(
        raidId: Int,
        ranking: Int? = null,
        percentage: BigDecimal? = null,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<Long> {
        if (ranking != null && percentage != null) {
            return Result.failure(RaidDomainError.RankingAndPercentageBothSpecified)
        }

        if (ranking != null && ranking < MIN_RANKING) {
            return Result.failure(RaidDomainError.RankingOutOfRange(ranking))
        }

        if (percentage != null && (percentage <= BigDecimal.ZERO || percentage > MAX_PERCENTAGE)) {
            return Result.failure(RaidDomainError.PercentageOutOfRange(percentage))
        }

        val updated = unionRaidRepository.finishUnionRaid(raidId, now, ranking, percentage)
        return Result.success(updated)
    }

    /**
     * ユーザーが3ターン撃破を報告したことを記録します。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @param username ユーザー名
     * @param difficulty 難易度
     * @param now 報告の時刻（デフォルトは現在のUTC時刻）
     * @return 保存された報告のIDを含む `Result` オブジェクト
     */
    fun reportThreeTurn(
        raidId: Int,
        userId: Long,
        username: String,
        difficulty: Difficulty,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<Long> {
        val report = RaidReport(
            raidId = raidId,
            userId = userId,
            username = username,
            difficulty = difficulty.value,
            reportedAt = now,
        )
        return Result.success(raidReportRepository.saveRaidReport(report))
    }

    /**
     * ユーザーが指定の難易度で既に3凸報告済みかを返す。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @param difficulty 難易度
     * @return 報告済みなら `true`
     */
    fun hasReported(raidId: Int, userId: Long, difficulty: Difficulty): Boolean =
        raidReportRepository.findRaidReport(raidId, userId, difficulty.value) != null

    /**
     * ユーザーが報告済みの難易度を返す（ノーマル、ハードの順）。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @return 報告済みの難易度のリスト。報告がなければ空
     */
    fun findReportedDifficulties(raidId: Int, userId: Long): List<Difficulty> {
        val reported = raidReportRepository.findRaidReportsByRaidIdAndUserId(raidId, userId)
            .map { it.difficulty }
            .toSet()
        return Difficulty.entries.filter { it.value in reported }
    }

    /**
     * ユーザー自身の3凸報告を、指定した難易度分だけ取り消します。
     * 指定していない難易度の報告は残ります。終了済みのレイドは取り消せません。
     *
     * @param raidId レイドのID
     * @param userId 取り消すユーザーのID（本人の報告のみ対象）
     * @param difficulties 取り消す難易度。空の場合は `NoDifficultySelected`
     * @return 実際に削除された件数を含む `Result`。未報告の難易度だけを指定した場合は 0
     */
    fun cancelThreeTurnReports(
        raidId: Int,
        userId: Long,
        difficulties: Set<Difficulty>,
    ): Result<Long> {
        if (difficulties.isEmpty()) {
            return Result.failure(RaidDomainError.NoDifficultySelected)
        }

        val raid = unionRaidRepository.findUnionRaidById(raidId)
            ?: return Result.failure(RaidDomainError.RaidNotFound)
        if (raid.finishedAt != null) {
            return Result.failure(RaidDomainError.RaidAlreadyFinished)
        }

        val deleted = raidReportRepository.deleteRaidReports(raidId, userId, difficulties.map { it.value })
        return Result.success(deleted)
    }

    /**
     * 指定されたレイドの3凸報告をnormal/hard別のユーザー名リストに集計する。
     *
     * @param raidId レイド ID
     * @return 難易度ごとの3凸済みユーザー名リストを含む `RaidAggregation`
     */
    fun aggregateRaidResults(raidId: Int): RaidAggregation {
        val reports = raidReportRepository.findRaidReportsByRaidId(raidId)

        return RaidAggregation(
            normalUsers = reports.filter { it.difficulty == Difficulty.NORMAL.value }.map { it.username },
            hardUsers = reports.filter { it.difficulty == Difficulty.HARD.value }.map { it.username },
        )
    }
}
