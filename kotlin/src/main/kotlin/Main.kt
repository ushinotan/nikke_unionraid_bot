package com.nikke

import com.nikke.config.AppConfig
import com.nikke.config.DiscordConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(DiscordConfig::class, AppConfig::class)
class NikkeApplication

fun main(args: Array<String>) {
    runApplication<NikkeApplication>(*args)
}
