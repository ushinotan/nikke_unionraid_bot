package com.nikke.bot.entity

import org.komapper.annotation.KomapperEntity
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperTable
import java.time.OffsetDateTime

@KomapperEntity
@KomapperTable("guilds")
data class Guild(
    @KomapperId
    val guildId: Long,
    val createdAt: OffsetDateTime? = null
)
