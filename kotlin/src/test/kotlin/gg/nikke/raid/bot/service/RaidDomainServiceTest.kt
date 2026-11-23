package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
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
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
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

    @Test
    fun `3凸報告_存在しないレイドはRaidNotFoundエラーになる`() {
        val result = raidDomainService.reportThreeTurn(
            raidId = Int.MAX_VALUE,
            userId = 90_000_115L,
            username = "user",
            difficulty = Difficulty.NORMAL,
        )

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
    }

    @Test
    fun `3凸報告_終了済みレイドはRaidAlreadyFinishedエラーになり報告は保存されない`() {
        val raid = createActiveRaid(70_000_110L)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.reportThreeTurn(raid.id, 90_000_116L, "user", Difficulty.NORMAL)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RaidAlreadyFinished::class.java, result.exceptionOrNull())
        assertFalse(raidDomainService.hasReported(raid.id, 90_000_116L, Difficulty.NORMAL))
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    // ----------------------------------------------------------------
    // ensureRaidActive
    // ----------------------------------------------------------------

    @Test
    fun `進行中確認_進行中レイドは成功し終了済みと未存在はエラーになる`() {
        val raid = createActiveRaid(70_000_111L)

        assertEquals(raid.id, raidDomainService.ensureRaidActive(raid.id).getOrThrow().id)
        assertInstanceOf(
            RaidDomainError.RaidNotFound::class.java,
            raidDomainService.ensureRaidActive(Int.MAX_VALUE).exceptionOrNull(),
        )

        raidDomainService.finishRaid(raidId = raid.id)
        assertInstanceOf(
            RaidDomainError.RaidAlreadyFinished::class.java,
            raidDomainService.ensureRaidActive(raid.id).exceptionOrNull(),
        )
    }

    // ----------------------------------------------------------------
    // hasReported / findReportedDifficulties
    // ----------------------------------------------------------------

    @Test
    fun `報告済み判定_報告した難易度だけtrueになる`() {
        val raid = createActiveRaid(70_000_101L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_101L, "user", Difficulty.NORMAL)

        assertTrue(raidDomainService.hasReported(raid.id, 90_000_101L, Difficulty.NORMAL))
        assertFalse(raidDomainService.hasReported(raid.id, 90_000_101L, Difficulty.HARD))
        assertFalse(raidDomainService.hasReported(raid.id, 90_000_102L, Difficulty.NORMAL))
    }

    @Test
    fun `報告済み難易度の取得_ノーマル ハードの順で返り他ユーザーは含まれない`() {
        val raid = createActiveRaid(70_000_102L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_103L, "user", Difficulty.HARD)
        raidDomainService.reportThreeTurn(raid.id, 90_000_103L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 90_000_104L, "other", Difficulty.NORMAL)

        assertEquals(
            listOf(Difficulty.NORMAL, Difficulty.HARD),
            raidDomainService.findReportedDifficulties(raid.id, 90_000_103L),
        )
        assertEquals(
            listOf(Difficulty.NORMAL),
            raidDomainService.findReportedDifficulties(raid.id, 90_000_104L),
        )
        assertTrue(raidDomainService.findReportedDifficulties(raid.id, 90_000_105L).isEmpty())
    }

    // ----------------------------------------------------------------
    // cancelThreeTurnReports
    // ----------------------------------------------------------------

    @Test
    fun `報告取り消し_指定した難易度だけ消えて他の難易度は残る`() {
        val raid = createActiveRaid(70_000_103L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_106L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 90_000_106L, "user", Difficulty.HARD)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 90_000_106L, setOf(Difficulty.NORMAL))

        assertTrue(result.isSuccess)
        assertEquals(setOf(Difficulty.NORMAL), result.getOrThrow().cancelled)
        val aggregation = raidDomainService.aggregateRaidResults(raid.id)
        assertEquals(0, aggregation.normalUsers.size)
        assertEquals(listOf("user"), aggregation.hardUsers)
    }

    @Test
    fun `報告取り消し_両方の難易度をまとめて取り消せる`() {
        val raid = createActiveRaid(70_000_104L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_107L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 90_000_107L, "user", Difficulty.HARD)

        val result = raidDomainService.cancelThreeTurnReports(
            raid.id, 90_000_107L, setOf(Difficulty.NORMAL, Difficulty.HARD),
        )

        assertEquals(setOf(Difficulty.NORMAL, Difficulty.HARD), result.getOrThrow().cancelled)
        assertTrue(raidDomainService.findReportedDifficulties(raid.id, 90_000_107L).isEmpty())
    }

    @Test
    fun `報告取り消し_他ユーザーの報告は消えない`() {
        val raid = createActiveRaid(70_000_105L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_108L, "me", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 90_000_109L, "other", Difficulty.NORMAL)

        raidDomainService.cancelThreeTurnReports(raid.id, 90_000_108L, setOf(Difficulty.NORMAL))

        assertEquals(listOf("other"), raidDomainService.aggregateRaidResults(raid.id).normalUsers)
    }

    @Test
    fun `報告取り消し_未報告の難易度だけを指定すると空集合で成功する`() {
        val raid = createActiveRaid(70_000_106L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_110L, "user", Difficulty.NORMAL)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 90_000_110L, setOf(Difficulty.HARD))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().cancelled.isEmpty())
        assertTrue(raidDomainService.hasReported(raid.id, 90_000_110L, Difficulty.NORMAL))
    }

    @Test
    fun `報告取り消し_既に取り消された難易度は結果に含まれず実際に消えた難易度だけ返る`() {
        val raid = createActiveRaid(70_000_109L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_114L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 90_000_114L, "user", Difficulty.HARD)

        // 古いメニューで先にノーマルを取り消した後、別のメニューでノーマル・ハードを選択した想定
        raidDomainService.cancelThreeTurnReports(raid.id, 90_000_114L, setOf(Difficulty.NORMAL))
        val result = raidDomainService.cancelThreeTurnReports(
            raid.id, 90_000_114L, setOf(Difficulty.NORMAL, Difficulty.HARD),
        )

        assertEquals(setOf(Difficulty.HARD), result.getOrThrow().cancelled)
        assertTrue(raidDomainService.findReportedDifficulties(raid.id, 90_000_114L).isEmpty())
    }

    @Test
    fun `報告取り消し_難易度が空だとNoDifficultySelectedエラーになる`() {
        val raid = createActiveRaid(70_000_107L)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 90_000_111L, emptySet())

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.NoDifficultySelected::class.java, result.exceptionOrNull())
    }

    @Test
    fun `報告取り消し_存在しないレイドはRaidNotFoundエラーになる`() {
        val result = raidDomainService.cancelThreeTurnReports(
            raidId = Int.MAX_VALUE,
            userId = 90_000_112L,
            difficulties = setOf(Difficulty.NORMAL),
        )

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
    }

    @Test
    fun `報告取り消し_終了済みレイドはRaidAlreadyFinishedエラーになり報告は残る`() {
        val raid = createActiveRaid(70_000_108L)
        raidDomainService.reportThreeTurn(raid.id, 90_000_113L, "user", Difficulty.NORMAL)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 90_000_113L, setOf(Difficulty.NORMAL))

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.RaidAlreadyFinished::class.java, result.exceptionOrNull())
        assertTrue(raidDomainService.hasReported(raid.id, 90_000_113L, Difficulty.NORMAL))
    }

    // ----------------------------------------------------------------
    // reportThreeTurn（参加者連動）
    // ----------------------------------------------------------------

    @Test
    fun `3凸報告_未参加なら参加者にスコア0と報告者名で登録される`() {
        val raid = createActiveRaid(70_092_101L)
        val now = OffsetDateTime.now()

        val result = raidDomainService.reportThreeTurn(raid.id, 92_100_001L, "reporter", Difficulty.NORMAL, now)

        assertTrue(result.getOrThrow().participantRegistered)
        assertFalse(result.getOrThrow().alreadyReported)
        val participant = raidParticipantRepository.findParticipantsByRaidId(raid.id).single()
        assertEquals(92_100_001L, participant.userId)
        assertEquals("reporter", participant.username)
        assertEquals(0, participant.score)
        assertEquals(now.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), participant.joinedAt?.toInstant()?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
    }

    @Test
    fun `3凸報告_手動登録済みの参加者はユーザー名とスコアが上書きされない`() {
        val raid = createActiveRaid(70_092_102L)
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 92_100_002L, username = "manual", score = 1234, joinedAt = OffsetDateTime.now())
        )

        val result = raidDomainService.reportThreeTurn(raid.id, 92_100_002L, "reporter", Difficulty.NORMAL)

        assertFalse(result.getOrThrow().participantRegistered)
        val participant = raidParticipantRepository.findParticipantsByRaidId(raid.id).single()
        assertEquals("manual", participant.username)
        assertEquals(1234, participant.score)
    }

    @Test
    fun `3凸報告_同じ難易度の再報告はalreadyReportedがtrueになる`() {
        val raid = createActiveRaid(70_092_103L)
        val first = raidDomainService.reportThreeTurn(raid.id, 92_100_003L, "user", Difficulty.HARD).getOrThrow()
        val second = raidDomainService.reportThreeTurn(raid.id, 92_100_003L, "user", Difficulty.HARD).getOrThrow()

        assertFalse(first.alreadyReported)
        assertTrue(second.alreadyReported)
        assertFalse(second.participantRegistered)
    }

    @Test
    fun `3凸報告_2つ目の難易度でも参加者は1行のまま`() {
        val raid = createActiveRaid(70_092_104L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_004L, "user", Difficulty.NORMAL)
        val second = raidDomainService.reportThreeTurn(raid.id, 92_100_004L, "user", Difficulty.HARD).getOrThrow()

        assertFalse(second.participantRegistered)
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    // ----------------------------------------------------------------
    // cancelThreeTurnReports（参加者連動）
    // ----------------------------------------------------------------

    @Test
    fun `報告取り消し_最後の報告を取り消すと参加者も削除される`() {
        val raid = createActiveRaid(70_092_111L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_011L, "user", Difficulty.NORMAL)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_011L, setOf(Difficulty.NORMAL)).getOrThrow()

        assertTrue(result.participantRemoved)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `報告取り消し_報告が残っていれば参加者は残る`() {
        val raid = createActiveRaid(70_092_112L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_012L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 92_100_012L, "user", Difficulty.HARD)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_012L, setOf(Difficulty.NORMAL)).getOrThrow()

        assertFalse(result.participantRemoved)
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    @Test
    fun `報告取り消し_手動登録でスコア入力済みでも報告0件で参加者が削除される`() {
        val raid = createActiveRaid(70_092_113L)
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 92_100_013L, username = "manual", score = 5000, joinedAt = OffsetDateTime.now())
        )
        raidDomainService.reportThreeTurn(raid.id, 92_100_013L, "user", Difficulty.NORMAL)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_013L, setOf(Difficulty.NORMAL)).getOrThrow()

        assertTrue(result.participantRemoved)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `報告取り消し_未報告の難易度だけを指定すると報告なしの手動登録者は削除されない`() {
        val raid = createActiveRaid(70_092_114L)
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 92_100_014L, username = "manual", score = 100, joinedAt = OffsetDateTime.now())
        )

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_014L, setOf(Difficulty.HARD)).getOrThrow()

        assertTrue(result.cancelled.isEmpty())
        assertFalse(result.participantRemoved)
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    @Test
    fun `報告取り消し_参加者の行が無い既存の報告でも取り消せる`() {
        val raid = createActiveRaid(70_092_115L)
        raidReportRepository.saveRaidReport(
            RaidReport(raidId = raid.id, userId = 92_100_015L, username = "legacy", difficulty = Difficulty.NORMAL.value, reportedAt = OffsetDateTime.now())
        )

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_015L, setOf(Difficulty.NORMAL)).getOrThrow()

        assertEquals(setOf(Difficulty.NORMAL), result.cancelled)
        assertFalse(result.participantRemoved)
        assertTrue(raidDomainService.findReportedDifficulties(raid.id, 92_100_015L).isEmpty())
    }

    @Test
    fun `報告取り消し_終了済みレイドは参加者も報告も残る`() {
        val raid = createActiveRaid(70_092_116L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_016L, "user", Difficulty.NORMAL)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_100_016L, setOf(Difficulty.NORMAL))

        assertInstanceOf(RaidDomainError.RaidAlreadyFinished::class.java, result.exceptionOrNull())
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
        assertTrue(raidDomainService.hasReported(raid.id, 92_100_016L, Difficulty.NORMAL))
    }

    // ----------------------------------------------------------------
    // cancelParticipation
    // ----------------------------------------------------------------

    @Test
    fun `参加取り消し_参加者と全難易度の報告がまとめて削除される`() {
        val raid = createActiveRaid(70_092_121L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_021L, "user", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 92_100_021L, "user", Difficulty.HARD)

        val result = raidDomainService.cancelParticipation(raid.id, 92_100_021L).getOrThrow()

        assertTrue(result.participantRemoved)
        assertEquals(2L, result.removedReportCount)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `参加取り消し_他ユーザーの参加者と報告は残る`() {
        val raid = createActiveRaid(70_092_122L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_022L, "me", Difficulty.NORMAL)
        raidDomainService.reportThreeTurn(raid.id, 92_100_023L, "other", Difficulty.NORMAL)

        raidDomainService.cancelParticipation(raid.id, 92_100_022L)

        assertEquals(listOf(92_100_023L), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
        assertEquals(listOf("other"), raidDomainService.aggregateRaidResults(raid.id).normalUsers)
    }

    @Test
    fun `参加取り消し_報告だけで参加者の行が無くても報告は削除される`() {
        val raid = createActiveRaid(70_092_123L)
        raidReportRepository.saveRaidReport(
            RaidReport(raidId = raid.id, userId = 92_100_024L, username = "legacy", difficulty = Difficulty.HARD.value, reportedAt = OffsetDateTime.now())
        )

        val result = raidDomainService.cancelParticipation(raid.id, 92_100_024L).getOrThrow()

        assertFalse(result.participantRemoved)
        assertEquals(1L, result.removedReportCount)
        assertTrue(raidReportRepository.findAllRaidReportsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `参加取り消し_参加者でも報告者でもなければ成功し件数は0`() {
        val raid = createActiveRaid(70_092_124L)

        val result = raidDomainService.cancelParticipation(raid.id, 92_100_025L).getOrThrow()

        assertFalse(result.participantRemoved)
        assertEquals(0L, result.removedReportCount)
    }

    @Test
    fun `参加取り消し_存在しないレイドはRaidNotFoundエラーになる`() {
        val result = raidDomainService.cancelParticipation(Int.MAX_VALUE, 92_100_026L)

        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
    }

    @Test
    fun `参加取り消し_終了済みレイドでも削除できる`() {
        val raid = createActiveRaid(70_092_125L)
        raidDomainService.reportThreeTurn(raid.id, 92_100_027L, "user", Difficulty.NORMAL)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.cancelParticipation(raid.id, 92_100_027L).getOrThrow()

        assertTrue(result.participantRemoved)
        assertEquals(1L, result.removedReportCount)
    }

    // ----------------------------------------------------------------
    // registerParticipant
    // ----------------------------------------------------------------

    @Test
    fun `参加登録_未参加なら指定のスコアとユーザー名で登録される`() {
        val raid = createActiveRaid(70_093_101L)
        val now = OffsetDateTime.now()

        val participant = raidDomainService.registerParticipant(raid.id, 93_100_001L, "ニック", 12345, now).getOrThrow()

        assertEquals("ニック", participant.username)
        assertEquals(12345, participant.score)
        assertEquals(
            now.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            participant.joinedAt?.toInstant()?.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
        )
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    @Test
    fun `参加登録_既に参加者ならParticipantAlreadyExistsエラーになりスコアは変わらない`() {
        val raid = createActiveRaid(70_093_102L)
        raidDomainService.registerParticipant(raid.id, 93_100_002L, "first", 100).getOrThrow()

        val result = raidDomainService.registerParticipant(raid.id, 93_100_002L, "second", 999)

        assertInstanceOf(RaidDomainError.ParticipantAlreadyExists::class.java, result.exceptionOrNull())
        val participant = raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, 93_100_002L)!!
        assertEquals("first", participant.username)
        assertEquals(100, participant.score)
    }

    @Test
    fun `参加登録_3凸報告だけで参加者の行が無いユーザーも登録できる`() {
        val raid = createActiveRaid(70_093_103L)
        val userId = 93_100_003L
        raidReportRepository.saveRaidReport(
            RaidReport(raidId = raid.id, userId = userId, username = "legacy", difficulty = Difficulty.NORMAL.value),
        )

        val participant = raidDomainService.registerParticipant(raid.id, userId, "legacy", 500).getOrThrow()

        assertEquals(500, participant.score)
        assertTrue(raidDomainService.hasReported(raid.id, userId, Difficulty.NORMAL))
    }

    @Test
    fun `参加登録_終了済みレイドでも登録できる`() {
        val raid = createActiveRaid(70_093_104L)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.registerParticipant(raid.id, 93_100_004L, "late", 10)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `参加登録_存在しないレイドはRaidNotFoundエラーになる`() {
        val result = raidDomainService.registerParticipant(Int.MAX_VALUE, 93_100_005L, "user", 10)

        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
    }

    // ----------------------------------------------------------------
    // updateParticipantScore
    // ----------------------------------------------------------------

    @Test
    fun `スコア更新_参加者のスコアだけが変わりユーザー名と参加時刻は変わらない`() {
        val raid = createActiveRaid(70_093_111L)
        val before = raidDomainService.registerParticipant(raid.id, 93_100_011L, "name", 100).getOrThrow()

        val after = raidDomainService.updateParticipantScore(raid.id, 93_100_011L, 2000).getOrThrow()

        assertEquals(2000, after.score)
        assertEquals("name", after.username)
        assertEquals(before.joinedAt?.toInstant(), after.joinedAt?.toInstant())
        assertEquals(before.id, after.id)
    }

    @Test
    fun `スコア更新_参加者でなければParticipantNotFoundエラーになる`() {
        val raid = createActiveRaid(70_093_112L)

        val result = raidDomainService.updateParticipantScore(raid.id, 93_100_012L, 10)

        assertInstanceOf(RaidDomainError.ParticipantNotFound::class.java, result.exceptionOrNull())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `スコア更新_終了済みレイドでも更新できる`() {
        val raid = createActiveRaid(70_093_113L)
        raidDomainService.registerParticipant(raid.id, 93_100_013L, "name", 100).getOrThrow()
        raidDomainService.finishRaid(raidId = raid.id)

        val after = raidDomainService.updateParticipantScore(raid.id, 93_100_013L, 300).getOrThrow()

        assertEquals(300, after.score)
    }

    @Test
    fun `スコア更新_存在しないレイドはRaidNotFoundエラーになる`() {
        val result = raidDomainService.updateParticipantScore(Int.MAX_VALUE, 93_100_014L, 10)

        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
    }

    // ----------------------------------------------------------------
    // reportThreeTurn（allowFinished）
    // ----------------------------------------------------------------

    @Test
    fun `3凸報告_allowFinishedなら終了済みレイドでも報告と自動参加ができる`() {
        val raid = createActiveRaid(70_093_121L)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.reportThreeTurn(
            raidId = raid.id,
            userId = 93_100_021L,
            username = "user",
            difficulty = Difficulty.HARD,
            allowFinished = true,
        ).getOrThrow()

        assertTrue(result.participantRegistered)
        assertTrue(raidDomainService.hasReported(raid.id, 93_100_021L, Difficulty.HARD))
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    // ----------------------------------------------------------------
    // cancelThreeTurnReports（allowFinished）
    // ----------------------------------------------------------------

    @Test
    fun `報告取り消し_allowFinishedなら終了済みレイドでも取り消せて報告0件なら参加者も削除される`() {
        val raid = createActiveRaid(70_093_131L)
        raidDomainService.reportThreeTurn(raid.id, 93_100_031L, "user", Difficulty.NORMAL)
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.cancelThreeTurnReports(
            raidId = raid.id,
            userId = 93_100_031L,
            difficulties = setOf(Difficulty.NORMAL),
            allowFinished = true,
        ).getOrThrow()

        assertEquals(setOf(Difficulty.NORMAL), result.cancelled)
        assertTrue(result.participantRemoved)
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    private fun createActiveRaid(guildId: Long): gg.nikke.raid.bot.entity.UnionRaid {
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        return raidDomainService.createRaid(
            guildId = guildId,
            raidName = "test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_000_000L + guildId % 1000,
            now = now,
        ).getOrThrow()
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
