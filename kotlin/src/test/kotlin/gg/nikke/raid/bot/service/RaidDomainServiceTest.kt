package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.repository.GuildRepository
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
open class RaidDomainServiceTest(
    private val raidDomainService: RaidDomainService,
    private val guildRepository: GuildRepository,
) {

    // ----------------------------------------------------------------
    // createRaid
    // ----------------------------------------------------------------

    @Test
    fun `レイド作成_期間ちょうど1時間で成功する`() {
        val guildId = 70_000_001L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val result = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(1),
            channelId = 80_000_001L,
            now = now,
        )

        assertTrue(result.isSuccess)
        assertEquals("test-raid", result.getOrNull()?.raidName)
    }

    @Test
    fun `レイド作成_期間が59分のときDurationTooShortエラーになる`() {
        val guildId = 70_000_002L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        val result = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusMinutes(59),
            channelId = 80_000_002L,
            now = now,
        )

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.DurationTooShort::class.java, result.exceptionOrNull())
    }

    @Test
    fun `レイド作成_進行中レイドが存在するときActiveRaidAlreadyExistsエラーになる`() {
        val guildId = 70_000_003L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))

        raidDomainService.createRaid(
            guildId = guildId,
            raidName = "first-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_003L,
            now = now,
        )

        val result = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "second-raid",
            startTime = now.plusHours(1),
            endTime = now.plusHours(3),
            channelId = 80_000_003L,
            now = now,
        )

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.ActiveRaidAlreadyExists::class.java, result.exceptionOrNull())
    }

    // ----------------------------------------------------------------
    // finishRaid
    // ----------------------------------------------------------------

    @Test
    fun `レイド終了_順位のみで成功する`() {
        val guildId = 70_000_004L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_004L,
            now = now,
        ).getOrThrow()

        val result = raidDomainService.finishRaid(raidId = raid.id, ranking = 100)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `レイド終了_パーセンテージのみで成功する`() {
        val guildId = 70_000_005L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_005L,
            now = now,
        ).getOrThrow()

        val result = raidDomainService.finishRaid(raidId = raid.id, percentage = BigDecimal("50.00"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `レイド終了_パーセンテージ100で成功する`() {
        val guildId = 70_000_006L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_006L,
            now = now,
        ).getOrThrow()

        val result = raidDomainService.finishRaid(raidId = raid.id, percentage = BigDecimal("100"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `レイド終了_順位とパーセンテージの同時指定でRankingAndPercentageBothSpecifiedエラーになる`() {
        val result = raidDomainService.finishRaid(
            raidId = 1,
            ranking = 100,
            percentage = BigDecimal("50.00"),
        )

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RankingAndPercentageBothSpecified::class.java, result.exceptionOrNull())
    }

    @Test
    fun `レイド終了_パーセンテージ0でPercentageOutOfRangeエラーになる`() {
        val result = raidDomainService.finishRaid(raidId = 1, percentage = BigDecimal("0"))

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.PercentageOutOfRange::class.java, result.exceptionOrNull())
    }

    @Test
    fun `レイド終了_負のパーセンテージでPercentageOutOfRangeエラーになる`() {
        val result = raidDomainService.finishRaid(raidId = 1, percentage = BigDecimal("-1"))

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.PercentageOutOfRange::class.java, result.exceptionOrNull())
    }

    @Test
    fun `レイド終了_パーセンテージ100超でPercentageOutOfRangeエラーになる`() {
        val result = raidDomainService.finishRaid(raidId = 1, percentage = BigDecimal("100.01"))

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.PercentageOutOfRange::class.java, result.exceptionOrNull())
    }

    // ----------------------------------------------------------------
    // reportThreeTurn
    // ----------------------------------------------------------------

    @Test
    fun `3凸報告_新規登録される`() {
        val guildId = 70_000_007L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_007L,
            now = now,
        ).getOrThrow()

        val result = raidDomainService.reportThreeTurn(
            raidId = raid.id,
            userId = 90_000_001L,
            username = "test-user",
            difficulty = "normal",
            now = now,
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `3凸報告_同一ユーザー同一難易度の報告はupsertされ件数が増えない`() {
        val guildId = 70_000_008L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_008L,
            now = now,
        ).getOrThrow()

        raidDomainService.reportThreeTurn(raid.id, 90_000_002L, "old-name", "hard", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_002L, "new-name", "hard", now.plusMinutes(10))

        val aggregation = raidDomainService.aggregateRaidResults(raid.id)
        assertEquals(0, aggregation.normalCount)
        assertEquals(1, aggregation.hardCount)
    }

    // ----------------------------------------------------------------
    // aggregateRaidResults
    // ----------------------------------------------------------------

    @Test
    fun `集計_報告がない場合は0を返す`() {
        val guildId = 70_000_009L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_009L,
            now = now,
        ).getOrThrow()

        val result = raidDomainService.aggregateRaidResults(raid.id)

        assertEquals(0, result.normalCount)
        assertEquals(0, result.hardCount)
        assertEquals(BigDecimal.ZERO, result.normalPercentage)
        assertEquals(BigDecimal.ZERO, result.hardPercentage)
    }

    @Test
    fun `集計_難易度別の件数が正しく集計される`() {
        val guildId = 70_000_010L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_010L,
            now = now,
        ).getOrThrow()

        raidDomainService.reportThreeTurn(raid.id, 90_000_011L, "user1", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_012L, "user2", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_013L, "user3", "hard", now)

        val result = raidDomainService.aggregateRaidResults(raid.id)

        assertEquals(2, result.normalCount)
        assertEquals(1, result.hardCount)
    }

    @Test
    fun `集計_パーセンテージが小数第2位で丸められる`() {
        val guildId = 70_000_011L
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_011L,
            now = now,
        ).getOrThrow()

        // normal 5件, hard 2件 → normal: 5/7≒71.43%, hard: 2/7≒28.57%
        raidDomainService.reportThreeTurn(raid.id, 90_000_021L, "user1", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_022L, "user2", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_023L, "user3", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_024L, "user4", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_025L, "user5", "normal", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_026L, "user6", "hard", now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_027L, "user7", "hard", now)

        val result = raidDomainService.aggregateRaidResults(raid.id)

        assertEquals(BigDecimal("71.43"), result.normalPercentage)
        assertEquals(BigDecimal("28.57"), result.hardPercentage)
    }
}
