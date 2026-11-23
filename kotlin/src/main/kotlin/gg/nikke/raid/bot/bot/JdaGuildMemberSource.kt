package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.service.GuildMemberSnapshot
import gg.nikke.raid.bot.service.GuildMemberSource
import gg.nikke.raid.bot.service.RaidDomainError
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.utils.concurrent.Task
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * JDA のギルドメンバーキャッシュを使う [GuildMemberSource] 実装。
 *
 * bot 無効時（テストなど）でも Bean として存在し、その場合は取得不能として扱う。
 * `guild.isLoaded == false` のときは `loadMembers` で取得する（ロード済みになるまで毎回再取得し得る）。
 *
 * 同じギルドへの同時リクエストは 1 回の `loadMembers` にまとめる（single-flight）。
 * 先頭のリクエスト（リーダー）は `loadMembers` を開始して結果を共有 Future に橋渡しするだけで、
 * リーダーも後続（フォロワー）も同じ共有 Future を [timeoutMillis] まで `get(timeout)` で待つ（割り込み可能）。
 * JDA の `setTimeout` は定期スキャンでしか効かず、シャットダウン時に未完了のまま捨てられ得るため、
 * `Task.get()` では待たない。タイムアウト時は共有 Future を異常完了させ、`Task.cancel()` を試みる。
 * 完了・タイムアウト・開始失敗のいずれでも取得中エントリは除去される。HTTP スレッドで待つこと。
 */
@Component
class JdaGuildMemberSource internal constructor(
    private val timeoutMillis: Long,
) : GuildMemberSource {
    @Autowired
    constructor() : this(DEFAULT_TIMEOUT_MILLIS)

    private val logger = LoggerFactory.getLogger(JdaGuildMemberSource::class.java)

    @Volatile
    private var jda: JDA? = null

    private class Flight {
        val future = CompletableFuture<Result<List<GuildMemberSnapshot>>>()

        @Volatile
        var task: Task<List<Member>>? = null
    }

    /** ギルドごとの取得中リクエスト。完了・タイムアウト時に必ず除去する */
    private val inFlight = ConcurrentHashMap<Long, Flight>()

    /**
     * READY 後の JDA を受け取る
     */
    fun attach(jda: JDA) {
        this.jda = jda
    }

    /**
     * JDA を切り離す。シャットダウン前に呼び、以降は取得不能として扱う
     */
    fun detach() {
        this.jda = null
    }

    override fun findMembers(guildId: Long): Result<List<GuildMemberSnapshot>?> {
        val currentJda = jda ?: return Result.failure(RaidDomainError.GuildMembersUnavailable)
        if (currentJda.status.isShuttingDown()) {
            return Result.failure(RaidDomainError.GuildMembersUnavailable)
        }
        val guild = currentJda.getGuildById(guildId) ?: return Result.success(null)

        if (guild.isLoaded) {
            return Result.success(cachedSnapshots(guild))
        }
        return loadSingleFlight(guildId, guild)
    }

    private fun JDA.Status?.isShuttingDown(): Boolean =
        this == JDA.Status.SHUTTING_DOWN || this == JDA.Status.SHUTDOWN

    private fun cachedSnapshots(guild: Guild): List<GuildMemberSnapshot> =
        guild.memberCache.asList().map { it.toSnapshot() }

    private fun loadSingleFlight(guildId: Long, guild: Guild): Result<List<GuildMemberSnapshot>?> {
        val flight = Flight()
        val existing = inFlight.putIfAbsent(guildId, flight)
        if (existing != null) {
            return await(guildId, existing)
        }
        flight.future.whenComplete { _, _ -> inFlight.remove(guildId, flight) }

        try {
            if (guild.isLoaded) {
                // 先行リクエストの完了直後に勝った場合の再確認
                flight.future.complete(Result.success(cachedSnapshots(guild)))
            } else {
                startLoad(guild, flight)
            }
        } catch (t: Throwable) {
            flight.future.complete(logFailure("開始に失敗しました", guildId, t))
            if (t !is Exception) throw t
        }
        return await(guildId, flight)
    }

    /** `loadMembers` を開始し、結果を共有 Future へ橋渡しする（コールバック内では待たない） */
    private fun startLoad(guild: Guild, flight: Flight) {
        val task = guild.loadMembers()
        flight.task = task
        task.onSuccess { members ->
            try {
                flight.future.complete(Result.success(members.map { it.toSnapshot() }))
            } catch (t: Throwable) {
                flight.future.complete(Result.failure(RaidDomainError.GuildMembersUnavailable))
            }
        }
        task.onError { e ->
            logger.warn("ギルドメンバーの取得が失敗しました: error=${e.javaClass.simpleName}: ${e.message}")
            flight.future.complete(Result.failure(RaidDomainError.GuildMembersUnavailable))
        }
    }

    private fun await(guildId: Long, flight: Flight): Result<List<GuildMemberSnapshot>?> = try {
        flight.future.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logFailure("待機中に中断されました", guildId, e)
    } catch (e: TimeoutException) {
        flight.future.completeExceptionally(e)
        try {
            flight.task?.cancel()
        } catch (ignored: Exception) {
            logger.debug("loadMembers のキャンセルに失敗: guildId=$guildId", ignored)
        }
        logFailure("タイムアウトしました", guildId, e)
    } catch (e: Exception) {
        logFailure("待機に失敗しました", guildId, e.cause ?: e)
    }

    private fun logFailure(reason: String, guildId: Long, e: Throwable): Result<List<GuildMemberSnapshot>> {
        logger.warn("ギルドメンバーの取得が$reason: guildId=$guildId, error=${e.javaClass.simpleName}: ${e.message}")
        logger.debug("ギルドメンバー取得のスタックトレース: guildId=$guildId", e)
        return Result.failure(RaidDomainError.GuildMembersUnavailable)
    }

    private fun Member.toSnapshot(): GuildMemberSnapshot = GuildMemberSnapshot(
        userId = user.idLong,
        username = user.name,
        globalName = user.globalName,
        nickname = nickname,
        avatarUrl = effectiveAvatarUrl,
        isBot = user.isBot || user.isSystem,
    )

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
