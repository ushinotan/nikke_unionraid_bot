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
import java.time.temporal.ChronoUnit

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

        val found = unionRaidRepository.findActiveUnionRaidByGuildId(guildId = guildId)

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
                finishedAt = now.minusHours(1),
                createdAt = now,
            )
        )

        val found = unionRaidRepository.findActiveUnionRaidByGuildId(guildId = guildId)

        assertNull(found)
    }

    @Test
    fun `終了時刻超過でも未終了なら進行中レイドとして取得できる`() {
        val guildId = 20_000_006L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val created = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-past-end-unfinished",
                startTime = now.minusHours(2),
                endTime = now.minusMinutes(1),
                notifyTime = null,
                channelId = 30_000_006L,
                createdAt = now,
            )
        )

        val found = unionRaidRepository.findActiveUnionRaidByGuildId(guildId = guildId)

        assertNotNull(found)
        assertEquals(created.id, found?.id)
    }

    @Test
    fun `未終了レイド一覧を取得できる`() {
        val guildId = 20_000_007L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val unfinished = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-unfinished",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 30_000_007L,
                createdAt = now,
            )
        )
        unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-finished",
                startTime = now.minusHours(3),
                endTime = now.minusHours(2),
                channelId = 30_000_007L,
                finishedAt = now.minusHours(2),
                createdAt = now,
            )
        )

        val raids = unionRaidRepository.findUnfinishedUnionRaids()

        assertTrue(raids.any { it.id == unfinished.id })
        assertTrue(raids.none { it.raidName == "test-finished" })
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
    fun `終了時刻超過の未終了レイドは通知対象に含まれない`() {
        val guildId = 20_000_008L
        val now = OffsetDateTime.now()

        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val overdue = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-overdue-notifiable",
                startTime = now.minusHours(2),
                endTime = now.minusMinutes(1),
                notifyTime = now.minusHours(1),
                channelId = 30_000_008L,
                createdAt = now,
            )
        )

        val raids = unionRaidRepository.findNotifiableActiveUnionRaids(now)

        assertTrue(raids.none { it.id == overdue.id })
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
        // PostgreSQL timestamptz はマイクロ秒精度のため、比較用時刻も揃える
        val finishTime = now.plusMinutes(5).truncatedTo(ChronoUnit.MICROS)
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

        val activeRaid = unionRaidRepository.findActiveUnionRaidByGuildId(guildId = guildId)
        val finished = unionRaidRepository.findUnionRaidById(created.id)

        assertEquals(1L, updatedCount)
        assertNull(activeRaid)
        assertNotNull(finished?.finishedAt)
        // JDBC 復帰時の offset 差を避けるため instant で比較する
        assertEquals(
            finishTime.toInstant(),
            finished?.finishedAt?.toInstant()?.truncatedTo(ChronoUnit.MICROS),
        )

        val secondFinish = unionRaidRepository.finishUnionRaid(
            raidId = created.id,
            now = finishTime.plusMinutes(1),
            ranking = 1,
            percentage = null,
        )
        assertEquals(0L, secondFinish)
    }
}
