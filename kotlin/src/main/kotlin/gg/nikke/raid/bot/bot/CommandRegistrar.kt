package gg.nikke.raid.bot.bot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands

object CommandRegistrar {
    fun register(jda: JDA) {
        jda.updateCommands().addCommands(
            Commands.slash("レイド作成", "新しいユニオンレイドを作成します")
                .addOption(OptionType.INTEGER, "期間時間", "レイドの期間（時間単位、デフォルト: 24時間）", false)
                .setContexts(InteractionContextType.GUILD),
            Commands.slash("レイド終了", "進行中のレイドを終了し、3凸報告を集計します")
                .addOption(OptionType.INTEGER, "順位", "最終ランキング順位（例: 1, 42）", false)
                .addOption(OptionType.NUMBER, "パーセンテージ", "最終ランキングの上位パーセンテージ（例: 5.3）", false)
                .setContexts(InteractionContextType.GUILD),
        ).queue()
    }
}
