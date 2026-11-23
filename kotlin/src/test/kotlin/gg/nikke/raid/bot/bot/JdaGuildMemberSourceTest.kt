package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.service.GuildMemberSnapshot
import gg.nikke.raid.bot.service.RaidDomainError
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.utils.cache.MemberCacheView
import net.dv8tion.jda.api.utils.concurrent.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.concurrent.thread

class JdaGuildMemberSourceTest {
    private val source = JdaGuildMemberSource(5_000)
    private val jda = mock(JDA::class.java)
    private val guild = mock(Guild::class.java)

    @BeforeEach
    fun setup() {
        `when`(jda.getGuildById(GUILD_ID)).thenReturn(guild)
    }

    private fun member(
        id: Long,
        name: String = "user$id",
        globalName: String? = null,
        nickname: String? = null,
        bot: Boolean = false,
        system: Boolean = false,
    ): Member {
        val user = mock(User::class.java)
        `when`(user.idLong).thenReturn(id)
        `when`(user.name).thenReturn(name)
        `when`(user.globalName).thenReturn(globalName)
        `when`(user.isBot).thenReturn(bot)
        `when`(user.isSystem).thenReturn(system)
        val member = mock(Member::class.java)
        `when`(member.user).thenReturn(user)
        `when`(member.nickname).thenReturn(nickname)
        `when`(member.effectiveAvatarUrl).thenReturn("https://cdn.example.com/$id.png")
        return member
    }

    private fun loadedGuild(members: List<Member>) {
        val cache = mock(MemberCacheView::class.java)
        `when`(cache.asList()).thenReturn(members)
        `when`(guild.isLoaded).thenReturn(true)
        `when`(guild.memberCache).thenReturn(cache)
    }

    private fun unloadedGuild(task: Task<List<Member>>) {
        `when`(guild.isLoaded).thenReturn(false)
        `when`(guild.loadMembers()).thenReturn(task)
    }

    /** onSuccess / onError に渡されたコールバックを捕捉する Task モック */
    private class CapturingTask {
        @Suppress("UNCHECKED_CAST")
        val task: Task<List<Member>> = mock(Task::class.java) as Task<List<Member>>

        @Volatile
        var success: Consumer<in List<Member>>? = null

        @Volatile
        var error: Consumer<in Throwable>? = null

        init {
            `when`(task.onSuccess(any())).thenAnswer {
                success = it.getArgument(0)
                task
            }
            `when`(task.onError(any())).thenAnswer {
                error = it.getArgument(0)
                task
            }
        }
    }

    /** 開始と同時に成功コールバックを呼ぶ Task */
    @Suppress("UNCHECKED_CAST")
    private fun taskReturning(members: List<Member>): Task<List<Member>> {
        val task = mock(Task::class.java) as Task<List<Member>>
        `when`(task.onSuccess(any())).thenAnswer {
            it.getArgument<Consumer<List<Member>>>(0).accept(members)
            task
        }
        return task
    }

    /** 開始と同時に失敗コールバックを呼ぶ Task */
    @Suppress("UNCHECKED_CAST")
    private fun taskFailing(error: Throwable): Task<List<Member>> {
        val task = mock(Task::class.java) as Task<List<Member>>
        `when`(task.onSuccess(any())).thenReturn(task)
        `when`(task.onError(any())).thenAnswer {
            it.getArgument<Consumer<Throwable>>(0).accept(error)
            task
        }
        return task
    }

    private fun assertUnavailable(result: Result<*>) {
        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, result.exceptionOrNull())
    }

    private fun awaitState(t: Thread, state: Thread.State) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (t.state != state) {
            check(System.nanoTime() < deadline) { "thread did not reach $state: ${t.state}" }
            Thread.yield()
        }
    }

    // ---- findMembers ----

    @Test
    fun `ギルドメンバー取得_attach前は取得不能で失敗する`() {
        val result = source.findMembers(GUILD_ID)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, result.exceptionOrNull())
    }

    @Test
    fun `ギルドメンバー取得_ギルドがなければnullを返す`() {
        source.attach(jda)

        val result = source.findMembers(GUILD_ID + 1)

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `ギルドメンバー取得_未ロードならloadMembersを呼ぶ`() {
        source.attach(jda)
        unloadedGuild(taskReturning(listOf(member(1))))

        val result = source.findMembers(GUILD_ID)

        assertEquals(listOf(1L), result.getOrThrow()?.map { it.userId })
        verify(guild).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_ロード済みならloadMembersを呼ばずキャッシュを使う`() {
        source.attach(jda)
        loadedGuild(listOf(member(1)))

        source.findMembers(GUILD_ID)
        val result = source.findMembers(GUILD_ID)

        assertEquals(listOf(1L), result.getOrThrow()?.map { it.userId })
        verify(guild, never()).loadMembers()
        verify(guild, times(2)).memberCache
    }

    @Test
    fun `ギルドメンバー取得_Snapshotに正しく変換される`() {
        source.attach(jda)
        loadedGuild(
            listOf(
                member(1, name = "u1", globalName = "g1", nickname = "n1"),
                member(2, bot = true),
                member(3, system = true),
            ),
        )

        val snapshots = source.findMembers(GUILD_ID).getOrThrow().orEmpty()

        val first = snapshots[0]
        assertEquals("u1", first.username)
        assertEquals("g1", first.globalName)
        assertEquals("n1", first.nickname)
        assertEquals("https://cdn.example.com/1.png", first.avatarUrl)
        assertFalse(first.isBot)
        assertTrue(snapshots[1].isBot)
        assertTrue(snapshots[2].isBot)
    }

    @Test
    fun `ギルドメンバー取得_isLoadedがfalseからtrueに変わったらキャッシュを使う`() {
        source.attach(jda)
        val members = listOf(member(1))
        val cache = mock(MemberCacheView::class.java)
        `when`(cache.asList()).thenReturn(members)
        `when`(guild.memberCache).thenReturn(cache)
        `when`(guild.isLoaded).thenReturn(false, false, true)
        val task = taskReturning(members)
        `when`(guild.loadMembers()).thenReturn(task)

        val first = source.findMembers(GUILD_ID)
        val second = source.findMembers(GUILD_ID)

        assertEquals(listOf(1L), first.getOrThrow()?.map { it.userId })
        assertEquals(listOf(1L), second.getOrThrow()?.map { it.userId })
        verify(guild, times(1)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_先行完了直後に勝った場合はisLoaded再確認でloadMembersを呼ばない`() {
        source.attach(jda)
        val cache = mock(MemberCacheView::class.java)
        val members = listOf(member(1))
        `when`(cache.asList()).thenReturn(members)
        `when`(guild.memberCache).thenReturn(cache)
        `when`(guild.isLoaded).thenReturn(false, true)

        val result = source.findMembers(GUILD_ID)

        assertEquals(listOf(1L), result.getOrThrow()?.map { it.userId })
        verify(guild, never()).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_完了しないタスクはタイムアウトで取得不能になり次回は再取得する`() {
        val shortSource = JdaGuildMemberSource(100)
        shortSource.attach(jda)
        val neverCompletes = CapturingTask()
        unloadedGuild(neverCompletes.task)

        val first = shortSource.findMembers(GUILD_ID)
        val second = shortSource.findMembers(GUILD_ID)

        assertUnavailable(first)
        assertUnavailable(second)
        verify(guild, times(2)).loadMembers()
        verify(neverCompletes.task, times(2)).cancel()
    }

    @Test
    fun `ギルドメンバー取得_onErrorで失敗したら取得不能で失敗する`() {
        source.attach(jda)
        unloadedGuild(taskFailing(IllegalStateException("boom")))

        assertUnavailable(source.findMembers(GUILD_ID))
    }

    @Test
    fun `ギルドメンバー取得_リーダー失敗時は待機中のフォロワーも即座に取得不能になる`() {
        val slowSource = JdaGuildMemberSource(30_000)
        slowSource.attach(jda)
        val captured = CapturingTask()
        unloadedGuild(captured.task)

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val results = Collections.synchronizedList(mutableListOf<Result<List<GuildMemberSnapshot>?>>())
        val body = { try { results.add(slowSource.findMembers(GUILD_ID)) } catch (t: Throwable) { errors.add(t) } }
        val leader = thread { body() }
        awaitState(leader, Thread.State.TIMED_WAITING)
        val follower = thread { body() }
        awaitState(follower, Thread.State.TIMED_WAITING)

        val start = System.nanoTime()
        captured.error!!.accept(IllegalStateException("boom"))
        leader.join(5_000)
        follower.join(5_000)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertTrue(errors.isEmpty(), errors.toString())
        assertEquals(2, results.size)
        results.forEach { assertUnavailable(it) }
        assertTrue(elapsedMs < 5_000, "elapsed=$elapsedMs")
        verify(guild, times(1)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_同時リクエストでもloadMembersは1回だけ呼ばれる`() {
        source.attach(jda)
        val captured = CapturingTask()
        unloadedGuild(captured.task)
        val members = listOf(member(1))

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val results = Collections.synchronizedList(mutableListOf<Result<List<GuildMemberSnapshot>?>>())
        val body = { try { results.add(source.findMembers(GUILD_ID)) } catch (t: Throwable) { errors.add(t) } }
        val leader = thread { body() }
        awaitState(leader, Thread.State.TIMED_WAITING)
        val follower = thread { body() }
        awaitState(follower, Thread.State.TIMED_WAITING)

        captured.success!!.accept(members)
        leader.join(5_000)
        follower.join(5_000)

        assertTrue(errors.isEmpty(), errors.toString())
        assertEquals(2, results.size)
        results.forEach { assertEquals(listOf(1L), it.getOrThrow()?.map { s -> s.userId }) }
        verify(guild, times(1)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_フォロワーが中断されたら取得不能で割り込みフラグが残る`() {
        source.attach(jda)
        val captured = CapturingTask()
        unloadedGuild(captured.task)

        val leader = thread { source.findMembers(GUILD_ID) }
        awaitState(leader, Thread.State.TIMED_WAITING)

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val result = CompletableFuture<Result<List<GuildMemberSnapshot>?>>()
        val interruptedFlag = CompletableFuture<Boolean>()
        val follower = thread {
            try {
                result.complete(source.findMembers(GUILD_ID))
                interruptedFlag.complete(Thread.currentThread().isInterrupted)
            } catch (t: Throwable) {
                errors.add(t)
            }
        }
        awaitState(follower, Thread.State.TIMED_WAITING)
        follower.interrupt()

        assertUnavailable(result.get(5, TimeUnit.SECONDS))
        assertTrue(interruptedFlag.get(5, TimeUnit.SECONDS))
        assertTrue(errors.isEmpty(), errors.toString())

        captured.success!!.accept(listOf(member(1)))
        leader.join(5_000)
        verify(guild, times(1)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_開始時にErrorが投げられたらフォロワーは即座に完了し次回は再取得する`() {
        val slowSource = JdaGuildMemberSource(30_000)
        slowSource.attach(jda)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val members = listOf(member(1))
        val task = taskReturning(members)
        `when`(guild.isLoaded).thenReturn(false)
        `when`(guild.loadMembers()).thenAnswer {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw AssertionError("start failed")
        }.thenReturn(task)

        val leaderThrown = CompletableFuture<Throwable>()
        val leader = thread {
            try {
                slowSource.findMembers(GUILD_ID)
            } catch (t: Throwable) {
                leaderThrown.complete(t)
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val followerResult = CompletableFuture<Result<List<GuildMemberSnapshot>?>>()
        val follower = thread {
            try {
                followerResult.complete(slowSource.findMembers(GUILD_ID))
            } catch (t: Throwable) {
                errors.add(t)
            }
        }
        awaitState(follower, Thread.State.TIMED_WAITING)
        release.countDown()

        assertUnavailable(followerResult.get(5, TimeUnit.SECONDS))
        assertInstanceOf(AssertionError::class.java, leaderThrown.get(5, TimeUnit.SECONDS))
        leader.join(5_000)
        assertTrue(errors.isEmpty(), errors.toString())

        val next = slowSource.findMembers(GUILD_ID)
        assertEquals(listOf(1L), next.getOrThrow()?.map { it.userId })
        verify(guild, times(2)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_完了後は取得中エントリが除去され再取得できる`() {
        source.attach(jda)
        unloadedGuild(taskReturning(listOf(member(1))))

        source.findMembers(GUILD_ID)
        source.findMembers(GUILD_ID)

        verify(guild, times(2)).loadMembers()
    }

    @Test
    fun `ギルドメンバー取得_detach後は取得不能で失敗する`() {
        source.attach(jda)
        source.detach()

        val result = source.findMembers(GUILD_ID)

        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, result.exceptionOrNull())
    }

    @Test
    fun `ギルドメンバー取得_JDAがシャットダウン中なら取得不能で失敗する`() {
        source.attach(jda)
        loadedGuild(listOf(member(1)))
        `when`(jda.status).thenReturn(JDA.Status.SHUTTING_DOWN)

        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, source.findMembers(GUILD_ID).exceptionOrNull())

        `when`(jda.status).thenReturn(JDA.Status.SHUTDOWN)

        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, source.findMembers(GUILD_ID).exceptionOrNull())
    }

    companion object {
        private const val GUILD_ID = 70_094_100L
    }
}
