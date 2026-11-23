package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.service.Difficulty
import tools.jackson.databind.JsonNode

/**
 * 書き込み API のリクエストボディの不正を表す例外。コントローラーが 400 に変換する。
 * メッセージは固定文（項目名だけ）で、ボディの値は含めない。
 */
class InvalidRequestException(message: String) : RuntimeException(message)

/** 参加登録のリクエスト（検証済み）。 */
internal data class RegisterParticipantRequest(val userId: Long, val username: String, val score: Int)

/** スコア更新のリクエスト（検証済み）。 */
internal data class UpdateScoreRequest(val score: Int)

/** 3T 報告のリクエスト（検証済み）。 */
internal data class ReportRequest(val userId: Long, val username: String, val difficulty: Difficulty)

/**
 * 書き込み API のリクエストボディを `JsonNode` から厳密に読み取る。
 *
 * Kotlin の data class に直接バインドすると、`1.5` や `"100"` が `Int` に丸め込まれて通ってしまうため、
 * 1 項目ずつ型を確かめる（通らないものは [InvalidRequestException]）。
 */
internal object RaidWriteRequests {
    private const val MAX_USERNAME_LENGTH = 100
    private val USER_ID_PATTERN = Regex("^[0-9]{1,20}$")

    private val REGISTER_KEYS = setOf("userId", "username", "score")
    private val UPDATE_SCORE_KEYS = setOf("score")
    private val REPORT_KEYS = setOf("userId", "username", "difficulty")

    /** 参加登録のボディ（`userId`・`username`・`score`）を読み取る。 */
    fun parseRegisterParticipant(body: JsonNode?): RegisterParticipantRequest {
        val node = requireObject(body, REGISTER_KEYS)
        return RegisterParticipantRequest(
            userId = readUserId(node),
            username = readUsername(node),
            score = readScore(node),
        )
    }

    /** スコア更新のボディ（`score`）を読み取る。 */
    fun parseUpdateScore(body: JsonNode?): UpdateScoreRequest {
        val node = requireObject(body, UPDATE_SCORE_KEYS)
        return UpdateScoreRequest(score = readScore(node))
    }

    /** 3T 報告のボディ（`userId`・`username`・`difficulty`）を読み取る。 */
    fun parseReport(body: JsonNode?): ReportRequest {
        val node = requireObject(body, REPORT_KEYS)
        return ReportRequest(
            userId = readUserId(node),
            username = readUsername(node),
            difficulty = readDifficulty(node),
        )
    }

    /** パスの `userId` が 1 以上であることを確かめる。 */
    fun requirePositiveUserId(userId: Long): Long {
        if (userId < 1) throw InvalidRequestException("userId must be a positive integer")
        return userId
    }

    /** パスの `difficulty` を [Difficulty] に変換する。 */
    fun requireDifficulty(value: String): Difficulty =
        Difficulty.fromValueOrNull(value) ?: throw InvalidRequestException(DIFFICULTY_MESSAGE)

    private const val DIFFICULTY_MESSAGE = "difficulty must be 'normal' or 'hard'"

    private fun requireObject(body: JsonNode?, allowedKeys: Set<String>): JsonNode {
        if (body == null || !body.isObject) {
            throw InvalidRequestException("request body must be a JSON object")
        }
        if (body.propertyNames().any { it !in allowedKeys }) {
            throw InvalidRequestException("request body has unknown fields")
        }
        return body
    }

    private fun readUserId(node: JsonNode): Long {
        val value = node.get("userId")
        if (value == null || !value.isString) {
            throw InvalidRequestException("userId must be a string of digits")
        }
        val text = value.asString()
        val parsed = if (USER_ID_PATTERN.matches(text)) text.toLongOrNull() else null
        if (parsed == null || parsed < 1) {
            throw InvalidRequestException("userId must be a positive 64-bit integer string")
        }
        return parsed
    }

    private fun readUsername(node: JsonNode): String {
        val value = node.get("username")
        if (value == null || !value.isString) {
            throw InvalidRequestException("username must be a string")
        }
        val raw = value.asString()
        // 前後の改行・タブも trim で消えてしまうため、制御文字は trim の前の値で確かめる
        if (raw.any { it.isISOControl() }) {
            throw InvalidRequestException("username must not contain control characters")
        }
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_USERNAME_LENGTH) {
            throw InvalidRequestException("username must be 1 to $MAX_USERNAME_LENGTH characters")
        }
        return trimmed
    }

    private fun readScore(node: JsonNode): Int {
        val value = node.get("score")
        if (value == null || !value.isIntegralNumber || !value.canConvertToInt() || value.intValue() < 0) {
            throw InvalidRequestException("score must be an integer between 0 and ${Int.MAX_VALUE}")
        }
        return value.intValue()
    }

    private fun readDifficulty(node: JsonNode): Difficulty {
        val value = node.get("difficulty")
        if (value == null || !value.isString) {
            throw InvalidRequestException(DIFFICULTY_MESSAGE)
        }
        return Difficulty.fromValueOrNull(value.asString()) ?: throw InvalidRequestException(DIFFICULTY_MESSAGE)
    }
}
