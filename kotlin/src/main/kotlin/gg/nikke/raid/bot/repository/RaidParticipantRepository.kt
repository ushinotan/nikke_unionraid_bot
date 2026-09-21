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
}
