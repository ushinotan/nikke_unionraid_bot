package com.nikke.repository

import com.nikke.bot.entity.guild
import com.nikke.bot.entity.raidReport
import com.nikke.bot.entity.unionRaid
import org.komapper.core.dsl.Meta


abstract class KomapperMeta {
    val guildTable = Meta.guild
    val RaidReportTable = Meta.raidReport
    val UnionRaid = Meta.unionRaid
}