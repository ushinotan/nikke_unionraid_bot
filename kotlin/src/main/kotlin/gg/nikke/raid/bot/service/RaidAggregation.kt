package gg.nikke.raid.bot.service

import java.math.BigDecimal

/**
 * レイドの集計結果を表すデータクラス。
 *
 * ギルド内で実施されたレイドの難易度ごとの参加報告数および参加割合を保持します。
 *
 * @property normalCount 通常難易度の参加報告数
 * @property hardCount 高難易度の参加報告数
 * @property normalPercentage 通常難易度の参加割合（%）
 * @property hardPercentage 高難易度の参加割合（%）
 */
data class RaidAggregation(
    val normalCount: Int,
    val hardCount: Int,
    val normalPercentage: BigDecimal,
    val hardPercentage: BigDecimal,
)
