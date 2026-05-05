package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
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
) {
    private val logger = LoggerFactory.getLogger(RaidNotificationScheduler::class.java)
    private val executor = Executors.newScheduledThreadPool(4)
    private val scheduledTasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()
    private val notifyMessages = ConcurrentHashMap<Int, Message>()

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
    }

    fun scheduleNotification(
        guildId: Long,
        raidId: Int,
        channelId: Long,
        startTimeEpoch: Long,
        delaySeconds: Long,
        jda: JDA,
    ) {
        if (scheduledTasks.containsKey(guildId)) return

        val future = executor.schedule({
            try {
                sendNotification(jda, raidId, channelId, startTimeEpoch)
            } finally {
                scheduledTasks.remove(guildId)
            }
        }, delaySeconds, TimeUnit.SECONDS)

        scheduledTasks[guildId] = future
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

    fun cancelNotification(guildId: Long) {
        scheduledTasks.remove(guildId)?.cancel(false)
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
