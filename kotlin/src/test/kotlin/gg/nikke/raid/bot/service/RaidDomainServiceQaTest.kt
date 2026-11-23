package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * QA #92 — DEV の RaidDomainServiceTest が拾っていない観点の補完（実 DB、テスト終了時にロールバック）。
 *
 * Plan: docs/initiatives/91/qa-plan-92.md
 * Divergence: なし（I-01〜I-19 の大半は DEV テストに対応済み。対応表は qa-backend-92.md）。
 */
@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class RaidDomainServiceQaTest(
    private val raidDomainService: RaidDomainService,
    private val guildRepository: GuildRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val raidReportRepository: RaidReportRepository,
) {

    @Test
    fun `I-02 手動登録済みの参加者はjoinedAtも上書きされず報告は保存される`() {
        val raid = createActiveRaid(70_092_501L)
        val manualJoinedAt = OffsetDateTime.now().minusDays(1)
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(raidId = raid.id, userId = 92_500_001L, username = "manual", score = 777, joinedAt = manualJoinedAt)
        )

        val result = raidDomainService.reportThreeTurn(raid.id, 92_500_001L, "reporter", Difficulty.NORMAL).getOrThrow()

        assertFalse(result.participantRegistered)
        val p = raidParticipantRepository.findParticipantsByRaidId(raid.id).single()
        assertEquals("manual", p.username)
        assertEquals(777, p.score)
        assertEquals(
            manualJoinedAt.toInstant().truncatedTo(ChronoUnit.MILLIS),
            p.joinedAt?.toInstant()?.truncatedTo(ChronoUnit.MILLIS),
        )
        assertNotNull(raidReportRepository.findRaidReport(raid.id, 92_500_001L, Difficulty.NORMAL.value))
    }

    @Test
    fun `I-03 同じ難易度の再報告は報告1行のまま時刻が更新される`() {
        val raid = createActiveRaid(70_092_502L)
        val t1 = OffsetDateTime.now().minusHours(1)
        val t2 = OffsetDateTime.now()
        raidDomainService.reportThreeTurn(raid.id, 92_500_002L, "user", Difficulty.HARD, t1).getOrThrow()
        val second = raidDomainService.reportThreeTurn(raid.id, 92_500_002L, "user", Difficulty.HARD, t2).getOrThrow()

        assertTrue(second.alreadyReported)
        val reports = raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 92_500_002L)
        assertEquals(1, reports.size)
        assertEquals(
            t2.toInstant().truncatedTo(ChronoUnit.MILLIS),
            reports.single().reportedAt?.toInstant()?.truncatedTo(ChronoUnit.MILLIS),
        )
    }

    @Test
    fun `I-04 別の難易度を続けて報告すると報告2行で参加者は1行のまま`() {
        val raid = createActiveRaid(70_092_503L)
        raidDomainService.reportThreeTurn(raid.id, 92_500_003L, "user", Difficulty.NORMAL).getOrThrow()
        raidDomainService.reportThreeTurn(raid.id, 92_500_003L, "user", Difficulty.HARD).getOrThrow()

        assertEquals(2, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 92_500_003L).size)
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
    }

    @Test
    fun `I-06 存在しないレイドへの報告は何も作られない`() {
        val result = raidDomainService.reportThreeTurn(Int.MAX_VALUE, 92_500_004L, "user", Difficulty.NORMAL)

        assertInstanceOf(RaidDomainError.RaidNotFound::class.java, result.exceptionOrNull())
        assertNull(raidReportRepository.findRaidReport(Int.MAX_VALUE, 92_500_004L, Difficulty.NORMAL.value))
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(Int.MAX_VALUE).isEmpty())
    }

    @Test
    fun `I-12 終了済みレイドでの報告取り消しは失敗し参加者と報告が残る`() {
        val raid = createActiveRaid(70_092_505L)
        raidDomainService.reportThreeTurn(raid.id, 92_500_005L, "user", Difficulty.NORMAL).getOrThrow()
        raidDomainService.finishRaid(raidId = raid.id)

        val result = raidDomainService.cancelThreeTurnReports(raid.id, 92_500_005L, setOf(Difficulty.NORMAL))

        assertInstanceOf(RaidDomainError.RaidAlreadyFinished::class.java, result.exceptionOrNull())
        assertEquals(1, raidParticipantRepository.findParticipantsByRaidId(raid.id).size)
        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 92_500_005L).size)
    }

    @Test
    fun `I-15 参加取り消しは同じユーザーの他レイドの参加者と報告に影響しない`() {
        val raidA = createActiveRaid(70_092_506L)
        val raidB = createActiveRaid(70_092_507L)
        val userId = 92_500_006L
        raidDomainService.reportThreeTurn(raidA.id, userId, "user", Difficulty.NORMAL).getOrThrow()
        raidDomainService.reportThreeTurn(raidB.id, userId, "user", Difficulty.NORMAL).getOrThrow()
        raidDomainService.reportThreeTurn(raidB.id, userId, "user", Difficulty.HARD).getOrThrow()

        raidDomainService.cancelParticipation(raidA.id, userId).getOrThrow()

        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raidA.id).isEmpty())
        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raidB.id).map { it.userId })
        assertEquals(2, raidReportRepository.findRaidReportsByRaidIdAndUserId(raidB.id, userId).size)
    }

    @Test
    fun `I-15 参加取り消しは同じレイドの他ユーザーの報告も残す`() {
        val raid = createActiveRaid(70_092_508L)
        raidDomainService.reportThreeTurn(raid.id, 92_500_007L, "me", Difficulty.NORMAL).getOrThrow()
        raidDomainService.reportThreeTurn(raid.id, 92_500_008L, "other", Difficulty.HARD).getOrThrow()

        raidDomainService.cancelParticipation(raid.id, 92_500_007L).getOrThrow()

        assertEquals(1, raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, 92_500_008L).size)
        assertEquals(listOf(92_500_008L), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
    }

    private fun createActiveRaid(guildId: Long): gg.nikke.raid.bot.entity.UnionRaid {
        val now = OffsetDateTime.now()
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        return raidDomainService.createRaid(
            guildId = guildId,
            raidName = "qa-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_500_000L + guildId % 1000,
            now = now,
        ).getOrThrow()
    }
}
