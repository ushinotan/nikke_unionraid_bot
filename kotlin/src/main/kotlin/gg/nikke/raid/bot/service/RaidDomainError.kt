package gg.nikke.raid.bot.service

import java.math.BigDecimal

sealed class RaidDomainError(message: String) : Exception(message) {
    data object DurationTooShort : RaidDomainError("レイド期間は1時間以上にしてください")
    data object ActiveRaidAlreadyExists : RaidDomainError("進行中のレイドが既に存在します")
    data object RankingAndPercentageBothSpecified : RaidDomainError("順位とパーセンテージは同時に指定できません")
    data class RankingOutOfRange(val ranking: Int) : RaidDomainError("順位は1以上で指定してください: $ranking")
    data class PercentageOutOfRange(val percentage: BigDecimal) : RaidDomainError("パーセンテージは0より大きく100以下にしてください: $percentage")
}
