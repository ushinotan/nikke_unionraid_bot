package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.service.Difficulty
import gg.nikke.raid.bot.service.RaidDomainService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.OffsetDateTime

/** QA #92 G-03: 3凸報告で自動登録された参加者が GET /api/raids/{raidId} に出る（応答の形は変わらない）。 */
@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RaidApiControllerQaTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
    private val guildRepository: GuildRepository,
    private val raidDomainService: RaidDomainService,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    fun `G-03 3凸報告した人がparticipantsにスコア0で出て報告取り消しで消える`() {
        val guildId = 70_092_601L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId, raidName = "qa-g03", startTime = now, endTime = now.plusHours(2),
            channelId = 80_600_001L, now = now,
        ).getOrThrow()
        raidDomainService.reportThreeTurn(raid.id, 92_600_001L, "reporter", Difficulty.NORMAL).getOrThrow()

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants.length()").value(1))
            .andExpect(jsonPath("$.participants[0].userId").value("92600001"))
            .andExpect(jsonPath("$.participants[0].username").value("reporter"))
            .andExpect(jsonPath("$.participants[0].score").value(0))
            .andExpect(jsonPath("$.reports.length()").value(1))

        raidDomainService.cancelThreeTurnReports(raid.id, 92_600_001L, setOf(Difficulty.NORMAL)).getOrThrow()

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants").isEmpty)
            .andExpect(jsonPath("$.reports").isEmpty)
    }
}
