package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.dto.*
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

// 拡張関数のインポート
import gg.nikke.raid.bot.dto.toSummaryDto
import gg.nikke.raid.bot.dto.toDto

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
    private val logger = LoggerFactory.getLogger(RaidApiController::class.java)

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

        val raids = unionRaidRepository.findUnionRaidsByGuildId(guildId).map { it.toSummaryDto() }
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

        val participants = raidParticipantRepository.findParticipantsByRaidId(raidId).map { it.toDto() }
        val reports = raidReportRepository.findAllRaidReportsByRaidId(raidId).map { it.toDto() }

        return ResponseEntity.ok(
            RaidDetailResponse(
                raid = raid.toSummaryDto(),
                participants = participants,
                reports = reports
            )
        )
    }

    /**
     * 不正なパスパラメータの型変換エラーハンドラー (400)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid parameter type for '${e.name}': ${e.value}", e)
        val errorResponse = ErrorResponse(
            error = "BAD_REQUEST",
            message = "Invalid parameter format"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * その他の予期しない例外ハンドラー (500)
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", e)
        val errorResponse = ErrorResponse(
            error = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred. Please contact the administrator."
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}
