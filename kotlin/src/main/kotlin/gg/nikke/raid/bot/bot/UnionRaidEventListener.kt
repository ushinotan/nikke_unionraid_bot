package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.config.AppConfig
import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.service.Difficulty
import gg.nikke.raid.bot.service.RaidDomainError
import gg.nikke.raid.bot.service.RaidDomainService
import gg.nikke.raid.bot.util.TimeUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.modals.Modal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Component
class UnionRaidEventListener(
    private val raidDomainService: RaidDomainService,
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val scheduler: RaidNotificationScheduler,
    private val appConfig: AppConfig,
) : ListenerAdapter() {
    private val logger = LoggerFactory.getLogger(UnionRaidEventListener::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "レイド作成" -> handleRaidCreate(event)
            "レイド終了" -> handleRaidEnd(event)
        }
    }

    private fun handleRaidCreate(event: SlashCommandInteractionEvent) {
        val duration = event.getOption("期間時間")?.asLong?.toInt() ?: 24
        if (duration < 1) {
            event.reply("レイドの期間は1時間以上で指定してください。").setEphemeral(true).queue()
            return
        }
        val defaultTime = TimeUtils.localNow(appConfig.defaultTimezoneHours).format(timeFormatter)
        val textInput = TextInput.create("start_time", TextInputStyle.SHORT)
            .setRequired(true)
            .setValue(defaultTime)
            .build()
        val modal = Modal.create("raid_start_modal_$duration", "レイド開始設定")
            .addComponents(Label.of("開始時刻 (YYYY-MM-DD HH:MM JST)", textInput))
            .build()
        event.replyModal(modal).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith("raid_start_modal_")) return
        val duration = event.modalId.removePrefix("raid_start_modal_").toIntOrNull() ?: return
        event.deferReply().queue()

        val guildId = event.guild?.idLong
        if (guildId == null) {
            event.hook.sendMessage("このコマンドはサーバー内でのみ使用可能です。").setEphemeral(true).queue()
            return
        }

        try {
            val startText = event.getValue("start_time")?.asString?.trim()
                ?: run {
                    event.hook.sendMessage("入力値を取得できませんでした。").setEphemeral(true).queue()
                    return
                }
            val offset = ZoneOffset.ofHours(appConfig.defaultTimezoneHours)
            val startTimeUtc = LocalDateTime.parse(startText, timeFormatter)
                .atOffset(offset)
                .withOffsetSameInstant(ZoneOffset.UTC)
            val endTimeUtc = startTimeUtc.plusHours(duration.toLong())
            val channelId = event.channel.idLong

            guildRepository.insertGuild(Guild(guildId = guildId))

            val result = raidDomainService.createRaid(
                guildId = guildId,
                raidName = "ユニオンレイド",
                startTime = startTimeUtc,
                endTime = endTimeUtc,
                notifyTime = startTimeUtc,
                channelId = channelId,
            )

            result.onSuccess { raid ->
                val embed = EmbedBuilder()
                    .setTitle("🎯 ユニオンレイドを作成しました")
                    .setDescription("**ユニオンレイド**")
                    .setColor(Color(0x57F287))
                    .addField("レイドID", "`${raid.id}`", true)
                    .addField("期間", "${duration}時間", true)
                    .addField("開始時刻", "<t:${startTimeUtc.toEpochSecond()}:F>", false)
                    .addField("終了時刻", "<t:${endTimeUtc.toEpochSecond()}:F>", false)
                    .setFooter("作成者: ${event.member?.effectiveName ?: event.user.name}")
                    .build()

                event.hook.sendMessageEmbeds(embed).queue()
                scheduler.scheduleNotification(
                    guildId = guildId,
                    raidId = raid.id,
                    channelId = channelId,
                    startTimeEpoch = startTimeUtc.toEpochSecond(),
                    delaySeconds = maxOf(0L, TimeUtils.secondsUntil(startTimeUtc)),
                    jda = event.jda,
                )
                scheduler.scheduleAutoEnd(
                    guildId = guildId,
                    raidId = raid.id,
                    channelId = channelId,
                    raidName = raid.raidName,
                    delaySeconds = maxOf(0L, TimeUtils.secondsUntil(endTimeUtc)),
                    jda = event.jda,
                )
            }.onFailure { error ->
                val message = when (error) {
                    is RaidDomainError.ActiveRaidAlreadyExists ->
                        "既に進行中のレイドが存在してるわ。先に終了させて頂戴。（/レイド終了）"
                    is RaidDomainError.DurationTooShort ->
                        "レイド期間は1時間以上にしてください。"
                    else -> "エラーが発生しました: ${error.message}"
                }
                event.hook.sendMessage(message).setEphemeral(true).queue()
            }
        } catch (e: DateTimeParseException) {
            event.hook.sendMessage("時刻の形式が正しくありません。YYYY-MM-DD HH:MM の形式で入力してください。")
                .setEphemeral(true).queue()
        } catch (e: Exception) {
            logger.error("レイド作成モーダル処理中にエラーが発生しました", e)
            event.hook.sendMessage("エラーが発生しました: ${e.message}").setEphemeral(true).queue()
        }
    }

    private fun handleRaidEnd(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()

        val guildId = event.guild?.idLong
        if (guildId == null) {
            event.hook.sendMessage("このコマンドはサーバー内でのみ使用可能です。").setEphemeral(true).queue()
            return
        }

        val ranking = event.getOption("順位")?.asLong?.toInt()
        val percentage = event.getOption("パーセンテージ")?.asDouble
            ?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) }

        val activeRaid = unionRaidRepository.findActiveUnionRaidByGuildId(guildId)
        if (activeRaid == null) {
            event.hook.sendMessage("進行中のレイドが見つかりません。").setEphemeral(true).queue()
            return
        }

        val result = raidDomainService.finishRaid(
            raidId = activeRaid.id,
            ranking = ranking,
            percentage = percentage,
        )

        result.onSuccess { updatedCount ->
            if (updatedCount == 0L) {
                scheduler.cleanupAfterRaidEnd(guildId, activeRaid.id)
                event.hook.sendMessage("このレイドは既に終了済みだわ。").setEphemeral(true).queue()
                return@onSuccess
            }
            val aggregation = raidDomainService.aggregateRaidResults(activeRaid.id)
            val embed = EmbedBuilder()
                .setTitle("レイド ${activeRaid.raidName} の終了報告")
                .setColor(Color(0xFEE75C))
            if (ranking != null) embed.addField("最終順位", "${ranking}位", true)
            if (percentage != null) embed.addField("上位パーセンテージ", "${percentage}%", true)
            embed.addField("ノーマル 3凸", aggregation.normalUsers.joinToString("\n").ifEmpty { "なし" }, false)
            embed.addField("ハード 3凸", aggregation.hardUsers.joinToString("\n").ifEmpty { "なし" }, false)

            scheduler.cleanupAfterRaidEnd(guildId, activeRaid.id)

            event.hook.sendMessage("人間、今回もおつかれさま。").addEmbeds(embed.build()).queue()
        }.onFailure { error ->
            val message = when (error) {
                is RaidDomainError.RankingAndPercentageBothSpecified ->
                    "順位かパーセンテージのどちらか一方のみ指定してください。"
                is RaidDomainError.RankingOutOfRange ->
                    "順位は1以上で指定してください。"
                is RaidDomainError.PercentageOutOfRange ->
                    "パーセンテージは0より大きく100以下で指定してください。"
                else -> "エラーが発生しました: ${error.message}"
            }
            event.hook.sendMessage(message).setEphemeral(true).queue()
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId != "raid_report") return

        val embed = event.message.embeds.firstOrNull()
        val raidId = embed?.fields?.find { it.name == "レイドID" }?.value?.trim('`')?.toIntOrNull()
        if (raidId == null) {
            event.reply("レイド情報を取得できませんでした。").setEphemeral(true).queue()
            return
        }

        val selectMenu = StringSelectMenu.create("difficulty_select_$raidId")
            .setPlaceholder("難易度を選択してください")
            .addOption("ノーマル", "normal")
            .addOption("ハード", "hard")
            .build()
        event.reply("難易度を選択してください:").addComponents(ActionRow.of(selectMenu)).setEphemeral(true).queue()
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        if (!event.componentId.startsWith("difficulty_select_")) return

        val raidId = event.componentId.removePrefix("difficulty_select_").toIntOrNull() ?: return
        val difficultyValue = event.values.firstOrNull() ?: return
        val difficulty = Difficulty.fromValue(difficultyValue)

        val result = raidDomainService.reportThreeTurn(
            raidId = raidId,
            userId = event.user.idLong,
            username = event.member?.effectiveName ?: event.user.name,
            difficulty = difficulty,
        )

        result.onSuccess {
            event.reply("報告を受け付けました。").setEphemeral(true).queue()
            scheduler.updateRaidMessage(raidId)
        }.onFailure { error ->
            event.reply("エラーが発生しました: ${error.message}").setEphemeral(true).queue()
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        guildRepository.insertGuild(Guild(guildId = event.guild.idLong))
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        guildRepository.deleteGuildById(event.guild.idLong)
    }
}
