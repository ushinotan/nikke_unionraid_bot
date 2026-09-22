package gg.nikke.raid.bot.dto

import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid

/**
 * UnionRaid エンティティを RaidSummaryDto に変換する拡張関数
 */
fun UnionRaid.toSummaryDto(): RaidSummaryDto = RaidSummaryDto(
    id = this.id,
    guildId = this.guildId.toString(),
    raidName = this.raidName,
    startTime = this.startTime,
    endTime = this.endTime,
    notifyTime = this.notifyTime,
    channelId = this.channelId.toString(),
    ranking = this.ranking,
    percentage = this.percentage,
    finishedAt = this.finishedAt,
    createdAt = this.createdAt
)

/**
 * RaidParticipant エンティティを ParticipantDto に変換する拡張関数
 */
fun RaidParticipant.toDto(): ParticipantDto = ParticipantDto(
    id = this.id,
    userId = this.userId.toString(),
    username = this.username,
    score = this.score,
    joinedAt = this.joinedAt
)

/**
 * RaidReport エンティティを ReportDto に変換する拡張関数
 */
fun RaidReport.toDto(): ReportDto = ReportDto(
    id = this.id,
    userId = this.userId.toString(),
    username = this.username,
    difficulty = this.difficulty,
    threeT = this.is3t,
    reportedAt = this.reportedAt
)
