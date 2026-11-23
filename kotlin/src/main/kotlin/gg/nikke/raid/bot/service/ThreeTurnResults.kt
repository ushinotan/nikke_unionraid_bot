package gg.nikke.raid.bot.service

/** 3凸報告の結果。 */
data class ThreeTurnReportResult(
    /** 同じ難易度を報告済みだった（報告時刻だけ更新した）なら true */
    val alreadyReported: Boolean,
    /** この報告で参加者に自動登録したなら true */
    val participantRegistered: Boolean,
)

/** 3凸報告の取り消し結果。 */
data class ThreeTurnCancelResult(
    /** 実際に取り消された難易度。未報告の難易度だけを指定したときは空 */
    val cancelled: Set<Difficulty>,
    /** 報告が 0 件になり参加者からも削除したなら true */
    val participantRemoved: Boolean,
)

/** 参加取り消しの結果。 */
data class ParticipationCancelResult(
    /** 参加者の行を削除したなら true（参加者でなかった場合は false） */
    val participantRemoved: Boolean,
    /** 削除した 3凸報告の件数 */
    val removedReportCount: Long,
)
