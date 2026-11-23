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
     *
     * **レイド行のロックを取ったトランザクションの中で呼ぶこと**（`RaidDomainService` 経由）。外から呼ぶと `IllegalStateException`。
     */
    fun insertOrUpdateParticipant(participant: RaidParticipant): Long {
        requireActiveTransaction("insertOrUpdateParticipant")
        val query = QueryDsl.insert(raidParticipantTable)
            .onDuplicateKeyUpdate(raidParticipantTable.raidId, raidParticipantTable.userId) {
                raidParticipantTable.username eq participant.username
                raidParticipantTable.score eq participant.score
                // joined_at は更新しない（最初の参加時刻を保持）
            }
            .single(participant)

        return database.runQuery(query)
    }

    /**
     * レイド参加者を登録する。既に同じレイド ID とユーザー ID の参加者が存在する場合は何も変更しない
     * （`ON CONFLICT (raid_id, user_id) DO NOTHING`）。既存の `username`・`score`・`joined_at` は上書きしない。
     *
     * @param participant 登録する `RaidParticipant`。`joinedAt` には登録時刻を渡すこと
     * @return 挿入された行数（新規なら 1、既に存在して何もしなかったなら 0）
     *
     * **レイド行のロックを取ったトランザクションの中で呼ぶこと**（`RaidDomainService` 経由）。外から呼ぶと `IllegalStateException`。
     */
    fun insertParticipantIfAbsent(participant: RaidParticipant): Long {
        requireActiveTransaction("insertParticipantIfAbsent")
        val query = QueryDsl.insert(raidParticipantTable)
            .onDuplicateKeyIgnore(raidParticipantTable.raidId, raidParticipantTable.userId)
            .single(participant)

        return database.runQuery(query)
    }

    /**
     * 指定したレイド・ユーザーの参加者を削除する。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @return 削除された行数（参加者でなければ 0）
     *
     * **レイド行のロックを取ったトランザクションの中で呼ぶこと**（`RaidDomainService` 経由）。外から呼ぶと `IllegalStateException`。
     */
    fun deleteParticipant(raidId: Int, userId: Long): Long {
        requireActiveTransaction("deleteParticipant")
        val query = QueryDsl.delete(raidParticipantTable)
            .where {
                raidParticipantTable.raidId eq raidId
                raidParticipantTable.userId eq userId
            }

        return database.runQuery(query)
    }

    /**
     * 指定したレイド・ユーザーの参加者を 1 件取得する。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @return 参加者。参加者でなければ `null`
     */
    fun findParticipantByRaidIdAndUserId(raidId: Int, userId: Long): RaidParticipant? {
        val query = QueryDsl.from(raidParticipantTable)
            .where {
                raidParticipantTable.raidId eq raidId
                raidParticipantTable.userId eq userId
            }

        return database.runQuery(query).firstOrNull()
    }

    /**
     * 指定したレイド・ユーザーの参加者のスコアだけを更新する。`username` と `joined_at` は変更しない。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @param score 新しいスコア（0 以上。呼び出し側で検証済みであること）
     * @return 更新された行数（参加者でなければ 0）
     *
     * **レイド行のロックを取ったトランザクションの中で呼ぶこと**（`RaidDomainService` 経由）。外から呼ぶと `IllegalStateException`。
     */
    fun updateParticipantScore(raidId: Int, userId: Long, score: Int): Long {
        requireActiveTransaction("updateParticipantScore")
        val query = QueryDsl.update(raidParticipantTable)
            .set {
                raidParticipantTable.score eq score
            }
            .where {
                raidParticipantTable.raidId eq raidId
                raidParticipantTable.userId eq userId
            }

        return database.runQuery(query)
    }
}
