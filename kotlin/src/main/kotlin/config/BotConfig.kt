package com.nikke.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "discord")
class DiscordConfig {
    lateinit var token: String
}

@Configuration
@ConfigurationProperties(prefix = "app")
class AppConfig {
    var defaultTimezoneHours: Int = 9
}
