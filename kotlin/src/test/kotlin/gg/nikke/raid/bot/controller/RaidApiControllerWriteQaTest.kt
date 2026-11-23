package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.config.InternalApiTokenFilter
import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.OffsetDateTime

/**
 * QA #93: 参加者・3T 報告の書き込み API の補完（DEV の [RaidApiControllerWriteTest] は編集しない）。
 * 実 HTTP（MockMvc）+ 実 DB。クラスに `@Transactional` を付け、テストごとにロールバックする。
 * ID は 70_093_5xx（ギルド）と 53_xxx_xxx（ユーザー）を使い、DEV のテストと重ならない。
 *
 * 計画とコードの乖離:
 * - I-42: 通常の MockMvc（DEV 側）は [InternalApiTokenFilter] を通さないため、このクラスはフィルター付きの MockMvc も持つ。
 * - I-43: 400 の本文は `error` / `message`（固定文・項目名のみ）/ `timestamp`。送った値は含まれない。
 * - I-07 の `null`: `{"score":null}` は項目欠落と同じく 400（"score must be an integer ..."）。
 * - I-37 の PATCH / participants の壊れた JSON も `HttpMessageNotReadableException` で 400。
 */
@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RaidApiControllerWriteQaTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
    @Autowired private val internalApiTokenFilter: InternalApiTokenFilter,
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
) {
    private lateinit var mockMvc: MockMvc
    private lateinit var filteredMockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        filteredMockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilters<DefaultMockMvcBuilder>(internalApiTokenFilter)
            .build()
    }

    // ---- POST participants ----

    @Test
    fun `I-05 3T報告だけで参加者の行が無いユーザーを登録すると参加者が作られ報告は残る`() {
        val raid = createRaid(70_093_501L)
        raidReportRepository.saveRaidReport(
            RaidReport(raidId = raid.id, userId = 53_000_001L, username = "legacy", difficulty = "normal"),
        )

        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000001","username":"new","score":7}""")
            .andExpect(status().isCreated)

        assertEquals(7, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_001L)?.score)
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_001L).size)
    }

    @Test
    fun `I-06 必須項目のどれかが無いと400で行は作られない`() {
        val raid = createRaid(70_093_502L)
        val bodies = listOf(
            """{"username":"a","score":1}""",
            """{"userId":"53000002","score":1}""",
            """{"userId":"53000002","username":"a"}""",
        )
        bodies.forEach {
            postJson("/api/raids/${raid.id}/participants", it).andExpect(status().isBadRequest)
        }
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-07 scoreが不正な値は5種類とも400で行は作られない`() {
        val raid = createRaid(70_093_503L)
        listOf("-1", "1.5", "\"100\"", "2147483648", "null").forEach { score ->
            postJson("/api/raids/${raid.id}/participants", """{"userId":"53000003","username":"a","score":$score}""")
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        }
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-08 scoreの境界値0とInt最大値はPOSTでもPATCHでもそのまま保存される`() {
        val raid = createRaid(70_093_504L)

        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000004","username":"a","score":0}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$.score").value(0))
        assertEquals(0, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_004L)?.score)

        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000005","username":"b","score":2147483647}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$.score").value(2147483647))
        assertEquals(
            Int.MAX_VALUE,
            raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_005L)?.score,
        )

        patchJson("/api/raids/${raid.id}/participants/53000004", """{"score":2147483647}""")
            .andExpect(status().isOk)
        assertEquals(
            Int.MAX_VALUE,
            raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_004L)?.score,
        )
        patchJson("/api/raids/${raid.id}/participants/53000004", """{"score":0}""").andExpect(status().isOk)
        assertEquals(0, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_004L)?.score)
    }

    @Test
    fun `I-09 userIdが不正なら400`() {
        val raid = createRaid(70_093_505L)
        listOf("1", "\"abc\"", "\"0\"", "\"\"").forEach { userId ->
            postJson("/api/raids/${raid.id}/participants", """{"userId":$userId,"username":"a","score":1}""")
                .andExpect(status().isBadRequest)
        }
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-10 usernameが不正なら400`() {
        val raid = createRaid(70_093_506L)
        val tooLong = "a".repeat(101)
        listOf("\"   \"", "\"$tooLong\"", "\"a\\nb\"", "\"a\\tb\"").forEach { username ->
            postJson("/api/raids/${raid.id}/participants", """{"userId":"53000006","username":$username,"score":1}""")
                .andExpect(status().isBadRequest)
        }
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-11 想定外のキーを含むと400`() {
        val raid = createRaid(70_093_507L)
        postJson(
            "/api/raids/${raid.id}/participants",
            """{"userId":"53000007","username":"a","score":1,"difficulty":"hard"}""",
        ).andExpect(status().isBadRequest)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    // ---- PATCH participants ----

    @Test
    fun `I-12 スコア更新でDBのスコアだけが変わりusernameと参加時刻は変わらない`() {
        val raid = createRaid(70_093_508L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000008","username":"keep","score":5}""")
        val before = raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_008L)!!

        patchJson("/api/raids/${raid.id}/participants/53000008", """{"score":99}""").andExpect(status().isOk)

        val after = raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_008L)!!
        assertEquals(99, after.score)
        assertEquals("keep", after.username)
        assertEquals(before.joinedAt, after.joinedAt)
        assertEquals(before.id, after.id)
    }

    @Test
    fun `I-13 参加者でないユーザーのPATCHは404で行は作られない`() {
        val raid = createRaid(70_093_509L)
        patchJson("/api/raids/${raid.id}/participants/53000009", """{"score":1}""")
            .andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("NOT_FOUND"))
        assertNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_009L))
    }

    @Test
    fun `I-16 PATCHのscoreが不正なら400で既存スコアは変わらない`() {
        val raid = createRaid(70_093_510L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000010","username":"a","score":42}""")
        listOf("-1", "1.5", "\"100\"", "2147483648", "null").forEach { score ->
            patchJson("/api/raids/${raid.id}/participants/53000010", """{"score":$score}""")
                .andExpect(status().isBadRequest)
        }
        assertEquals(42, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_010L)?.score)
    }

    @Test
    fun `I-17 PATCHは対象以外のユーザーと他レイドの同じユーザーのスコアを変えない`() {
        val raid = createRaid(70_093_511L)
        val otherRaid = createRaid(70_093_512L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000011","username":"target","score":1}""")
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000012","username":"other","score":2}""")
        postJson("/api/raids/${otherRaid.id}/participants", """{"userId":"53000011","username":"target","score":3}""")

        patchJson("/api/raids/${raid.id}/participants/53000011", """{"score":100}""").andExpect(status().isOk)

        assertEquals(100, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_011L)?.score)
        assertEquals(2, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_012L)?.score)
        assertEquals(3, raidParticipantRepository.findParticipantByRaidIdAndUserId(otherRaid.id, 53_000_011L)?.score)
    }

    // ---- DELETE participants ----

    @Test
    fun `I-23 参加の取り消しは対象以外のユーザーと他レイドの参加者と報告を残す`() {
        val raid = createRaid(70_093_513L)
        val otherRaid = createRaid(70_093_514L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000013","username":"t","difficulty":"normal"}""")
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000014","username":"o","difficulty":"normal"}""")
        postJson("/api/raids/${otherRaid.id}/reports", """{"userId":"53000013","username":"t","difficulty":"hard"}""")

        delete("/api/raids/${raid.id}/participants/53000013").perform().andExpect(status().isOk)

        assertNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_013L))
        assertTrue(raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_013L).isEmpty())
        assertNotNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_014L))
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_014L).size)
        assertNotNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(otherRaid.id, 53_000_013L))
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(otherRaid.id, 53_000_013L).size)
    }

    // ---- POST reports ----

    @Test
    fun `I-25 参加者がスコア入力済みで3T報告してもusernameとスコアは上書きされない`() {
        val raid = createRaid(70_093_515L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000015","username":"manual","score":777}""")

        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000015","username":"changed","difficulty":"normal"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantRegistered").value(false))

        val participant = raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_015L)!!
        assertEquals("manual", participant.username)
        assertEquals(777, participant.score)
    }

    @Test
    fun `I-26 同じ難易度の再報告は報告1行のままでalreadyReportedになる`() {
        val raid = createRaid(70_093_516L)
        val body = """{"userId":"53000016","username":"n","difficulty":"normal"}"""
        postJson("/api/raids/${raid.id}/reports", body).andExpect(status().isOk)

        postJson("/api/raids/${raid.id}/reports", body)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.alreadyReported").value(true))

        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_016L).size)
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    @Test
    fun `I-28 存在しないレイドへの報告は404で報告も参加者も作られない`() {
        val raid = createRaid(70_093_517L)
        postJson("/api/raids/${Int.MAX_VALUE}/reports", """{"userId":"53000017","username":"n","difficulty":"normal"}""")
            .andExpect(status().isNotFound)
        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(Int.MAX_VALUE).isEmpty())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(Int.MAX_VALUE).isEmpty())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-29 difficultyが不正なら400で報告も参加者も作られない`() {
        val raid = createRaid(70_093_518L)
        listOf("\"HARD\"", "\"extreme\"", "1").forEach { difficulty ->
            postJson("/api/raids/${raid.id}/reports", """{"userId":"53000018","username":"n","difficulty":$difficulty}""")
                .andExpect(status().isBadRequest)
        }
        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(raid.id).isEmpty())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `I-30 3T報告の必須項目のどれかが無いと400`() {
        val raid = createRaid(70_093_519L)
        listOf(
            """{"username":"n","difficulty":"normal"}""",
            """{"userId":"53000019","difficulty":"normal"}""",
            """{"userId":"53000019","username":"n"}""",
        ).forEach {
            postJson("/api/raids/${raid.id}/reports", it).andExpect(status().isBadRequest)
        }
        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(raid.id).isEmpty())
    }

    // ---- DELETE reports ----

    @Test
    fun `I-31 唯一の報告を取り消すと報告と参加者の両方が消えDBにも残らない`() {
        val raid = createRaid(70_093_520L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000020","username":"n","difficulty":"hard"}""")

        delete("/api/raids/${raid.id}/reports/53000020/hard").perform()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantRemoved").value(true))

        assertTrue(raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_020L).isEmpty())
        assertNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_020L))
    }

    @Test
    fun `I-32 2難易度のうち1つを取り消しても残りの報告と参加者は残る`() {
        val raid = createRaid(70_093_521L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000021","username":"n","difficulty":"normal"}""")
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000021","username":"n","difficulty":"hard"}""")

        delete("/api/raids/${raid.id}/reports/53000021/normal").perform().andExpect(status().isOk)

        val remaining = raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_021L)
        assertEquals(listOf("hard"), remaining.map { it.difficulty })
        assertNotNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 53_000_021L))
    }

    @Test
    fun `I-34 パスのdifficultyが不正なら500ではなく400`() {
        val raid = createRaid(70_093_522L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000022","username":"n","difficulty":"normal"}""")
        delete("/api/raids/${raid.id}/reports/53000022/extreme").perform()
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        delete("/api/raids/${raid.id}/reports/53000022/HARD").perform().andExpect(status().isBadRequest)
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 53_000_022L).size)
    }

    // ---- 共通 ----

    @Test
    fun `I-37 壊れたJSONはreportsとPATCHでも400で500にならない`() {
        val raid = createRaid(70_093_523L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"1",""").andExpect(status().isBadRequest)
        patchJson("/api/raids/${raid.id}/participants/1", """{"score":""").andExpect(status().isBadRequest)
    }

    @Test
    fun `I-38 ボディが空ならparticipantsのPOSTも400で500にならない`() {
        val raid = createRaid(70_093_524L)
        post("/api/raids/${raid.id}/participants").contentType(MediaType.APPLICATION_JSON).perform()
            .andExpect(status().isBadRequest)
        postJson("/api/raids/${raid.id}/reports", "").andExpect(status().isBadRequest)
    }

    @Test
    fun `I-39 ContentTypeがtext-plainならreportsとPATCHも415`() {
        val raid = createRaid(70_093_525L)
        post("/api/raids/${raid.id}/reports").contentType(MediaType.TEXT_PLAIN)
            .content("""{"userId":"1","username":"a","difficulty":"normal"}""").perform()
            .andExpect(status().isUnsupportedMediaType)
        patch("/api/raids/${raid.id}/participants/1").contentType(MediaType.TEXT_PLAIN)
            .content("""{"score":1}""").perform()
            .andExpect(status().isUnsupportedMediaType)
    }

    @Test
    fun `I-40 パスのraidIdが数値でなければ5本とも400でuserIdがabcでも400`() {
        postJson("/api/raids/abc/participants", """{"userId":"1","username":"a","score":1}""")
            .andExpect(status().isBadRequest)
        patchJson("/api/raids/abc/participants/1", """{"score":1}""").andExpect(status().isBadRequest)
        delete("/api/raids/abc/participants/1").perform().andExpect(status().isBadRequest)
        postJson("/api/raids/abc/reports", """{"userId":"1","username":"a","difficulty":"normal"}""")
            .andExpect(status().isBadRequest)
        delete("/api/raids/abc/reports/1/normal").perform().andExpect(status().isBadRequest)
        delete("/api/raids/1/participants/abc").perform().andExpect(status().isBadRequest)
        delete("/api/raids/1/reports/abc/normal").perform().andExpect(status().isBadRequest)
        delete("/api/raids/1/reports/0/normal").perform().andExpect(status().isBadRequest)
    }

    @Test
    fun `I-42 誤ったトークンの書き込みは401で何も書かれない`() {
        val raid = createRaid(70_093_526L)

        filteredMockMvc.perform(
            post("/api/raids/${raid.id}/reports")
                .header(InternalApiTokenFilter.HEADER_NAME, "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"53000026","username":"n","difficulty":"normal"}"""),
        ).andExpect(status().isUnauthorized).andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        filteredMockMvc.perform(
            delete("/api/raids/${raid.id}/participants/53000026")
                .header(InternalApiTokenFilter.HEADER_NAME, "wrong-token"),
        ).andExpect(status().isUnauthorized)

        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(raid.id).isEmpty())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())

        // 正しいトークンなら同じリクエストが通る（フィルターが書き込みだけを塞いでいないこと）
        filteredMockMvc.perform(
            post("/api/raids/${raid.id}/reports")
                .header(InternalApiTokenFilter.HEADER_NAME, "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":"53000026","username":"n","difficulty":"normal"}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `I-43 400の応答に送ったusernameや値が含まれない`() {
        val raid = createRaid(70_093_527L)
        val secret = "SECRET-USER-NAME-12345"

        val bodies = listOf(
            """{"userId":"53000027","username":"$secret","score":-1}""",
            """{"userId":"53000027","username":"$secret","score":"leak-score"}""",
            """{"userId":"leak-id","username":"$secret","score":1}""",
            """{"userId":"53000027","username":"$secret","score":1,"leak-key":1}""",
        )
        bodies.forEach { body ->
            val response = postJson("/api/raids/${raid.id}/participants", body)
                .andExpect(status().isBadRequest)
                .andReturn().response.contentAsString
            assertFalse(response.contains(secret), "username が応答に含まれた: $response")
            assertFalse(response.contains("leak-score"), "score が応答に含まれた: $response")
            assertFalse(response.contains("leak-id"), "userId が応答に含まれた: $response")
        }
        // 壊れた JSON でもボディの断片を返さない
        postJson("/api/raids/${raid.id}/participants", """{"username":"$secret",""")
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secret))))
    }

    // ---- 回帰 ----

    @Test
    fun `G-02 書き込み後のGET raidsに参加者と報告の変更が反映される`() {
        val raid = createRaid(70_093_528L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"53000028","username":"viewer","score":10}""")
        postJson("/api/raids/${raid.id}/reports", """{"userId":"53000028","username":"viewer","difficulty":"hard"}""")
        patchJson("/api/raids/${raid.id}/participants/53000028", """{"score":55}""")

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants.length()").value(1))
            .andExpect(jsonPath("$.participants[0].userId").value("53000028"))
            .andExpect(jsonPath("$.participants[0].username").value("viewer"))
            .andExpect(jsonPath("$.participants[0].score").value(55))
            .andExpect(jsonPath("$.reports.length()").value(1))
            .andExpect(jsonPath("$.reports[0].difficulty").value("hard"))

        delete("/api/raids/${raid.id}/participants/53000028").perform().andExpect(status().isOk)

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants").isEmpty)
            .andExpect(jsonPath("$.reports").isEmpty)
    }

    // ---- helpers ----

    private fun postJson(url: String, body: String): ResultActions =
        post(url).contentType(MediaType.APPLICATION_JSON).content(body).perform()

    private fun patchJson(url: String, body: String): ResultActions =
        patch(url).contentType(MediaType.APPLICATION_JSON).content(body).perform()

    private fun MockHttpServletRequestBuilder.perform(): ResultActions = mockMvc.perform(this)

    private fun createRaid(guildId: Long): UnionRaid {
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        return unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "write-api-qa-raid-$guildId",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 90_000_000L + guildId,
                createdAt = now,
            ),
        )
    }
}
