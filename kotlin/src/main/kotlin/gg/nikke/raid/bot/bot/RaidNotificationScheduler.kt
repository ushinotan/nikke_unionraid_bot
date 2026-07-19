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

        unionRaidRepository.findNotifiableActiveUnionRaids(now).forEach { raid ->
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

        unionRaidRepository.findUnfinishedUnionRaids().forEach { raid ->
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
        if (scheduledNotifyTasks.containsKey(guildId)) return

        val future = executor.schedule({
            try {
                sendNotification(jda, raidId, channelId, startTimeEpoch)
            } finally {
                scheduledNotifyTasks.computeIfPresent(guildId) { _, task ->
                    if (task.raidId == raidId) null else task
                }
            }
        }, delaySeconds, TimeUnit.SECONDS)

        scheduledNotifyTasks[guildId] = ScheduledRaidTask(raidId, future)
    }

    fun scheduleAutoEnd(
        guildId: Long,
        raidId: Int,
        channelId: Long,
        raidName: String,
        delaySeconds: Long,
        jda: JDA,
    ) {
        if (scheduledAutoEndTasks.containsKey(raidId)) return

        val future = executor.schedule({
            try {
                autoEndRaid(jda, guildId, raidId, channelId, raidName)
            } finally {
                scheduledAutoEndTasks.remove(raidId)
            }
        }, delaySeconds, TimeUnit.SECONDS)

        scheduledAutoEndTasks[raidId] = future
    }

    private fun sendNotification(
        jda: JDA,
        raidId: Int,
        channelId: Long,
        startTimeEpoch: Long,
    ) {
        val channel = jda.getTextChannelById(channelId) ?: run {
            logger.warn("通知チャンネルが見つかりません: channelId=$channelId")
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

        channel.sendMessageEmbeds(embed)
            .addComponents(ActionRow.of(Button.primary("raid_report", "報告")))
            .queue(
                { message ->
                    notifyMessages[raidId] = message
                    unionRaidRepository.clearNotifyTime(raidId)
                },
                { e -> logger.error("通知送信中にエラーが発生しました: raidId=$raidId", e) },
            )
    }

    private fun autoEndRaid(
        jda: JDA,
        guildId: Long,
        raidId: Int,
        channelId: Long,
        raidName: String,
    ) {
        val result = raidDomainService.finishRaid(raidId = raidId)
        result.onSuccess { updatedCount ->
            if (updatedCount == 0L) {
                logger.info("自動終了をスキップしました（既に終了済み）: raidId=$raidId")
                cleanupAfterEnd(guildId, raidId)
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

            val channel = jda.getTextChannelById(channelId)
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

            disableReportButton(raidId)
            cleanupAfterEnd(guildId, raidId)
            logger.info("レイドを自動終了しました: raidId=$raidId guildId=$guildId")
        }.onFailure { error ->
            logger.error("レイド自動終了中にエラーが発生しました: raidId=$raidId", error)
        }
    }

    private fun disableReportButton(raidId: Int) {
        val message = notifyMessages[raidId] ?: return
        message.editMessageComponents().queue(
            null,
            { e -> logger.warn("報告ボタン無効化に失敗しました: raidId=$raidId", e) },
        )
    }

    private fun cleanupAfterEnd(guildId: Long, raidId: Int) {
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
}
