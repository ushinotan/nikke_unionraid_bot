package com.nikke.bot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import org.springframework.beans.factory.annotation.Value
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
class DiscordBot(
    @Value("\${discord.token}") private val token: String
) {
    private val logger = LoggerFactory.getLogger(DiscordBot::class.java)
    private lateinit var jda: JDA

    private val DEFAULT_TOKEN = "dummy-token-for-devcontainer"

    @PostConstruct
    fun init() {
        if (token.isBlank() || token == DEFAULT_TOKEN) {
            throw IllegalStateException("Discord Botトークンが未設定のため、起動を中止します。")
        }

        try {
            jda = JDABuilder.createDefault(token).build()
            jda.awaitReady()
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
}