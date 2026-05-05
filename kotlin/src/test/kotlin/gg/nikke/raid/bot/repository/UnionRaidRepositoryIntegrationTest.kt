package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.UnionRaid
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class UnionRaidRepositoryIntegrationTest(
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
) {

    @Test
    fun `進行中レイドを作成して取得できる`() {
        val guildId = 20_000_001L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val created = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-active-raid",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                notifyTime = now.plusMinutes(30),
                channelId = 30_000_001L,
                createdAt = now,
            )
        )

        val found = unionRaidRepository.findActiveUnionRaidByGuildId(
            guildId = guildId,
            now = now,
        )

        assertTrue(created.id > 0)
        assertNotNull(found)
        assertEquals(created.id, found?.id)
        assertEquals("test-active-raid", found?.raidName)
    }

    @Test
    fun `終了済みレイドは進行中レイドとして取得されない`() {
        val guildId = 20_000_002L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-ended-raid",
                startTime = now.minusHours(2),
                endTime = now.minusHours(1),
                notifyTime = now.minusMinutes(30),
                channelId = 30_000_002L,
                createdAt = now,
            )
        )

        val found = unionRaidRepository.findActiveUnionRaidByGuildId(
            guildId = guildId,
            now = now,
        )

        assertNull(found)
    }

    @Test
    fun `通知対象の進行中レイド一覧を取得できる`() {
        val guildId = 20_000_003L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val created = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-notifiable-raid",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                notifyTime = now.plusMinutes(10),
                channelId = 30_000_003L,
                createdAt = now,
            )
        )

        val raids = unionRaidRepository.findNotifiableActiveUnionRaids(now)

        assertTrue(raids.any { it.id == created.id })
    }

    @Test
    fun `notifyTimeをクリアできる`() {
        val guildId = 20_000_004L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val created = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-clear-notify-time",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                notifyTime = now.plusMinutes(10),
                channelId = 30_000_004L,
                createdAt = now,
            )
        )

        val updatedCount = unionRaidRepository.clearNotifyTime(created.id)

        val notifiableRaids = unionRaidRepository.findNotifiableActiveUnionRaids(now)

        assertEquals(1L, updatedCount)
        assertTrue(notifiableRaids.none { it.id == created.id })
    }

    @Test
    fun `レイドを終了できる`() {
        val guildId = 20_000_005L
        val now = OffsetDateTime.now()
        val finishTime = now.plusMinutes(5)
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val created = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-finish-raid",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                notifyTime = now.plusMinutes(10),
                channelId = 30_000_005L,
                createdAt = now,
            )
        )

        val updatedCount = unionRaidRepository.finishUnionRaid(
            raidId = created.id,
            now = finishTime,
            ranking = 123,
            percentage = BigDecimal("45.67"),
        )

        val activeRaid = unionRaidRepository.findActiveUnionRaidByGuildId(
            guildId = guildId,
            now = finishTime,
        )

        assertEquals(1L, updatedCount)
        assertNull(activeRaid)
    }
}