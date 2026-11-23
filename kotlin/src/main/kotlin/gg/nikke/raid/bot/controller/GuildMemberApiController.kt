package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.dto.ErrorResponse
import gg.nikke.raid.bot.dto.GuildMembersResponse
import gg.nikke.raid.bot.dto.toDto
import gg.nikke.raid.bot.service.GuildMemberService
import gg.nikke.raid.bot.service.RaidDomainError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * ギルドメンバー一覧取得API用のコントローラー
 *
 * Next.js BFFからの内部呼び出しを想定する。呼び出し元が権限
 * （セッションの guildIds・特権ユーザー）を確認する前提。
 */
@RestController
@RequestMapping("/api")
class GuildMemberApiController(
    private val guildMemberService: GuildMemberService,
) {
    private val logger = LoggerFactory.getLogger(GuildMemberApiController::class.java)

    /**
     * 指定ギルドのメンバー一覧を取得する（bot ユーザーは除外）
     *
     * @param guildId ギルドID
     * @param query 名前の部分一致検索（任意、100文字以内。前後の空白はサービス側で除去）
     * @param limit 最大件数（任意、1〜100、既定100）
     * @return メンバー一覧。400: パラメーター不正、404: bot がギルドにいない、503: 取得不能
     */
    @GetMapping("/guilds/{guildId}/members")
    fun getGuildMembers(
        @PathVariable guildId: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false, defaultValue = DEFAULT_LIMIT.toString()) limit: Int,
    ): ResponseEntity<*> {
        if (query != null && query.length > MAX_QUERY_LENGTH) {
            return badRequest("query is too long")
        }
        if (limit !in 1..MAX_LIMIT) {
            return badRequest("limit must be between 1 and $MAX_LIMIT")
        }

        return guildMemberService.findGuildMembers(guildId, query, limit).fold(
            onSuccess = { page ->
                ResponseEntity.ok(
                    GuildMembersResponse(
                        members = page.members.map { it.toDto() },
                        truncated = page.truncated,
                    ),
                )
            },
            onFailure = { error ->
                when (error) {
                    is RaidDomainError.GuildNotFound -> errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", error.message)
                    is RaidDomainError.GuildMembersUnavailable ->
                        errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", error.message)
                    else -> {
                        logger.error("Unexpected failure while listing guild members: guildId=$guildId", error)
                        internalError()
                    }
                }
            },
        )
    }

    /**
     * 不正なパラメーターの型変換エラーハンドラー (400)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid parameter type: name=${e.name}")
        return badRequest("Invalid parameter format")
    }

    /**
     * その他の予期しない例外ハンドラー (500)
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", e)
        return internalError()
    }

    private fun badRequest(message: String): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)

    private fun internalError(): ResponseEntity<ErrorResponse> = errorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "An unexpected error occurred. Please contact the administrator.",
    )

    private fun errorResponse(status: HttpStatus, error: String, message: String?): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(error = error, message = message ?: error))

    companion object {
        private const val DEFAULT_LIMIT = 100
        private const val MAX_LIMIT = 100
        private const val MAX_QUERY_LENGTH = 100
    }
}
