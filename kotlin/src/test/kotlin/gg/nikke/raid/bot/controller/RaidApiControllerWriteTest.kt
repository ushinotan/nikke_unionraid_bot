package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RaidApiControllerWriteTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    // ---- 正常系 ----

    @Test
    fun `POST participants - 参加登録は201でuserIdが文字列で返りDBに保存される`() {
        val raid = createRaid(70_093_201L)

        postJson("/api/raids/${raid.id}/participants", """{"userId":"300000001","username":"ニック","score":12345}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value("300000001"))
            .andExpect(jsonPath("$.username").value("ニック"))
            .andExpect(jsonPath("$.score").value(12345))
            .andExpect(jsonPath("$.joinedAt").exists())

        assertEquals(12345, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000001L)?.score)
    }

    @Test
    fun `PATCH participants - スコア更新は200で更新後の参加者を返す`() {
        val raid = createRaid(70_093_202L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"300000002","username":"name","score":1}""")

        patchJson("/api/raids/${raid.id}/participants/300000002", """{"score":23456}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value("300000002"))
            .andExpect(jsonPath("$.username").value("name"))
            .andExpect(jsonPath("$.score").value(23456))
    }

    @Test
    fun `DELETE participants - 参加の取り消しで参加者と報告が消える`() {
        val raid = createRaid(70_093_203L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"300000003","username":"n","difficulty":"normal"}""")
        postJson("/api/raids/${raid.id}/reports", """{"userId":"300000003","username":"n","difficulty":"hard"}""")

        delete("/api/raids/${raid.id}/participants/300000003").perform()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantRemoved").value(true))
            .andExpect(jsonPath("$.removedReportCount").value(2))

        assertNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000003L))
        assertTrue(raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 300000003L).isEmpty())
    }

    @Test
    fun `DELETE participants - 参加者の行が無く報告だけある場合は報告を消して200`() {
        val raid = createRaid(70_093_204L)
        raidReportRepository.saveRaidReport(
            RaidReport(raidId = raid.id, userId = 300000004L, username = "legacy", difficulty = "normal"),
        )

        delete("/api/raids/${raid.id}/participants/300000004").perform()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participantRemoved").value(false))
            .andExpect(jsonPath("$.removedReportCount").value(1))
    }

    @Test
    fun `POST reports - 未参加なら参加者が自動登録され同じ難易度の再報告はalreadyReported`() {
        val raid = createRaid(70_093_205L)
        val body = """{"userId":"300000005","username":"n","difficulty":"hard"}"""

        postJson("/api/raids/${raid.id}/reports", body)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.alreadyReported").value(false))
            .andExpect(jsonPath("$.participantRegistered").value(true))
        postJson("/api/raids/${raid.id}/reports", body)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.alreadyReported").value(true))
            .andExpect(jsonPath("$.participantRegistered").value(false))

        assertEquals(0, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000005L)?.score)
    }

    @Test
    fun `DELETE reports - 最後の報告の取り消しで参加者も消える`() {
        val raid = createRaid(70_093_206L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"300000006","username":"n","difficulty":"normal"}""")
        postJson("/api/raids/${raid.id}/reports", """{"userId":"300000006","username":"n","difficulty":"hard"}""")

        delete("/api/raids/${raid.id}/reports/300000006/normal").perform()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cancelledDifficulties[0]").value("normal"))
            .andExpect(jsonPath("$.participantRemoved").value(false))
        delete("/api/raids/${raid.id}/reports/300000006/hard").perform()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cancelledDifficulties[0]").value("hard"))
            .andExpect(jsonPath("$.participantRemoved").value(true))

        assertNull(raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000006L))
    }

    // ---- 終了済みレイド ----

    @Test
    fun `終了済みレイドでも5本とも成功する`() {
        val raid = createRaid(70_093_207L)
        unionRaidRepository.finishUnionRaid(raid.id, OffsetDateTime.now(), null, null)
        val id = raid.id

        postJson("/api/raids/$id/participants", """{"userId":"300000007","username":"a","score":1}""")
            .andExpect(status().isCreated)
        patchJson("/api/raids/$id/participants/300000007", """{"score":2}""")
            .andExpect(status().isOk)
        postJson("/api/raids/$id/reports", """{"userId":"300000007","username":"a","difficulty":"normal"}""")
            .andExpect(status().isOk)
        delete("/api/raids/$id/reports/300000007/normal").perform()
            .andExpect(status().isOk)
        // 最後の報告の取り消しで参加者も消えたので、参加の取り消し用に登録し直す
        postJson("/api/raids/$id/participants", """{"userId":"300000007","username":"a","score":1}""")
            .andExpect(status().isCreated)
        delete("/api/raids/$id/participants/300000007").perform()
            .andExpect(status().isOk)
    }

    // ---- 404 / 409 ----

    @Test
    fun `存在しないレイドは5本とも404`() {
        val id = Int.MAX_VALUE

        postJson("/api/raids/$id/participants", """{"userId":"1","username":"a","score":1}""")
            .andExpect(status().isNotFound).andExpect(jsonPath("$.error").value("NOT_FOUND"))
        patchJson("/api/raids/$id/participants/1", """{"score":2}""").andExpect(status().isNotFound)
        delete("/api/raids/$id/participants/1").perform().andExpect(status().isNotFound)
        postJson("/api/raids/$id/reports", """{"userId":"1","username":"a","difficulty":"normal"}""")
            .andExpect(status().isNotFound)
        delete("/api/raids/$id/reports/1/normal").perform().andExpect(status().isNotFound)
    }

    @Test
    fun `未参加者のPATCHと参加者も報告も無いDELETEと未報告の難易度のDELETEは404`() {
        val raid = createRaid(70_093_208L)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"300000008","username":"a","difficulty":"normal"}""")

        patchJson("/api/raids/${raid.id}/participants/300000099", """{"score":2}""")
            .andExpect(status().isNotFound)
        delete("/api/raids/${raid.id}/participants/300000099").perform().andExpect(status().isNotFound)
        delete("/api/raids/${raid.id}/reports/300000008/hard").perform().andExpect(status().isNotFound)
        // 未報告の難易度を指定しても、既存の報告と参加者は消えない
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 300000008L).size)
        assertEquals(
            "a",
            raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000008L)?.username,
        )
    }

    @Test
    fun `参加登録の重複は409でスコアは変わらない`() {
        val raid = createRaid(70_093_209L)
        postJson("/api/raids/${raid.id}/participants", """{"userId":"300000009","username":"a","score":10}""")

        postJson("/api/raids/${raid.id}/participants", """{"userId":"300000009","username":"b","score":99}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("CONFLICT"))

        assertEquals(10, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 300000009L)?.score)
    }

    // ---- 400 / 415 ----

    @Test
    fun `パスの型不正は400`() {
        postJson("/api/raids/abc/participants", """{"userId":"1","username":"a","score":1}""")
            .andExpect(status().isBadRequest)
        patchJson("/api/raids/1/participants/abc", """{"score":1}""").andExpect(status().isBadRequest)
        patchJson("/api/raids/1/participants/0", """{"score":1}""").andExpect(status().isBadRequest)
        delete("/api/raids/1/participants/0").perform().andExpect(status().isBadRequest)
        delete("/api/raids/1/reports/1/extreme").perform().andExpect(status().isBadRequest)
    }

    @Test
    fun `ボディの不正は400でありレイドが存在しなくても検証が先に効く`() {
        val raid = createRaid(70_093_210L)

        postJson("/api/raids/${raid.id}/participants", """{"userId":"1","username":"a","score":-1}""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        postJson("/api/raids/${raid.id}/participants", """{"userId":"1","username":"a","score":1.5}""")
            .andExpect(status().isBadRequest)
        postJson("/api/raids/${raid.id}/participants", """{"userId":1,"username":"a","score":1}""")
            .andExpect(status().isBadRequest)
        patchJson("/api/raids/${raid.id}/participants/1", """{"score":"100"}""").andExpect(status().isBadRequest)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"1","username":"a","difficulty":"HARD"}""")
            .andExpect(status().isBadRequest)
        postJson("/api/raids/${raid.id}/reports", """{"userId":"1","username":"a","difficulty":"hard","x":1}""")
            .andExpect(status().isBadRequest)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `壊れたJSONは400`() {
        val raid = createRaid(70_093_211L)

        postJson("/api/raids/${raid.id}/participants", """{"userId":"1",""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `空のボディは400`() {
        val raid = createRaid(70_093_212L)

        post("/api/raids/${raid.id}/reports").contentType(MediaType.APPLICATION_JSON).perform()
            .andExpect(status().isBadRequest)
        patchJson("/api/raids/${raid.id}/participants/1", "").andExpect(status().isBadRequest)
    }

    @Test
    fun `ContentTypeがJSONでなければ415`() {
        val raid = createRaid(70_093_213L)

        post("/api/raids/${raid.id}/participants")
            .contentType(MediaType.TEXT_PLAIN)
            .content("""{"userId":"1","username":"a","score":1}""")
            .perform()
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"))
    }

    // ---- helpers ----

    private fun postJson(url: String, body: String): ResultActions =
        post(url).contentType(MediaType.APPLICATION_JSON).content(body).perform()

    private fun patchJson(url: String, body: String): ResultActions =
        patch(url).contentType(MediaType.APPLICATION_JSON).content(body).perform()

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.perform(): ResultActions =
        mockMvc.perform(this)

    private fun createRaid(guildId: Long): UnionRaid {
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        return unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "write-api-raid-$guildId",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 90_000_000L + guildId,
                createdAt = now,
            ),
        )
    }
}
