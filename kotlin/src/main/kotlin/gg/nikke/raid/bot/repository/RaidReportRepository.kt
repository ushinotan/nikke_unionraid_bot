package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.RaidReport
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.operator.desc
import org.komapper.core.dsl.query.firstOrNull
import org.komapper.jdbc.JdbcDatabase
import org.springframework.stereotype.Repository

/**
 * `RaidReport` エンティティに対する永続化処理を提供するリポジトリ。
 *
 * Komapper の DSL を使用して、ユニオンレイド報告データの検索・登録を行う。
 *
 * @property database Komapper の JDBC データベース接続
 */
@Repository
class RaidReportRepository(
    private val database: JdbcDatabase,
): KomapperMeta() {

    /**
     * 指定したレイド・ユーザー・難易度に一致するレイド報告を1件取得する。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @param difficulty 難易度（例: `normal`, `hard` など）
     * @return 一致する `RaidReport`。存在しない場合は `null`
     */
    fun findRaidReport(raidId: Int, userId: Long, difficulty: String): RaidReport? {
        val query = QueryDsl.from(raidReportTable)
            .where {
                raidReportTable.raidId eq raidId
                raidReportTable.userId eq userId
                raidReportTable.difficulty eq difficulty
            }
            .firstOrNull()
        return database.runQuery(query)
    }

    /**
     * 指定したレイド ID に一致する全てのレイド報告を取得する。
     * 報告日時の降順（最新が先頭）、同一日時の場合はIDの昇順でソートされる。
     *
     * @param raidId レイド ID
     * @return 指定したレイド ID に一致する `RaidReport` のリスト。存在しない場合は空のリスト
     */
    fun findAllRaidReportsByRaidId(raidId: Int): List<RaidReport> {
        val query = QueryDsl.from(raidReportTable)
            .where {
                raidReportTable.raidId eq raidId
            }
            .orderBy(raidReportTable.reportedAt.desc())
            .orderBy(raidReportTable.id)

        return database.runQuery(query)
    }

    /**
     * 指定したレイド ID に一致するレイド報告を取得する。
     * 3凸済み レポートのみを対象とする。
     *
     * @param raidId レイド ID
     * @return 指定したレイド ID に一致する `RaidReport` のリスト。存在しない場合は空のリスト
     */
    fun findRaidReportsByRaidId(raidId: Int): List<RaidReport> {
        val query = QueryDsl.from(raidReportTable)
            .where {
                raidReportTable.raidId eq raidId
                raidReportTable.is3t eq 1
            }

        return database.runQuery(query)
    }

    /**
     * 指定したレイド・ユーザーの3凸済み報告（`is3t = 1`）を全て取得する。
     * 集計用の [findRaidReportsByRaidId] と同じ条件で、報告が複数難易度にまたがる場合は難易度の昇順で返す。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @return 該当する `RaidReport` のリスト。存在しない場合は空のリスト
     */
    fun findRaidReportsByRaidIdAndUserId(raidId: Int, userId: Long): List<RaidReport> {
        val query = QueryDsl.from(raidReportTable)
            .where {
                raidReportTable.raidId eq raidId
                raidReportTable.userId eq userId
                raidReportTable.is3t eq 1
            }
            .orderBy(raidReportTable.difficulty)

        return database.runQuery(query)
    }

    /**
     * 指定したレイド・ユーザーの3凸報告を、指定した難易度分だけ削除する。
     *
     * @param raidId レイド ID
     * @param userId ユーザー ID
     * @param difficulties 削除対象の難易度（例: `normal`, `hard`）。空の場合は何も削除しない
     * @return 削除された件数
     */
    fun deleteRaidReports(raidId: Int, userId: Long, difficulties: Collection<String>): Long {
        if (difficulties.isEmpty()) return 0L

        val query = QueryDsl.delete(raidReportTable)
            .where {
                raidReportTable.raidId eq raidId
                raidReportTable.userId eq userId
                raidReportTable.difficulty inList difficulties.toList()
            }

        return database.runQuery(query)
    }

    /**
     * レイド報告を挿入する。既に同じレイド ID、ユーザー ID、難易度の報告が存在する場合は、その報告を更新する。
     *
     * @param raidReport 挿入または更新する `RaidReport` オブジェクト
     * @return 挿入または更新されたレポートのID
     */
    fun saveRaidReport(raidReport: RaidReport): Long {
        val report = raidReport.copy(is3t = 1)

        val query = QueryDsl.insert(raidReportTable)
            .onDuplicateKeyUpdate(raidReportTable.raidId, raidReportTable.userId, raidReportTable.difficulty) {
                raidReportTable.username eq report.username
                raidReportTable.is3t eq report.is3t
                raidReportTable.reportedAt eq report.reportedAt
            }
            .single(report)

        return database.runQuery(query)
    }
}
