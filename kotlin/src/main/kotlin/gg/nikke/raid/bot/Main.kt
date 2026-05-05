package gg.nikke.raid.bot

import gg.nikke.raid.bot.config.AppConfig
import gg.nikke.raid.bot.config.DiscordConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(DiscordConfig::class, AppConfig::class)
open class NikkeApplication

fun main(args: Array<String>) {
    runApplication<NikkeApplication>(*args)
}