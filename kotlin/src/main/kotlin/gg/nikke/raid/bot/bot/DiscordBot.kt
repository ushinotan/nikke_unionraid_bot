package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.config.DiscordConfig
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
@ConditionalOnProperty(
    prefix = "discord.bot",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class DiscordBot(
    private val discordConfig: DiscordConfig
) {
    private val logger = LoggerFactory.getLogger(DiscordBot::class.java)
    private lateinit var jda: JDA

    private val DEFAULT_TOKEN = "dummy-token-for-devcontainer"
    private val READY_TIMEOUT_SECONDS = 30L

    @PostConstruct
    fun init() {
        val token = discordConfig.token
        if (token.isBlank() || token == DEFAULT_TOKEN) {
            throw IllegalStateException("Discord Botトークンが未設定のため、起動を中止します。")
        }

        try {
            jda = JDABuilder.createDefault(token).build()
            waitForReadyWithTimeout(jda)
            logger.info("Discord Botが正常に起動しました。")
        } catch (e: InvalidTokenException) {
            throw IllegalStateException("Discord Botトークンが無効なため、起動を中止します。", e)
        } catch (e: Exception) {
            throw IllegalStateException("Discord Botの起動中にエラーが発生したため、起動を中止します。", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        if (::jda.isInitialized) {
            jda.shutdown()
            logger.info("Discord Botが正常にシャットダウンしました。")
        }
    }

    private fun waitForReadyWithTimeout(jda: JDA) {
        val readyFuture = CompletableFuture.runAsync {
            try {
                jda.awaitReady()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Discord Botの起動待機中に割り込みが発生しました。", e)
            }
        }

        try {
            readyFuture.get(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            readyFuture.cancel(true)
            jda.shutdownNow()
            Thread.currentThread().interrupt()
            throw IllegalStateException("Discord BotのREADY待機中に割り込みが発生したため、起動を中止します。", e)
        } catch (e: TimeoutException) {
            readyFuture.cancel(true)
            jda.shutdownNow()
            throw IllegalStateException(
                "Discord BotのREADY待機が${READY_TIMEOUT_SECONDS}秒でタイムアウトしたため、起動を中止します。",
                e,
            )
        } catch (e: ExecutionException) {
            readyFuture.cancel(true)
            jda.shutdownNow()
            throw IllegalStateException("Discord BotのREADY待機中にエラーが発生したため、起動を中止します。", e.cause ?: e)
        }
    }
}