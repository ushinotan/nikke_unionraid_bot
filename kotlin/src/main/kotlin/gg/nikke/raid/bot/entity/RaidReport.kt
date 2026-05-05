package gg.nikke.raid.bot.entity

import org.komapper.annotation.KomapperEntity
import org.komapper.annotation.KomapperAutoIncrement
import org.komapper.annotation.KomapperColumn
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperTable
import java.time.OffsetDateTime

@KomapperEntity
@KomapperTable("raid_reports")
data class RaidReport(
    @KomapperId
    @KomapperAutoIncrement
    val id: Int = 0,
    val raidId: Int,
    val userId: Long,
    val username: String,
    val difficulty: String,  // 'normal' or 'hard'
    @KomapperColumn("is_3t")
    val is3t: Int = 0,  // 1 = true, 0 = false
    val reportedAt: OffsetDateTime? = null
)
