package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.util.TimeUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
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
        if (Duration.between(startTime, endTime) < Duration.ofHours(1)) {
            return Result.failure(RaidDomainError.DurationTooShort)
        }

        if (unionRaidRepository.findActiveUnionRaidByGuildId(guildId, now) != null) {
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
     * @param ranking 終了時の順位（任意）。指定する場合、percentageと同時に指定することはできません
     * @param percentage 終了時の進行状況を示すパーセンテージ（任意）。0より大きく100以下である必要があります。指定する場合、rankingと同時に指定することはできません
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

        if (percentage != null && (percentage <= BigDecimal.ZERO || percentage > BigDecimal("100"))) {
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
     * @param difficulty 難易度（例: "normal" または "hard"）
     * @param now 報告の時刻（デフォルトは現在のUTC時刻）
     * @return 保存された報告のIDを含む `Result` オブジェクト
     */
    fun reportThreeTurn(
        raidId: Int,
        userId: Long,
        username: String,
        difficulty: String,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<Long> {
        val report = RaidReport(
            raidId = raidId,
            userId = userId,
            username = username,
            difficulty = difficulty,
            reportedAt = now,
        )
        return Result.success(raidReportRepository.saveRaidReport(report))
    }

    /**
     * 指定されたレイド ID に基づきレイドの集計結果を計算する。
     *
     * @param raidId レイド ID
     * @return レイド報告の集計結果を表す `RaidAggregation` オブジェクト
     */
    fun aggregateRaidResults(raidId: Int): RaidAggregation {
        val reports = raidReportRepository.findRaidReportsByRaidId(raidId)

        val normalCount = reports.count { it.difficulty == "normal" }
        val hardCount = reports.count { it.difficulty == "hard" }
        val total = normalCount + hardCount

        if (total == 0) {
            return RaidAggregation(
                normalCount = 0,
                hardCount = 0,
                normalPercentage = BigDecimal.ZERO,
                hardPercentage = BigDecimal.ZERO,
            )
        }

        val totalDecimal = total.toBigDecimal()
        val normalPercentage = normalCount.toBigDecimal()
            .divide(totalDecimal, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
        val hardPercentage = hardCount.toBigDecimal()
            .divide(totalDecimal, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)

        return RaidAggregation(
            normalCount = normalCount,
            hardCount = hardCount,
            normalPercentage = normalPercentage,
            hardPercentage = hardPercentage,
        )
    }
}
