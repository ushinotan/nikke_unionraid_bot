package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.dto.*
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * レイド情報取得API用のコントローラー
 *
 * Next.js BFFからの内部呼び出しを想定した読み取り専用APIを提供する。
 * 認証の本実装は #42 を参照。
 */
@RestController
@RequestMapping("/api")
class RaidApiController(
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
) {

    /**
     * 全てのギルド情報を取得する
     *
     * @return ギルド一覧
     */
    @GetMapping("/guilds")
    fun getGuilds(): ResponseEntity<GuildsResponse> {
        val guilds = guildRepository.findAllGuilds().map { guild ->
            GuildDto(
                guildId = guild.guildId,
                createdAt = guild.createdAt
            )
        }
        return ResponseEntity.ok(GuildsResponse(guilds))
    }

    /**
     * 指定されたギルドIDのレイド一覧を取得する
     *
     * @param guildId ギルドID
     * @return レイド一覧
     */
    @GetMapping("/guilds/{guildId}/raids")
    fun getRaidsByGuildId(@PathVariable guildId: Long): ResponseEntity<RaidsResponse> {
        val guild = guildRepository.findGuildById(guildId)
            ?: return ResponseEntity.notFound().build()

        val raids = unionRaidRepository.findUnionRaidsByGuildId(guildId).map { raid ->
            RaidSummaryDto(
                id = raid.id,
                guildId = raid.guildId,
                raidName = raid.raidName,
                startTime = raid.startTime,
                endTime = raid.endTime,
                notifyTime = raid.notifyTime,
                channelId = raid.channelId,
                ranking = raid.ranking,
                percentage = raid.percentage,
                finishedAt = raid.finishedAt,
                createdAt = raid.createdAt
            )
        }
        return ResponseEntity.ok(RaidsResponse(raids))
    }

    /**
     * 指定されたレイドIDの詳細情報を取得する
     * 参加者とレポート情報を含む
     *
     * @param raidId レイドID
     * @return レイド詳細情報
     */
    @GetMapping("/raids/{raidId}")
    fun getRaidDetail(@PathVariable raidId: Int): ResponseEntity<RaidDetailResponse> {
        val raid = unionRaidRepository.findUnionRaidById(raidId)
            ?: return ResponseEntity.notFound().build()

        val participants = raidParticipantRepository.findParticipantsByRaidId(raidId).map { participant ->
            ParticipantDto(
                id = participant.id,
                userId = participant.userId,
                username = participant.username,
                score = participant.score,
                joinedAt = participant.joinedAt
            )
        }

        val reports = raidReportRepository.findAllRaidReportsByRaidId(raidId).map { report ->
            ReportDto(
                id = report.id,
                userId = report.userId,
                username = report.username,
                difficulty = report.difficulty,
                is3t = report.is3t,
                reportedAt = report.reportedAt
            )
        }

        val raidSummary = RaidSummaryDto(
            id = raid.id,
            guildId = raid.guildId,
            raidName = raid.raidName,
            startTime = raid.startTime,
            endTime = raid.endTime,
            notifyTime = raid.notifyTime,
            channelId = raid.channelId,
            ranking = raid.ranking,
            percentage = raid.percentage,
            finishedAt = raid.finishedAt,
            createdAt = raid.createdAt
        )

        return ResponseEntity.ok(
            RaidDetailResponse(
                raid = raidSummary,
                participants = participants,
                reports = reports
            )
        )
    }

    /**
     * 例外ハンドラー
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            error = "INTERNAL_SERVER_ERROR",
            message = e.message ?: "An unexpected error occurred"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}
