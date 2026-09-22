package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RaidApiControllerTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
) {
    private lateinit var mockMvc: MockMvc

    @org.junit.jupiter.api.BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }

    @Test
    fun `GET guilds - ギルド一覧を取得できる`() {
        val now = OffsetDateTime.now()
        
        // テストデータ作成
        guildRepository.insertGuild(Guild(guildId = 100_000_001L, createdAt = now))
        guildRepository.insertGuild(Guild(guildId = 100_000_002L, createdAt = now))

        mockMvc.perform(get("/api/guilds"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.guilds").isArray)
            .andExpect(jsonPath("$.guilds.length()").value(2))
            .andExpect(jsonPath("$.guilds[?(@.guildId == '100000001')]").exists())
            .andExpect(jsonPath("$.guilds[?(@.guildId == '100000002')]").exists())
            // Snowflake IDが文字列としてシリアライズされることを確認
            .andExpect(jsonPath("$.guilds[0].guildId").isString)
    }

    @Test
    fun `GET guilds - ギルドが存在しない場合は空配列を返す`() {
        mockMvc.perform(get("/api/guilds"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.guilds").isArray)
            .andExpect(jsonPath("$.guilds").isEmpty)
    }

    @Test
    fun `GET guilds guildId raids - 指定ギルドのレイド一覧を取得できる`() {
        val guildId = 100_000_003L
        val now = OffsetDateTime.now()
        
        // テストデータ作成
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "テストレイド1",
                startTime = now.minusHours(2),
                endTime = now.minusHours(1),
                channelId = 200_000_001L,
                ranking = 5,
                percentage = BigDecimal("1.23"),
                finishedAt = now.minusHours(1),
                createdAt = now.minusHours(3),
            )
        )
        unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "テストレイド2",
                startTime = now.plusHours(1),
                endTime = now.plusHours(2),
                channelId = 200_000_001L,
                createdAt = now,
            )
        )

        mockMvc.perform(get("/api/guilds/$guildId/raids"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.raids").isArray)
            .andExpect(jsonPath("$.raids.length()").value(2))
            .andExpect(jsonPath("$.raids[?(@.raidName == 'テストレイド1')]").exists())
            .andExpect(jsonPath("$.raids[?(@.raidName == 'テストレイド2')]").exists())
            .andExpect(jsonPath("$.raids[?(@.raidName == 'テストレイド1')].ranking").value(5))
    }

    @Test
    fun `GET guilds guildId raids - 存在しないギルドの場合は404を返す`() {
        val nonExistentGuildId = 999_999_999L

        mockMvc.perform(get("/api/guilds/$nonExistentGuildId/raids"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET guilds guildId raids - レイドが存在しない場合は空配列を返す`() {
        val guildId = 100_000_004L
        val now = OffsetDateTime.now()
        
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        mockMvc.perform(get("/api/guilds/$guildId/raids"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.raids").isArray)
            .andExpect(jsonPath("$.raids").isEmpty)
    }

    @Test
    fun `GET raids raidId - レイド詳細を取得できる`() {
        val guildId = 100_000_005L
        val now = OffsetDateTime.now()
        
        // テストデータ作成
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "詳細テストレイド",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 200_000_002L,
                createdAt = now,
            )
        )

        // 参加者を追加
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(
                raidId = raid.id,
                userId = 300_000_001L,
                username = "参加者1",
                score = 1500000,
                joinedAt = now,
            )
        )
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(
                raidId = raid.id,
                userId = 300_000_002L,
                username = "参加者2",
                score = 1200000,
                joinedAt = now,
            )
        )

        // レポートを追加
        raidReportRepository.saveRaidReport(
            RaidReport(
                raidId = raid.id,
                userId = 300_000_001L,
                username = "参加者1",
                difficulty = "hard",
                is3t = 1,
                reportedAt = now,
            )
        )

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.raid.id").value(raid.id))
            .andExpect(jsonPath("$.raid.raidName").value("詳細テストレイド"))
            // Snowflake IDsが文字列としてシリアライズされることを確認
            .andExpect(jsonPath("$.raid.guildId").isString)
            .andExpect(jsonPath("$.raid.channelId").isString)
            .andExpect(jsonPath("$.participants").isArray)
            .andExpect(jsonPath("$.participants.length()").value(2))
            .andExpect(jsonPath("$.participants[0].userId").value("300000001"))
            .andExpect(jsonPath("$.participants[0].score").value(1500000))
            .andExpect(jsonPath("$.participants[1].userId").value("300000002"))
            .andExpect(jsonPath("$.participants[1].score").value(1200000))
            .andExpect(jsonPath("$.reports").isArray)
            .andExpect(jsonPath("$.reports.length()").value(1))
            .andExpect(jsonPath("$.reports[0].userId").value("300000001"))
            .andExpect(jsonPath("$.reports[0].difficulty").value("hard"))
            .andExpect(jsonPath("$.reports[0].is3t").value(1))
    }

    @Test
    fun `GET raids raidId - 参加者とレポートが空の場合も正常に取得できる`() {
        val guildId = 100_000_006L
        val now = OffsetDateTime.now()
        
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "空レイド",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 200_000_003L,
                createdAt = now,
            )
        )

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.raid.id").value(raid.id))
            .andExpect(jsonPath("$.participants").isArray)
            .andExpect(jsonPath("$.participants").isEmpty)
            .andExpect(jsonPath("$.reports").isArray)
            .andExpect(jsonPath("$.reports").isEmpty)
    }

    @Test
    fun `GET raids raidId - 存在しないレイドの場合は404を返す`() {
        val nonExistentRaidId = 999_999

        mockMvc.perform(get("/api/raids/$nonExistentRaidId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET raids raidId - 参加者がスコア降順でソートされる`() {
        val guildId = 100_000_007L
        val now = OffsetDateTime.now()
        
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "ソートテスト",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 200_000_004L,
                createdAt = now,
            )
        )

        // スコアが異なる参加者を順不同で登録
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 300_000_003L, username = "中", score = 1500000, joinedAt = now)
        )
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 300_000_004L, username = "高", score = 1800000, joinedAt = now)
        )
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 300_000_005L, username = "低", score = 1200000, joinedAt = now)
        )

        mockMvc.perform(get("/api/raids/${raid.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.participants[0].username").value("高"))
            .andExpect(jsonPath("$.participants[0].score").value(1800000))
            .andExpect(jsonPath("$.participants[1].username").value("中"))
            .andExpect(jsonPath("$.participants[1].score").value(1500000))
            .andExpect(jsonPath("$.participants[2].username").value("低"))
            .andExpect(jsonPath("$.participants[2].score").value(1200000))
    }
}
