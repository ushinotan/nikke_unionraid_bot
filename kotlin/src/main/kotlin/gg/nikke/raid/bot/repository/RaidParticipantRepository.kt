package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.RaidParticipant
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.operator.desc
import org.komapper.jdbc.JdbcDatabase
import org.springframework.stereotype.Repository

/**
 * `RaidParticipant` エンティティに対する永続化処理を提供するリポジトリ。
 *
 * Komapper の DSL を使用して、レイド参加者データの検索を行う。
 *
 * @property database Komapper の JDBC データベース接続
 */
@Repository
class RaidParticipantRepository(
    private val database: JdbcDatabase,
): KomapperMeta() {

    /**
     * 指定したレイド ID に一致する参加者リストを取得する。
     * スコアの降順でソートされる。
     *
     * @param raidId レイド ID
     * @return 指定したレイド ID に一致する `RaidParticipant` のリスト。存在しない場合は空のリスト
     */
    fun findParticipantsByRaidId(raidId: Int): List<RaidParticipant> {
        val query = QueryDsl.from(raidParticipantTable)
            .where {
                raidParticipantTable.raidId eq raidId
            }
            .orderBy(raidParticipantTable.score.desc())

        return database.runQuery(query)
    }

    /**
     * レイド参加者を登録する。既に同じレイド ID とユーザー ID の参加者が存在する場合は、その参加者のスコアを更新する。
     * 更新時は joined_at は元の値を保持する（履歴保持のため）。
     *
     * **注意**: このメソッドは読み取り専用 API では使用されません。
     * 将来の書き込みエンドポイント実装時、またはテストデータのセットアップ用に用意されています。
     *
     * @param participant 登録または更新する `RaidParticipant` オブジェクト
     * @return 挿入または更新された行数
     */
    fun insertOrUpdateParticipant(participant: RaidParticipant): Long {
        val query = QueryDsl.insert(raidParticipantTable)
            .onDuplicateKeyUpdate(raidParticipantTable.raidId, raidParticipantTable.userId) {
                raidParticipantTable.username eq participant.username
                raidParticipantTable.score eq participant.score
                // joined_at は更新しない（最初の参加時刻を保持）
            }
            .single(participant)

        return database.runQuery(query)
    }
}
