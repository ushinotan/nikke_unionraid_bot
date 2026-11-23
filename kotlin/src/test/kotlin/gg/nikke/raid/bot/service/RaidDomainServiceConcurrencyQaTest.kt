package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/**
 * QA #92 I-23: 「報告」と「レイド終了」の同時実行（実コミット）。
 * クラスに `@Transactional` を付けない。後片付けはギルド削除のカスケード。
 *
 * Divergence: finishRaid 自体は `lockRaid` を取らず単一 UPDATE のみ（KDoc 記載）。UPDATE の行ロックで
 * reportThreeTurn の `FOR UPDATE` が待たされるため、plan の期待（終了先コミットなら報告は終了済みエラー）は満たされる。
 * 待機判定は DEV の RaidDomainServiceTransactionTest と同様に pg_blocking_pids（Thread.sleep の経過時間には頼らない）。
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class RaidDomainServiceConcurrencyQaTest(
    private val raidDomainService: RaidDomainService,
    private val guildRepository: GuildRepository,
    private val raidReportRepository: RaidReportRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
) {
    private val createdGuildIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        createdGuildIds.forEach { guildRepository.deleteGuildById(it) }
        createdGuildIds.clear()
    }

    @Test
    fun `I-23 レイド終了がロック中の報告は待たされ終了コミット後にRaidAlreadyFinishedになり何も残らない`() {
        val now = OffsetDateTime.now()
        val guildId = 70_092_951L
        createdGuildIds.add(guildId)
        guildRepository.insertGuild(Guild(guildId = guildId, createdAt = now))
        val raid = raidDomainService.createRaid(
            guildId = guildId, raidName = "qa-i23", startTime = now, endTime = now.plusHours(2),
            channelId = 80_951_000L, now = now,
        ).getOrThrow()
        val userId = 92_950_001L

        val result = runWhileHoldingRaidLock(
            holder = { assertEquals(1L, raidDomainService.finishRaid(raidId = raid.id).getOrThrow()) },
            other = { raidDomainService.reportThreeTurn(raid.id, userId, "user", Difficulty.NORMAL) },
        )

        assertInstanceOf(RaidDomainError.RaidAlreadyFinished::class.java, result.exceptionOrNull())
        assertNull(raidReportRepository.findRaidReport(raid.id, userId, Difficulty.NORMAL.value))
        assertTrue(raidParticipantRepository.findParticipantsByRaidId(raid.id).isEmpty())
    }

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

    private fun currentBackendPid(): Int {
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
                    Thread.sleep(20) // 期限付き待機のポーリング間隔
                }
            }
        }
        fail<Unit>("別スレッドがロック待ちに入らなかった")
    }
}
