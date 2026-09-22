package gg.nikke.raid.bot.entity

import org.komapper.annotation.KomapperEntity
import org.komapper.annotation.KomapperAutoIncrement
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperTable
import java.time.OffsetDateTime

@KomapperEntity
@KomapperTable("raid_participants")
data class RaidParticipant(
    @KomapperId
    @KomapperAutoIncrement
    val id: Int = 0,
    val raidId: Int,
    val userId: Long,
    val username: String,
    val score: Int = 0,
    val joinedAt: OffsetDateTime? = null
)
