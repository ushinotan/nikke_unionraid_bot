package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.service.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class RaidWriteRequestsTest {
    private val mapper = JsonMapper.builder().build()

    private fun json(text: String): JsonNode = mapper.readTree(text)

    private fun assertInvalidRegister(text: String) {
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseRegisterParticipant(json(text)) }
    }

    private fun assertInvalidReport(text: String) {
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseReport(json(text)) }
    }

    @Test
    fun `正しい3種類のボディを読み取れる`() {
        val register = RaidWriteRequests.parseRegisterParticipant(
            json("""{"userId":"300000001","username":"ニック","score":12345}"""),
        )
        assertEquals(RegisterParticipantRequest(300000001L, "ニック", 12345), register)
        assertEquals(UpdateScoreRequest(23456), RaidWriteRequests.parseUpdateScore(json("""{"score":23456}""")))
        val report = RaidWriteRequests.parseReport(
            json("""{"userId":"300000001","username":"ニック","difficulty":"hard"}"""),
        )
        assertEquals(ReportRequest(300000001L, "ニック", Difficulty.HARD), report)
    }

    @Test
    fun `scoreは0とInt最大値を受け付ける`() {
        assertEquals(0, RaidWriteRequests.parseUpdateScore(json("""{"score":0}""")).score)
        assertEquals(Int.MAX_VALUE, RaidWriteRequests.parseUpdateScore(json("""{"score":2147483647}""")).score)
    }

    @Test
    fun `usernameの前後の空白は除かれる`() {
        val request = RaidWriteRequests.parseRegisterParticipant(
            json("""{"userId":"1","username":"  ニック  ","score":1}"""),
        )
        assertEquals("ニック", request.username)
    }

    @Test
    fun `ボディがnullや配列や文字列なら400`() {
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseRegisterParticipant(null) }
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseUpdateScore(json("[]")) }
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseReport(json("\"x\"")) }
    }

    @Test
    fun `未知のキーは400`() {
        assertInvalidRegister("""{"userId":"1","username":"u","score":1,"difficulty":"normal"}""")
        assertThrows(InvalidRequestException::class.java) {
            RaidWriteRequests.parseUpdateScore(json("""{"score":1,"username":"u"}"""))
        }
        assertInvalidReport("""{"userId":"1","username":"u","difficulty":"normal","score":1}""")
    }

    @Test
    fun `必須項目の欠落は400`() {
        assertInvalidRegister("""{"username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"1","score":1}""")
        assertInvalidRegister("""{"userId":"1","username":"u"}""")
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.parseUpdateScore(json("{}")) }
        assertInvalidReport("""{"userId":"1","username":"u"}""")
    }

    @Test
    fun `userIdが数値や空文字や不正な文字列なら400`() {
        assertInvalidRegister("""{"userId":1,"username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"","username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"abc","username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"0","username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"-1","username":"u","score":1}""")
        assertInvalidRegister("""{"userId":"123456789012345678901","username":"u","score":1}""")
        // 20 桁以内でも Long を超える値
        assertInvalidRegister("""{"userId":"99999999999999999999","username":"u","score":1}""")
    }

    @Test
    fun `usernameが不正なら400`() {
        assertInvalidRegister("""{"userId":"1","username":"   ","score":1}""")
        assertInvalidRegister("""{"userId":"1","username":"${"a".repeat(101)}","score":1}""")
        assertInvalidRegister("""{"userId":"1","username":123,"score":1}""")
        assertInvalidReport("""{"userId":"1","username":null,"difficulty":"normal"}""")
    }

    @Test
    fun `usernameは100文字ちょうどなら受け付ける`() {
        val request = RaidWriteRequests.parseRegisterParticipant(
            json("""{"userId":"1","username":"${"a".repeat(100)}","score":1}"""),
        )
        assertEquals(100, request.username.length)
    }

    @Test
    fun `usernameに改行やタブなどの制御文字があれば400`() {
        assertInvalidRegister("""{"userId":"1","username":"a\nb","score":1}""")
        assertInvalidRegister("""{"userId":"1","username":"a\tb","score":1}""")
        assertInvalidRegister("""{"userId":"1","username":"a\r\nb","score":1}""")
        // 前後の制御文字は trim で消えるが、通さない
        assertInvalidRegister("""{"userId":"1","username":"ab\n","score":1}""")
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "1.5", "\"100\"", "2147483648", "null", "true", "1e3"])
    fun `scoreが不正なら400`(score: String) {
        assertInvalidRegister("""{"userId":"1","username":"u","score":$score}""")
        assertThrows(InvalidRequestException::class.java) {
            RaidWriteRequests.parseUpdateScore(json("""{"score":$score}"""))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"HARD\"", "\"extreme\"", "1", "null", "\"\""])
    fun `difficultyが不正なら400`(difficulty: String) {
        assertInvalidReport("""{"userId":"1","username":"u","difficulty":$difficulty}""")
    }

    @Test
    fun `パスのuserIdとdifficultyの検証`() {
        assertEquals(5L, RaidWriteRequests.requirePositiveUserId(5L))
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.requirePositiveUserId(0L) }
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.requirePositiveUserId(-1L) }
        assertEquals(Difficulty.NORMAL, RaidWriteRequests.requireDifficulty("normal"))
        assertThrows(InvalidRequestException::class.java) { RaidWriteRequests.requireDifficulty("Normal") }
    }
}
