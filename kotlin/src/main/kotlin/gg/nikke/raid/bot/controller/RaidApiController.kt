package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.dto.ErrorResponse
import gg.nikke.raid.bot.dto.GuildDto
import gg.nikke.raid.bot.dto.GuildsResponse
import gg.nikke.raid.bot.dto.RaidDetailResponse
import gg.nikke.raid.bot.dto.RaidsResponse
import gg.nikke.raid.bot.dto.toDto
import gg.nikke.raid.bot.dto.toSummaryDto
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.service.RaidDomainError
import gg.nikke.raid.bot.service.RaidDomainService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.JsonNode

/**
 * レイド情報の取得と、参加者・3T 報告の書き込みを行う内部 API 用のコントローラー
 *
 * Next.js BFF からの内部呼び出しを想定する（認証は `X-Internal-Api-Token`）。
 * **この API は操作者の権限を確かめない。** 本人か特権ユーザーか、終了済みレイドを操作してよいかは、
 * 呼び出し元（BFF の `checkParticipantEdit`）が確かめてから呼ぶこと。終了済みのレイドも書き込める。
 * 書き込みは必ず [RaidDomainService] を通す。Discord の通知 Embed は更新しない。
 */
@RestController
@RequestMapping("/api")
class RaidApiController(
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
    private val raidDomainService: RaidDomainService,
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
                guildId = guild.guildId.toString(),
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
     * 参加者を登録する
     *
     * @param raidId レイドID
     * @param body `{ "userId": "<文字列>", "username": "...", "score": 0以上のInt }`
     * @return 201: 登録した参加者。400: 入力不正、404: レイドが無い、409: 既に参加者
     */
    @PostMapping("/raids/{raidId}/participants")
    fun registerParticipant(
        @PathVariable raidId: Int,
        @RequestBody(required = false) body: JsonNode?,
    ): ResponseEntity<*> {
        val request = RaidWriteRequests.parseRegisterParticipant(body)
        return raidDomainService.registerParticipant(
            raidId = raidId,
            userId = request.userId,
            username = request.username,
            score = request.score,
        ).fold(
            onSuccess = { ResponseEntity.status(HttpStatus.CREATED).body(it.toDto()) },
            onFailure = { domainErrorResponse(it, "registerParticipant", raidId) },
        )
    }

    /**
     * 参加者のスコアを更新する
     *
     * @param raidId レイドID
     * @param userId 参加者のユーザーID
     * @param body `{ "score": 0以上のInt }`
     * @return 200: 更新後の参加者。400: 入力不正、404: レイドまたは参加者が無い
     */
    @PatchMapping("/raids/{raidId}/participants/{userId}")
    fun updateParticipantScore(
        @PathVariable raidId: Int,
        @PathVariable userId: Long,
        @RequestBody(required = false) body: JsonNode?,
    ): ResponseEntity<*> {
        RaidWriteRequests.requirePositiveUserId(userId)
        val request = RaidWriteRequests.parseUpdateScore(body)
        return raidDomainService.updateParticipantScore(raidId, userId, request.score).fold(
            onSuccess = { ResponseEntity.ok(it.toDto()) },
            onFailure = { domainErrorResponse(it, "updateParticipantScore", raidId) },
        )
    }

    /**
     * 参加を取り消す（参加者の行と 3T 報告をすべて削除する）
     *
     * @param raidId レイドID
     * @param userId 参加を取り消すユーザーID
     * @return 200: 削除結果。400: 入力不正、404: レイドが無い、または参加者の行も報告も無い
     */
    @DeleteMapping("/raids/{raidId}/participants/{userId}")
    fun cancelParticipation(
        @PathVariable raidId: Int,
        @PathVariable userId: Long,
    ): ResponseEntity<*> {
        RaidWriteRequests.requirePositiveUserId(userId)
        return raidDomainService.cancelParticipation(raidId, userId).fold(
            onSuccess = { result ->
                if (!result.participantRemoved && result.removedReportCount == 0L) {
                    domainErrorResponse(RaidDomainError.ParticipantNotFound, "cancelParticipation", raidId)
                } else {
                    ResponseEntity.ok(result.toDto())
                }
            },
            onFailure = { domainErrorResponse(it, "cancelParticipation", raidId) },
        )
    }

    /**
     * 3T 報告を記録する（参加していなければ参加者にも登録する）
     *
     * @param raidId レイドID
     * @param body `{ "userId": "<文字列>", "username": "...", "difficulty": "normal" | "hard" }`
     * @return 200: 報告結果。400: 入力不正、404: レイドが無い
     */
    @PostMapping("/raids/{raidId}/reports")
    fun reportThreeTurn(
        @PathVariable raidId: Int,
        @RequestBody(required = false) body: JsonNode?,
    ): ResponseEntity<*> {
        val request = RaidWriteRequests.parseReport(body)
        return raidDomainService.reportThreeTurn(
            raidId = raidId,
            userId = request.userId,
            username = request.username,
            difficulty = request.difficulty,
            allowFinished = true,
        ).fold(
            onSuccess = { ResponseEntity.ok(it.toDto()) },
            onFailure = { domainErrorResponse(it, "reportThreeTurn", raidId) },
        )
    }

    /**
     * 3T 報告を取り消す（報告が 0 件になったら参加者も削除する）
     *
     * @param raidId レイドID
     * @param userId 取り消すユーザーID
     * @param difficulty 難易度（`normal` | `hard`）
     * @return 200: 取り消し結果。400: 入力不正、404: レイドが無い、またはその難易度の報告が無い
     */
    @DeleteMapping("/raids/{raidId}/reports/{userId}/{difficulty}")
    fun cancelThreeTurnReport(
        @PathVariable raidId: Int,
        @PathVariable userId: Long,
        @PathVariable difficulty: String,
    ): ResponseEntity<*> {
        RaidWriteRequests.requirePositiveUserId(userId)
        val target = RaidWriteRequests.requireDifficulty(difficulty)
        return raidDomainService.cancelThreeTurnReports(
            raidId = raidId,
            userId = userId,
            difficulties = setOf(target),
            allowFinished = true,
        ).fold(
            onSuccess = { result ->
                if (result.cancelled.isEmpty()) {
                    domainErrorResponse(RaidDomainError.ThreeTurnReportNotFound, "cancelThreeTurnReport", raidId)
                } else {
                    ResponseEntity.ok(result.toDto())
                }
            },
            onFailure = { domainErrorResponse(it, "cancelThreeTurnReport", raidId) },
        )
    }

    /**
     * リクエストボディの不正ハンドラー (400)
     */
    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(e: InvalidRequestException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid request: {}", e.message)
        return errorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.message)
    }

    /**
     * 壊れた JSON・空のボディなど、読み取れないリクエストボディのハンドラー (400)
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        // e.message にはボディの一部が含まれることがあるためクラス名だけを出す
        logger.warn("Unreadable request body: {}", e.javaClass.simpleName)
        return errorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request body")
    }

    /**
     * Content-Type が JSON でないリクエストのハンドラー (415)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Unsupported media type: {}", e.javaClass.simpleName)
        return errorResponse(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "Content-Type must be application/json",
        )
    }

    /**
     * ドメインエラーをステータスに写す。想定していないエラーは 500。
     */
    private fun domainErrorResponse(error: Throwable, operation: String, raidId: Int): ResponseEntity<ErrorResponse> =
        when (error) {
            is RaidDomainError.RaidNotFound,
            is RaidDomainError.ParticipantNotFound,
            is RaidDomainError.ThreeTurnReportNotFound,
            -> errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", error.message)
            is RaidDomainError.ParticipantAlreadyExists -> errorResponse(HttpStatus.CONFLICT, "CONFLICT", error.message)
            else -> {
                logger.error("Unexpected failure: operation=$operation, raidId=$raidId", error)
                errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred. Please contact the administrator.",
                )
            }
        }

    private fun errorResponse(status: HttpStatus, error: String, message: String?): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(error = error, message = message ?: error))

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
