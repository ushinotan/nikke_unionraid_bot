package gg.nikke.raid.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "discord")
class DiscordConfig {
    var token: String = ""
    var devGuildId: String? = null
}
