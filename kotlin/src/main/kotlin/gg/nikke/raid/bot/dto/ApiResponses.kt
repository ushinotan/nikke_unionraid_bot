package gg.nikke.raid.bot.dto

import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * ギルド一覧のレスポンス
 */
data class GuildsResponse(
    val guilds: List<GuildDto>
)

/**
 * ギルド情報
 */
data class GuildDto(
    val guildId: String,
    val createdAt: OffsetDateTime?
)

/**
 * レイド一覧のレスポンス
 */
data class RaidsResponse(
    val raids: List<RaidSummaryDto>
)

/**
 * レイド概要情報
 */
data class RaidSummaryDto(
    val id: Int,
    val guildId: String,
    val raidName: String,
    val startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    val notifyTime: OffsetDateTime?,
    val channelId: String,
    val ranking: Int?,
    val percentage: BigDecimal?,
    val finishedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime?
)

/**
 * レイド詳細のレスポンス
 */
data class RaidDetailResponse(
    val raid: RaidSummaryDto,
    val participants: List<ParticipantDto>,
    val reports: List<ReportDto>
)

/**
 * 参加者情報
 */
data class ParticipantDto(
    val id: Int,
    val userId: String,
    val username: String,
    val score: Int,
    val joinedAt: OffsetDateTime?
)

/**
 * レポート情報
 */
data class ReportDto(
    val id: Int,
    val userId: String,
    val username: String,
    val difficulty: String,
    val threeT: Int,
    val reportedAt: OffsetDateTime?
)

/**
 * エラーレスポンス
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
