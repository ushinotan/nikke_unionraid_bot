package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.service.RaidDomainService
import gg.nikke.raid.bot.util.TimeUtils
import jakarta.annotation.PreDestroy
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.Permission
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class RaidNotificationScheduler(
    private val unionRaidRepository: UnionRaidRepository,
    private val raidReportRepository: RaidReportRepository,
    private val raidDomainService: RaidDomainService,
) {
    private val logger = LoggerFactory.getLogger(RaidNotificationScheduler::class.java)
    private val executor = Executors.newScheduledThreadPool(4)
    private val scheduledNotifyTasks = ConcurrentHashMap<Long, ScheduledRaidTask>()
    private val scheduledAutoEndTasks = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private val notifyMessages = ConcurrentHashMap<Int, Message>()

    private data class ScheduledRaidTask(
        val raidId: Int,
        val future: ScheduledFuture<*>,
    )

    fun resumeSchedules(jda: JDA) {
        val now = TimeUtils.utcNow()
        val notifiable = unionRaidRepository.findNotifiableActiveUnionRaids(now)
        logger.info("通知スケジュール再開対象: ${notifiable.size}件")
        notifiable.forEach { raid ->
            val delaySeconds = maxOf(0L, TimeUtils.secondsUntil(raid.notifyTime!!, now))
            scheduleNotification(
                guildId = raid.guildId,
                raidId = raid.id,
                channelId = raid.channelId,
                startTimeEpoch = raid.startTime.toEpochSecond(),
                delaySeconds = delaySeconds,
                jda = jda,
            )
        }

        val unfinished = unionRaidRepository.findUnfinishedUnionRaids()
        logger.info("自動終了スケジュール再開対象: ${unfinished.size}件")
        unfinished.forEach { raid ->
            val delaySeconds = maxOf(0L, TimeUtils.secondsUntil(raid.endTime, now))
            scheduleAutoEnd(
                guildId = raid.guildId,
                raidId = raid.id,
                channelId = raid.channelId,
                raidName = raid.raidName,
                delaySeconds = delaySeconds,
                jda = jda,
            )
        }
    }

    fun scheduleNotification(
        guildId: Long,
        raidId: Int,
        channelId: Long,
        startTimeEpoch: Long,
        delaySeconds: Long,
        jda: JDA,
    ) {
        // 同一ギルドの古い予定は置き換える（握りつぶしだと後続レイドの通知が永久に飛ぶ）
        scheduledNotifyTasks.remove(guildId)?.future?.cancel(false)

        val safeDelay = maxOf(0L, delaySeconds)
        // delay=0 だと schedule 完了前にタスクが走り、finally が map 未登録のまま残る競合が起きる
        if (safeDelay == 0L) {
            logger.info("通知を即時実行: raidId=$raidId guildId=$guildId channelId=$channelId")
            executor.execute {
                sendNotification(jda, raidId, channelId, startTimeEpoch)
            }
            return
        }

        val future = executor.schedule({
            try {
                sendNotification(jda, raidId, channelId, startTimeEpoch)
            } finally {
                scheduledNotifyTasks.computeIfPresent(guildId) { _, task ->
                    if (task.raidId == raidId) null else task
                }
            }
        }, safeDelay, TimeUnit.SECONDS)

        scheduledNotifyTasks[guildId] = ScheduledRaidTask(raidId, future)
        logger.info("通知をスケジュール: raidId=$raidId guildId=$guildId delaySeconds=$safeDelay")
    }

    fun scheduleAutoEnd(
        guildId: Long,
        raidId: Int,
        channelId: Long,
        raidName: String,
        delaySeconds: Long,
        jda: JDA,
    ) {
        scheduledAutoEndTasks.remove(raidId)?.cancel(false)

        val safeDelay = maxOf(0L, delaySeconds)
        if (safeDelay == 0L) {
            logger.info("自動終了を即時実行: raidId=$raidId guildId=$guildId")
            executor.execute {
                autoEndRaid(jda, guildId, raidId, channelId, raidName)
            }
            return
        }

        // 成功時は cleanup、失敗時はリトライ予約が map を更新するため finally では消さない
        val future = executor.schedule({
            autoEndRaid(jda, guildId, raidId, channelId, raidName)
        }, safeDelay, TimeUnit.SECONDS)

        scheduledAutoEndTasks[raidId] = future
        logger.info("自動終了をスケジュール: raidId=$raidId guildId=$guildId delaySeconds=$safeDelay")
    }

    private fun sendNotification(
        jda: JDA,
        raidId: Int,
        channelId: Long,
        startTimeEpoch: Long,
    ) {
        val raid = unionRaidRepository.findUnionRaidById(raidId)
        if (raid == null || raid.finishedAt != null) {
            logger.info("通知をスキップしました（終了済みまたは未存在）: raidId=$raidId")
            // If the raid is already finished by notification time, clear the pending flag.
            raid?.let { unionRaidRepository.clearNotifyTime(raidId) }
            return
        }

        val channel = resolveMessageChannel(jda, channelId) ?: run {
            logger.warn("通知チャンネルが見つかりません: channelId=$channelId raidId=$raidId")
            // Do not clear notify_time here. Leave it so the notification can be retried on restart
            // (e.g. channel was temporarily unavailable or permissions were fixed).
            return
        }
        val embed = EmbedBuilder()
            .setTitle("📣 ユニオンレイド通知")
            .setDescription("人間、ユニオンレイドが始まったわ。")
            .setColor(Color(0x5865F2))
            .addField("開始時刻", "<t:${startTimeEpoch}:F>", true)
            .addField("レイドID", "`$raidId`", true)
            .addField("ノーマル 3凸", "なし", false)
            .addField("ハード 3凸", "なし", false)
            .build()

        logger.info("通知送信を開始: raidId=$raidId channelId=$channelId")

        val canMentionEveryone = (channel as? GuildMessageChannel)
            ?.guild?.selfMember?.hasPermission(Permission.MESSAGE_MENTION_EVERYONE) ?: false
        val content = if (canMentionEveryone) "@everyone" else ""
        val action = channel.sendMessage(content)
            .addEmbeds(embed)
            .addComponents(ActionRow.of(Button.primary("raid_report", "報告")))
        if (canMentionEveryone) {
            action.setAllowedMentions(setOf(MentionType.EVERYONE))
        }
        action.queue(
            { message ->
                // Only clear notify_time after we have confirmation the message was successfully sent.
                // This allows retry on restart if sending fails (transient error, channel temporarily unavailable, etc.).
                unionRaidRepository.clearNotifyTime(raidId)
                notifyMessages[raidId] = message
                logger.info("通知送信に成功: raidId=$raidId messageId=${message.idLong}")
            },
            { e ->
                logger.error("通知送信中にエラーが発生しました: raidId=$raidId", e)
                // Do not clear notify_time on failure so that the notification can be retried
                // on bot restart via resumeSchedules().
            },
        )
    }

    private fun resolveMessageChannel(jda: JDA, channelId: Long): MessageChannel? {
        jda.getTextChannelById(channelId)?.let { return it }
        jda.getChannelById(MessageChannel::class.java, channelId)?.let { return it }
        for (guild in jda.guilds) {
            guild.getTextChannelById(channelId)?.let { return it }
        }
        return null
    }

    private fun autoEndRaid(
        jda: JDA,
        guildId: Long,
        raidId: Int,
        channelId: Long,
        raidName: String,
    ) {
        val result = runCatching {
            raidDomainService.finishRaid(raidId = raidId)
        }.getOrElse { error ->
            logger.error("レイド自動終了中に例外が発生しました: raidId=$raidId", error)
            rescheduleAutoEndRetry(jda, guildId, raidId, channelId, raidName)
            return
        }

        result.onSuccess { updatedCount ->
            if (updatedCount == 0L) {
                logger.info("自動終了をスキップしました（既に終了済み）: raidId=$raidId")
                cleanupAfterRaidEnd(guildId, raidId)
                return@onSuccess
            }

            val aggregation = raidDomainService.aggregateRaidResults(raidId)
            val embed = EmbedBuilder()
                .setTitle("レイド $raidName の終了報告")
                .setDescription("予定時刻になったため、自動で終了したわ。")
                .setColor(Color(0xFEE75C))
                .addField("ノーマル 3凸", aggregation.normalUsers.joinToString("\n").ifEmpty { "なし" }, false)
                .addField("ハード 3凸", aggregation.hardUsers.joinToString("\n").ifEmpty { "なし" }, false)
                .build()

            val channel = resolveMessageChannel(jda, channelId)
            if (channel == null) {
                logger.warn("自動終了の通知チャンネルが見つかりません: channelId=$channelId raidId=$raidId")
            } else {
                channel.sendMessage("人間、今回もおつかれさま。")
                    .addEmbeds(embed)
                    .queue(
                        null,
                        { e -> logger.error("自動終了メッセージ送信中にエラーが発生しました: raidId=$raidId", e) },
                    )
            }

            cleanupAfterRaidEnd(guildId, raidId)
            logger.info("レイドを自動終了しました: raidId=$raidId guildId=$guildId")
        }.onFailure { error ->
            logger.error("レイド自動終了中にエラーが発生しました: raidId=$raidId", error)
            rescheduleAutoEndRetry(jda, guildId, raidId, channelId, raidName)
        }
    }

    private fun rescheduleAutoEndRetry(
        jda: JDA,
        guildId: Long,
        raidId: Int,
        channelId: Long,
        raidName: String,
    ) {
        // 未終了のまま放置すると新規作成がブロックされるため、短時間後に再試行する
        logger.info(
            "自動終了をリトライ予約: raidId=$raidId delaySeconds=$AUTO_END_RETRY_DELAY_SECONDS",
        )
        scheduleAutoEnd(
            guildId = guildId,
            raidId = raidId,
            channelId = channelId,
            raidName = raidName,
            delaySeconds = AUTO_END_RETRY_DELAY_SECONDS,
            jda = jda,
        )
    }

    /**
     * 報告ボタン無効化。
     * 現状はインメモリの通知メッセージに依存するため、再起動後は no-op になる（将来 messageId 永続化で改善予定）。
     */
    fun disableReportButton(raidId: Int) {
        val message = notifyMessages[raidId] ?: return
        message.editMessageComponents().queue(
            null,
            { e -> logger.warn("報告ボタン無効化に失敗しました: raidId=$raidId", e) },
        )
    }

    fun cleanupAfterRaidEnd(guildId: Long, raidId: Int) {
        disableReportButton(raidId)
        cancelNotification(guildId, raidId)
        cancelAutoEnd(raidId)
        removeMessage(raidId)
    }

    fun cancelNotification(guildId: Long, raidId: Int? = null) {
        val task = scheduledNotifyTasks[guildId] ?: return
        if (raidId != null && task.raidId != raidId) return
        scheduledNotifyTasks.remove(guildId)?.future?.cancel(false)
    }

    fun cancelAutoEnd(raidId: Int) {
        scheduledAutoEndTasks.remove(raidId)?.cancel(false)
    }

    fun removeMessage(raidId: Int) {
        notifyMessages.remove(raidId)
    }

    fun updateRaidMessage(raidId: Int) {
        val message = notifyMessages[raidId] ?: return
        try {
            val reports = raidReportRepository.findRaidReportsByRaidId(raidId)
            val normalUsers = reports.filter { it.difficulty == "normal" }.map { it.username }
            val hardUsers = reports.filter { it.difficulty == "hard" }.map { it.username }

            val embed = message.embeds.firstOrNull() ?: return
            val updatedEmbed = EmbedBuilder()
                .setTitle(embed.title)
                .setDescription(embed.description)
                .setColor(embed.colorRaw)
            embed.fields.forEach { field ->
                when (field.name) {
                    "ノーマル 3凸" -> updatedEmbed.addField(
                        "ノーマル 3凸", normalUsers.joinToString("\n").ifEmpty { "なし" }, false,
                    )
                    "ハード 3凸" -> updatedEmbed.addField(
                        "ハード 3凸", hardUsers.joinToString("\n").ifEmpty { "なし" }, false,
                    )
                    else -> updatedEmbed.addField(field.name ?: "", field.value ?: "", field.isInline)
                }
            }

            message.editMessageEmbeds(updatedEmbed.build()).queue()
        } catch (e: Exception) {
            logger.error("レイドメッセージ更新中にエラーが発生しました: raidId=$raidId", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    companion object {
        private const val AUTO_END_RETRY_DELAY_SECONDS = 30L
    }
}
