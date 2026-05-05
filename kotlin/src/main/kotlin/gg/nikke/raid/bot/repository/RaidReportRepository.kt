package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.RaidReport
import org.komapper.core.dsl.QueryDsl
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
    fun findRaidReports(raidId: Int, userId: Long, difficulty: String): RaidReport? {
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