package com.nikke.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class AppConfig {
    var defaultTimezoneHours: Int = 9
}