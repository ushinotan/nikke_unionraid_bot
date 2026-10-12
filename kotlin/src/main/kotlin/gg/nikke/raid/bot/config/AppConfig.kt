package gg.nikke.raid.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class AppConfig {
    var defaultTimezoneHours: Int = 9

    /**
     * BFF(Next.js) -> Backend 間の内部API認証に使う共有シークレット。
     * 未設定(空)の場合は fail-closed とし、`/api` 配下は全リクエストを拒否する。
     */
    var internalApiToken: String = ""
}
