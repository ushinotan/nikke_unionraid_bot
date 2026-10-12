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
        val ageMs = java.time.Duration.between(event.timeCreated, java.time.OffsetDateTime.now()).toMillis()
        logger.debug("onSlashCommandInteraction: name=${event.name}, age=${ageMs}ms")
        when (event.name) {
            "レイド作成" -> handleRaidCreate(event, ageMs)
            "レイド終了" -> handleRaidEnd(event)
        }
    }

    private fun handleRaidCreate(event: SlashCommandInteractionEvent, ageMs: Long = 0) {
        val rawDuration = event.getOption("期間時間")?.asLong?.toInt() ?: 24
        if (rawDuration < 1) {
            event.reply("レイドの期間は1時間以上で指定してください。").setEphemeral(true).queue(
                { logger.info("レイド作成: 無効な期間をエラー返却 (age=${ageMs}ms)") },
                { e -> logger.warn("レイド作成エラー返却に失敗 (interaction age=${ageMs}ms)", e) },
            )
            return
        }
        val duration = rawDuration
        val defaultTime = TimeUtils.localNow(appConfig.defaultTimezoneHours).format(timeFormatter)
        val textInput = TextInput.create("start_time", TextInputStyle.SHORT)
            .setRequired(true)
            .setValue(defaultTime)
            .build()
        val modal = Modal.create("raid_start_modal_$duration", "レイド開始設定")
            .addComponents(Label.of("開始時刻 (YYYY-MM-DD HH:MM JST)", textInput))
            .build()
        event.replyModal(modal).queue(
            { logger.info("レイド作成モーダル表示をキューイング (age=${ageMs}ms)") },
            { e -> logger.warn("レイド作成モーダルの表示に失敗 (interaction age=${ageMs}ms)", e) },
        )
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (!event.modalId.startsWith("raid_start_modal_")) return
        // まず即deferして3秒ルールを確実に守る
        event.deferReply().queue()
        val duration = event.modalId.removePrefix("raid_start_modal_").toIntOrNull()
        if (duration == null || duration < 1) {
            event.hook.sendMessage("レイドの期間は1時間以上で指定してください。").setEphemeral(true).queue()
            return
        }

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
        when (event.componentId) {
            REPORT_BUTTON_ID -> handleReportButton(event)
            REPORT_CANCEL_BUTTON_ID -> handleReportCancelButton(event)
        }
    }

    private fun extractRaidId(event: ButtonInteractionEvent): Int? =
        event.message.embeds.firstOrNull()
            ?.fields?.find { it.name == "レイドID" }?.value?.trim('`')?.toIntOrNull()

    private fun handleReportButton(event: ButtonInteractionEvent) {
        val raidId = extractRaidId(event)
        if (raidId == null) {
            event.reply("レイド情報を取得できませんでした。").setEphemeral(true).queue()
            return
        }
        // 終了済みレイドでは難易度メニューを出さない（Bot 再起動後などでボタンが残っている場合の対策）
        raidDomainService.ensureRaidActive(raidId).onFailure { error ->
            event.reply(raidErrorMessage(error, "報告できません")).setEphemeral(true).queue()
            return
        }

        val selectMenu = StringSelectMenu.create("$DIFFICULTY_SELECT_PREFIX$raidId")
            .setPlaceholder("難易度を選択してください")
            .addOption("ノーマル", Difficulty.NORMAL.value)
            .addOption("ハード", Difficulty.HARD.value)
            .build()
        event.reply("難易度を選択してください:").addComponents(ActionRow.of(selectMenu)).setEphemeral(true).queue()
    }

    private fun handleReportCancelButton(event: ButtonInteractionEvent) {
        val raidId = extractRaidId(event)
        if (raidId == null) {
            event.reply("レイド情報を取得できませんでした。").setEphemeral(true).queue()
            return
        }

        raidDomainService.ensureRaidActive(raidId).onFailure { error ->
            event.reply(raidErrorMessage(error, "報告を取り消せません")).setEphemeral(true).queue()
            return
        }

        // 自分が報告済みの難易度だけを選択肢に出す（未報告の難易度を選べないようにする）
        val reported = raidDomainService.findReportedDifficulties(raidId, event.user.idLong)
        if (reported.isEmpty()) {
            event.reply("取り消せる報告がありません。").setEphemeral(true).queue()
            return
        }

        val selectMenu = StringSelectMenu.create("$CANCEL_SELECT_PREFIX$raidId")
            .setPlaceholder("取り消す難易度を選択してください（複数選択可）")
            .setRequiredRange(1, reported.size)
            .apply { reported.forEach { addOption(it.label, it.value) } }
            .build()
        event.reply("取り消す報告を選択してください:").addComponents(ActionRow.of(selectMenu)).setEphemeral(true).queue()
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val componentId = event.componentId
        when {
            componentId.startsWith(DIFFICULTY_SELECT_PREFIX) -> handleDifficultySelect(event)
            componentId.startsWith(CANCEL_SELECT_PREFIX) -> handleCancelSelect(event)
        }
    }

    private fun handleDifficultySelect(event: StringSelectInteractionEvent) {
        val raidId = event.componentId.removePrefix(DIFFICULTY_SELECT_PREFIX).toIntOrNull() ?: return
        val difficultyValue = event.values.firstOrNull() ?: return
        val difficulty = Difficulty.fromValue(difficultyValue)
        val userId = event.user.idLong

        val alreadyReported = raidDomainService.hasReported(raidId, userId, difficulty)

        val result = raidDomainService.reportThreeTurn(
            raidId = raidId,
            userId = userId,
            username = event.member?.effectiveName ?: event.user.name,
            difficulty = difficulty,
        )

        result.onSuccess {
            val message = if (alreadyReported) {
                "${difficulty.label}は既に報告済みでした。報告時刻を更新しました。"
            } else {
                "報告を受け付けました。"
            }
            replaceSelectMenu(event, message)
            scheduler.updateRaidMessage(raidId)
        }.onFailure { error ->
            replaceSelectMenu(event, raidErrorMessage(error, "報告できません"))
        }
    }

    private fun handleCancelSelect(event: StringSelectInteractionEvent) {
        val raidId = event.componentId.removePrefix(CANCEL_SELECT_PREFIX).toIntOrNull() ?: return
        val difficulties = event.values.map { Difficulty.fromValue(it) }.toSet()

        val result = raidDomainService.cancelThreeTurnReports(
            raidId = raidId,
            userId = event.user.idLong,
            difficulties = difficulties,
        )

        result.onSuccess { cancelled ->
            if (cancelled.isEmpty()) {
                replaceSelectMenu(event, "取り消せる報告がありませんでした。")
                return@onSuccess
            }
            // 選択値ではなく、実際に取り消された難易度を表示する
            val labels = cancelled.sortedBy { it.ordinal }.joinToString("・") { it.label }
            replaceSelectMenu(event, "${labels}の報告を取り消しました。")
            scheduler.updateRaidMessage(raidId)
        }.onFailure { error ->
            replaceSelectMenu(event, raidErrorMessage(error, "報告を取り消せません"))
        }
    }

    /** 使用済みの選択メニューを結果メッセージに置き換え、再操作できないようにする。 */
    private fun replaceSelectMenu(event: StringSelectInteractionEvent, message: String) {
        event.editMessage(message).setComponents().queue()
    }

    private fun raidErrorMessage(error: Throwable, finishedSuffix: String): String = when (error) {
        is RaidDomainError.RaidNotFound -> "レイド情報を取得できませんでした。"
        is RaidDomainError.RaidAlreadyFinished -> "このレイドは既に終了しているため、$finishedSuffix。"
        is RaidDomainError.NoDifficultySelected -> "取り消す難易度を選択してください。"
        else -> "エラーが発生しました: ${error.message}"
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        guildRepository.insertGuild(Guild(guildId = event.guild.idLong))
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        guildRepository.deleteGuildById(event.guild.idLong)
    }

    companion object {
        const val REPORT_BUTTON_ID = "raid_report"
        const val REPORT_CANCEL_BUTTON_ID = "raid_report_cancel"
        private const val DIFFICULTY_SELECT_PREFIX = "difficulty_select_"
        private const val CANCEL_SELECT_PREFIX = "cancel_report_select_"
    }
}
