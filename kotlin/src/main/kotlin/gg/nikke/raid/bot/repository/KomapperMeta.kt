package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.guild
import gg.nikke.raid.bot.entity.raidReport
import gg.nikke.raid.bot.entity.unionRaid
import org.komapper.core.dsl.Meta

abstract class KomapperMeta {
    val guildTable = Meta.guild
    val raidReportTable = Meta.raidReport
    val unionRaidTable = Meta.unionRaid
}