package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class RaidReportRepositoryIntegrationTest(
        private val guildRepository: GuildRepository,
        private val unionRaidRepository: UnionRaidRepository,
        private val raidReportRepository: RaidReportRepository,
        ) {

    @Test
    fun `3凸報告を登録して取得できる`() {
        val guildId = 40_000_001L
        val now = OffsetDateTime.now()
        val raid = createRaid(guildId, now)

        val saved = raidReportRepository.saveRaidReport(
                RaidReport(
                        raidId = raid.id,
                        userId = 50_000_001L,
                        username = "test-user-1",
                        difficulty = "normal",
                        is3t = 1,
                        reportedAt = now,
                        )
        )

        val found = raidReportRepository.findRaidReport(
                raidId = raid.id,
                userId = 50_000_001L,
                difficulty = "normal",
                )

        assertEquals(1L, saved)
        assertNotNull(found)
        assertEquals("test-user-1", found?.username)
        assertEquals(1, found?.is3t)
    }

    @Test
    fun `同じraidId userId difficultyの3凸報告はupsertで更新される`() {
        val guildId = 40_000_002L
        val now = OffsetDateTime.now()

        val raid = createRaid(guildId, now)

        raidReportRepository.saveRaidReport(
                RaidReport(
                        raidId = raid.id,
                        userId = 50_000_002L,
                        username = "before-name",
                        difficulty = "hard",
                        is3t = 1,
                        reportedAt = now,
                        )
        )

        val updatedCount = raidReportRepository.saveRaidReport(
                RaidReport(
                        raidId = raid.id,
                        userId = 50_000_002L,
                        username = "after-name",
                        difficulty = "hard",
                        is3t = 1,
                        reportedAt = now.plusMinutes(10),
                        )
        )

        val found = raidReportRepository.findRaidReport(
                raidId = raid.id,
                userId = 50_000_002L,
                difficulty = "hard",
                )

        assertEquals(1L, updatedCount)
        assertNotNull(found)
        assertEquals("after-name", found?.username)
        assertEquals(1, found?.is3t)
    }

    @Test
    fun `指定レイドの3凸報告一覧を取得できる`() {
        val guildId = 40_000_003L
        val now = OffsetDateTime.now()

        val raid = createRaid(guildId, now)

        raidReportRepository.saveRaidReport(
                RaidReport(
                        raidId = raid.id,
                        userId = 50_000_003L,
                        username = "test-user-3",
                        difficulty = "normal",
                        is3t = 1,
                        reportedAt = now,
                        )
        )

        raidReportRepository.saveRaidReport(
                RaidReport(
                        raidId = raid.id,
                        userId = 50_000_004L,
                        username = "test-user-4",
                        difficulty = "hard",
                        is3t = 1,
                        reportedAt = now,
                        )
        )

        val reports = raidReportRepository.findRaidReportsByRaidId(raid.id)

        assertTrue(reports.any { it.userId == 50_000_003L })
        assertTrue(reports.any { it.userId == 50_000_004L })
        assertTrue(reports.all { it.is3t == 1 })
    }

    private fun createRaid(guildId: Long, now: OffsetDateTime): UnionRaid {
        guildRepository.insertGuild(
                Guild(
                        guildId = guildId,
                        createdAt = now,
                        )
        )

        return unionRaidRepository.insertUnionRaid(
                UnionRaid(
                        guildId = guildId,
                        raidName = "test-raid-report-$guildId",
                        startTime = now.minusHours(1),
                        endTime = now.plusHours(1),
                        notifyTime = now.plusMinutes(30),
                        channelId = 60_000_000L + guildId,
                        createdAt = now,
                        )
        )
    }
}
