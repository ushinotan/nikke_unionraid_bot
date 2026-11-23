package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/**
 * トランザクション境界と同時実行のテスト。
 * クラスに `@Transactional` を付けず、実際にコミットさせる。後片付けはギルド削除のカスケードで行う。
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class RaidDomainServiceTransactionTest(
    private val raidDomainService: RaidDomainService,
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidReportRepository: RaidReportRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
) {

    @MockitoSpyBean
    private lateinit var raidParticipantRepository: RaidParticipantRepository

    private val createdGuildIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        createdGuildIds.forEach { guildRepository.deleteGuildById(it) }
        createdGuildIds.clear()
    }

    @Test
    fun `原子性_参加者の登録で例外が起きると報告もロールバックされる`() {
        val raid = createActiveRaid(70_092_901L)
        val userId = 92_900_001L
        // mockito-kotlin は依存に無いので、any() が返す null を Kotlin の非 null 型に合わせるためダミーを返す
        Mockito.doAnswer { throw SQLException("probe") }
            .`when`(raidParticipantRepository)
            .insertParticipantIfAbsent(ArgumentMatchers.any(RaidParticipant::class.java) ?: dummyParticipant())

        val thrown = assertThrows(Throwable::class.java) {
            raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL)
        }

        assertProbe(thrown)
        assertNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
    }

    @Test
    fun `原子性_最後の報告の取り消しで参加者の削除が失敗すると報告の削除もロールバックされる`() {
        val raid = createActiveRaid(70_092_905L)
        val userId = 92_900_007L
        raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL).getOrThrow()
        stubDeleteParticipantToFail()

        val thrown = assertThrows(Throwable::class.java) {
            raidDomainService.cancelThreeTurnReports(raid.id, userId, setOf(Difficulty.NORMAL))
        }

        assertProbe(thrown)
        assertNotNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
    }

    @Test
    fun `原子性_参加の取り消しで参加者の削除が失敗すると報告の削除もロールバックされる`() {
        val raid = createActiveRaid(70_092_906L)
        val userId = 92_900_008L
        raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL).getOrThrow()
        stubDeleteParticipantToFail()

        val thrown = assertThrows(Throwable::class.java) {
            raidDomainService.cancelParticipation(raid.id, userId)
        }

        assertProbe(thrown)
        assertNotNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
    }

    @Test
    fun `外側のトランザクションなし_プロキシ経由の取り消しが成功しコミットされる`() {
        val raid = createActiveRaid(70_092_907L)
        val cancelUser = 92_900_009L
        val withdrawUser = 92_900_010L
        raidDomainService.reportThreeTurn(raid.id, cancelUser, "a", Difficulty.NORMAL).getOrThrow()
        raidDomainService.reportThreeTurn(raid.id, cancelUser, "a", Difficulty.HARD).getOrThrow()
        raidDomainService.reportThreeTurn(raid.id, withdrawUser, "b", Difficulty.NORMAL).getOrThrow()

        val cancel = raidDomainService
            .cancelThreeTurnReports(raid.id, cancelUser, setOf(Difficulty.NORMAL, Difficulty.HARD)).getOrThrow()
        val withdraw = raidDomainService.cancelParticipation(raid.id, withdrawUser).getOrThrow()

        assertTrue(cancel.participantRemoved)
        assertTrue(withdraw.participantRemoved)
        assertEquals(1L, withdraw.removedReportCount)
        // 外側のトランザクションが無いので、ここで読めるのはコミット済みの結果
        assertTrue(raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, cancelUser).isEmpty())
        assertTrue(raidReportRepository.findRaidReportsByRaidIdAndUserId(raid.id, withdrawUser).isEmpty())
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

    @Test
    fun `外側のトランザクションなし_プロキシ経由の参加登録とスコア更新がコミットされる`() {
        val raid = createActiveRaid(70_093_901L)
        val userId = 93_900_001L

        raidDomainService.registerParticipant(raid.id, userId, "user", 100).getOrThrow()
        raidDomainService.updateParticipantScore(raid.id, userId, 777).getOrThrow()

        // 外側のトランザクションが無いので、ここで読めるのはコミット済みの結果
        val participant = raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, userId)
        assertEquals(777, participant?.score)
        assertEquals("user", participant?.username)
    }

    @Test
    fun `直列化_最後の報告の取り消しがロック中なら別難易度の報告は待たされコミット後に両立する`() {
        val raid = createActiveRaid(70_092_902L)
        val userId = 92_900_002L
        raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL).getOrThrow()

        val report = runWhileHoldingRaidLock(
            holder = {
                val cancel = raidDomainService.cancelThreeTurnReports(raid.id, userId, setOf(Difficulty.NORMAL)).getOrThrow()
                assertTrue(cancel.participantRemoved)
            },
            other = { raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.HARD) },
        ).getOrThrow()

        assertTrue(report.participantRegistered)
        assertNotNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.HARD.value))
        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
    }

    @Test
    fun `直列化_レイド行をロック中は別ユーザーの報告も待たされコミット後に完了する`() {
        // 別ユーザーなら参加者の一意制約では衝突しないので、待たされる原因はレイド行のロックだけになる
        val raid = createActiveRaid(70_092_904L)
        val holder = 92_900_005L
        val other = 92_900_006L

        val report = runWhileHoldingRaidLock(
            holder = { raidDomainService.cancelParticipation(raid.id, holder).getOrThrow() },
            other = { raidDomainService.reportThreeTurn(raid.id, other, "other", Difficulty.NORMAL) },
        ).getOrThrow()

        assertTrue(report.participantRegistered)
        assertNotNull(raidReportRepository.findRaidReport(raid.id, other, Difficulty.NORMAL.value))
    }

    @Test
    fun `トランザクション外_プロキシを通さない呼び出しはIllegalStateExceptionで止まり行は増減しない`() {
        val raid = createActiveRaid(70_092_903L)
        val userId = 92_900_003L
        raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL).getOrThrow()
        val direct = RaidDomainService(unionRaidRepository, raidReportRepository, raidParticipantRepository)

        assertThrows(IllegalStateException::class.java) {
            direct.reportThreeTurn(raid.id, 92_900_004L, "other", Difficulty.NORMAL)
        }
        assertThrows(IllegalStateException::class.java) {
            direct.cancelThreeTurnReports(raid.id, userId, setOf(Difficulty.NORMAL))
        }
        assertThrows(IllegalStateException::class.java) {
            direct.cancelParticipation(raid.id, userId)
        }
        assertThrows(IllegalStateException::class.java) {
            direct.registerParticipant(raid.id, 92_900_013L, "other", 10)
        }
        assertThrows(IllegalStateException::class.java) {
            direct.updateParticipantScore(raid.id, userId, 10)
        }

        assertNull(raidReportRepository.findRaidReport(raid.id, 92_900_004L, Difficulty.NORMAL.value))
        assertNotNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
        assertEquals(0, raidParticipantRepository.findParticipantByRaidIdAndUserId(raid.id, userId)?.score)
    }

    @Test
    fun `トランザクション外_ガード付きリポジトリの書き込みを直接呼ぶとIllegalStateExceptionで止まり行は変わらない`() {
        val raid = createActiveRaid(70_092_908L)
        val userId = 92_900_011L
        raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL).getOrThrow()

        assertThrows(IllegalStateException::class.java) {
            raidParticipantRepository.deleteParticipant(raid.id, userId)
        }
        assertThrows(IllegalStateException::class.java) {
            raidReportRepository.saveRaidReport(
                RaidReport(raidId = raid.id, userId = 92_900_012L, username = "other", difficulty = Difficulty.NORMAL.value),
            )
        }

        assertEquals(listOf(userId), raidParticipantRepository.findParticipantsByRaidId(raid.id).map { it.userId })
        assertNotNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
        assertNull(raidReportRepository.findRaidReport(raid.id, 92_900_012L, Difficulty.NORMAL.value))
    }

    private fun stubDeleteParticipantToFail() {
        Mockito.doAnswer { throw SQLException("probe") }
            .`when`(raidParticipantRepository)
            .deleteParticipant(ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong())
    }

    /** 投げられた例外（または cause の連鎖）に `SQLException("probe")` が含まれることを確かめる。 */
    private fun assertProbe(thrown: Throwable) {
        val probe = generateSequence(thrown) { it.cause }
            .firstOrNull { it is SQLException && it.message == "probe" }
        assertNotNull(probe, "SQLException(probe) が cause に無い: $thrown")
    }

    /**
     * [holder] をトランザクション内で実行してレイド行のロックを持たせたまま、別スレッド（別トランザクション）で [other] を始める。
     * [other] が holder のセッションにブロックされていることを `pg_blocking_pids` で確かめてから、holder のトランザクションを
     * コミットし、[other] の結果を返す。時間の経過ではなく待機状態で判定する。
     */
    private fun <T> runWhileHoldingRaidLock(holder: () -> Unit, other: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        val futureRef = AtomicReference<Future<T>>()
        try {
            TransactionTemplate(transactionManager).executeWithoutResult {
                holder()
                val holderPid = currentBackendPid()
                val future = executor.submit<T> { other() }
                futureRef.set(future)
                awaitLockWait(future, holderPid)
                assertFalse(future.isDone, "ロック中なのに別スレッドが完了した")
            }
            return checkNotNull(futureRef.get()).get(10, TimeUnit.SECONDS)
        } finally {
            futureRef.get()?.cancel(true)
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    /** 現在のトランザクションに束縛された接続（holder がロックを持つセッション）の `pg_backend_pid()` を返す。 */
    private fun currentBackendPid(): Int {
        assertTrue(
            TransactionSynchronizationManager.isActualTransactionActive() &&
                TransactionSynchronizationManager.hasResource(dataSource),
            "トランザクションに束縛された接続が無い",
        )
        val conn = DataSourceUtils.getConnection(dataSource)
        try {
            conn.createStatement().use { st ->
                st.executeQuery("SELECT pg_backend_pid()").use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource)
        }
    }

    /** 別セッションが [holderPid] のセッションにブロックされるまで、期限付きで待つ。 */
    private fun awaitLockWait(future: Future<*>, holderPid: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT count(*) FROM pg_stat_activity " +
                    "WHERE pid <> pg_backend_pid() AND ? = ANY(pg_blocking_pids(pid))",
            ).use { stmt ->
                stmt.setInt(1, holderPid)
                while (System.nanoTime() < deadline) {
                    if (future.isDone) fail<Unit>("ロック待ちに入る前に別スレッドが完了した")
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        if (rs.getInt(1) > 0) return
                    }
                    Thread.sleep(20) // 期限付き待機のポーリング間隔であり、同期の根拠ではない
                }
            }
        }
        fail<Unit>("別スレッドがロック待ちに入らなかった")
    }

    private fun dummyParticipant() = RaidParticipant(raidId = 0, userId = 0, username = "")

    private fun createActiveRaid(guildId: Long): gg.nikke.raid.bot.entity.UnionRaid {
        val now = OffsetDateTime.now()
        createdGuildIds.add(guildId)
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        return raidDomainService.createRaid(
            guildId = guildId,
            raidName = "tx-test-raid",
            startTime = now,
            endTime = now.plusHours(2),
            channelId = 80_092_000L + guildId % 1000,
            now = now,
        ).getOrThrow()
    }
}
