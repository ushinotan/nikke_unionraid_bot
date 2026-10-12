package gg.nikke.raid.bot.entity

import org.komapper.annotation.KomapperEntity
import org.komapper.annotation.KomapperAutoIncrement
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperTable
import java.time.OffsetDateTime
import java.math.BigDecimal

@KomapperEntity
@KomapperTable("union_raids")
data class UnionRaid(
    @KomapperId
    @KomapperAutoIncrement
    val id: Int = 0,
    val guildId: Long,
    val raidName: String,
    val startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    val notifyTime: OffsetDateTime? = null,
    val channelId: Long,
    val ranking: Int? = null,
    val percentage: BigDecimal? = null,
    val finishedAt: OffsetDateTime? = null,
    /** 通知メッセージ(報告ボタン/Embed)の Discord message ID。未送信の場合は null */
    val notifyMessageId: Long? = null,
    val createdAt: OffsetDateTime? = null
)
