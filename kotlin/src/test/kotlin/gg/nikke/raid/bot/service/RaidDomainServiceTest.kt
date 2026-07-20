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
        assertEquals(1L, result.getOrThrow())

        val second = raidDomainService.finishRaid(raidId = raid.id, ranking = 50)
        assertTrue(second.isSuccess)
        assertEquals(0L, second.getOrThrow())
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
    fun `レイド終了_順位0でRankingOutOfRangeエラーになる`() {
        val result = raidDomainService.finishRaid(raidId = 1, ranking = 0)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RankingOutOfRange::class.java, result.exceptionOrNull())
    }

    @Test
    fun `レイド終了_負の順位でRankingOutOfRangeエラーになる`() {
        val result = raidDomainService.finishRaid(raidId = 1, ranking = -1)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RankingOutOfRange::class.java, result.exceptionOrNull())
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
            difficulty = Difficulty.NORMAL,
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

        raidDomainService.reportThreeTurn(raid.id, 90_000_002L, "old-name", Difficulty.HARD, now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_002L, "new-name", Difficulty.HARD, now.plusMinutes(10))

        val aggregation = raidDomainService.aggregateRaidResults(raid.id)
        assertEquals(0, aggregation.normalUsers.size)
        assertEquals(1, aggregation.hardUsers.size)
    }

    // ----------------------------------------------------------------
    // aggregateRaidResults
    // ----------------------------------------------------------------

    @Test
    fun `集計_報告がない場合は空リストを返す`() {
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

        assertTrue(result.normalUsers.isEmpty())
        assertTrue(result.hardUsers.isEmpty())
    }

    @Test
    fun `集計_難易度別のユーザー名リストが正しく集計される`() {
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

        raidDomainService.reportThreeTurn(raid.id, 90_000_011L, "user-normal-1", Difficulty.NORMAL, now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_012L, "user-normal-2", Difficulty.NORMAL, now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_013L, "user-hard-1", Difficulty.HARD, now)

        val result = raidDomainService.aggregateRaidResults(raid.id)

        assertEquals(2, result.normalUsers.size)
        assertEquals(1, result.hardUsers.size)
        assertTrue(result.normalUsers.containsAll(listOf("user-normal-1", "user-normal-2")))
        assertEquals("user-hard-1", result.hardUsers[0])
    }

    @Test
    fun `集計_upsert後のユーザー名は最新名で返る`() {
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

        raidDomainService.reportThreeTurn(raid.id, 90_000_021L, "old-name", Difficulty.NORMAL, now)
        raidDomainService.reportThreeTurn(raid.id, 90_000_021L, "new-name", Difficulty.NORMAL, now.plusMinutes(10))

        val result = raidDomainService.aggregateRaidResults(raid.id)

        assertEquals(1, result.normalUsers.size)
        assertEquals("new-name", result.normalUsers[0])
    }
}
