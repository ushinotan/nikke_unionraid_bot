package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.UnionRaid
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.query.firstOrNull
import org.komapper.jdbc.JdbcDatabase
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

@Repository
class UnionRaidRepository (
    private val database: JdbcDatabase,
): KomapperMeta() {

    /**
     * 指定されたギルドIDに基づき、現在有効なユニオンレイド情報を検索します。
     * 進行中判定は `finished_at IS NULL` です。
     *
     * @param guildId 検索対象のギルドのID
     * @return 指定された条件に一致する有効な `UnionRaid`。存在しない場合は `null`。
     */
    fun findActiveUnionRaidByGuildId(guildId: Long): UnionRaid? {
        val query = QueryDsl.from(unionRaidTable)
            .where {
                unionRaidTable.guildId eq guildId
                unionRaidTable.finishedAt.isNull()
            }
            .firstOrNull()
        return database.runQuery(query)
    }

    /**
     * 指定されたレイドIDのユニオンレイドを取得します。
     *
     * @param raidId 検索対象のレイドID
     * @return 一致する `UnionRaid`。存在しない場合は `null`。
     */
    fun findUnionRaidById(raidId: Int): UnionRaid? {
        val query = QueryDsl.from(unionRaidTable)
            .where {
                unionRaidTable.id eq raidId
            }
            .firstOrNull()
        return database.runQuery(query)
    }

    /**
     * 通知時刻が設定されており、まだ終了していないユニオンレイド一覧を取得します。
     * 終了予定時刻を過ぎたレイドは開始通知の対象外とします。
     *
     * @param now 現在時刻として使用するオフセット日時。デフォルトは `OffsetDateTime.now()`。
     * @return 通知対象の `UnionRaid` 一覧。存在しない場合は空のリスト。
     */
    fun findNotifiableActiveUnionRaids(
        now: OffsetDateTime = OffsetDateTime.now(),
    ): List<UnionRaid> {
        val query = QueryDsl.from(unionRaidTable)
            .where {
                unionRaidTable.notifyTime.isNotNull()
                unionRaidTable.finishedAt.isNull()
                unionRaidTable.endTime greater now
            }

        return database.runQuery(query)
    }

    /**
     * まだ終了処理されていないユニオンレイド一覧を取得します。
     * 自動終了スケジュールの再開・キャッチアップに使用します。
     *
     * @return 未終了の `UnionRaid` 一覧。存在しない場合は空のリスト。
     */
    fun findUnfinishedUnionRaids(): List<UnionRaid> {
        val query = QueryDsl.from(unionRaidTable)
            .where {
                unionRaidTable.finishedAt.isNull()
            }

        return database.runQuery(query)
    }

    /**
     * ユニオンレイドを作成します。
     *
     * @param unionRaid 作成するユニオンレイド
     * @return 作成されたユニオンレイド。自動採番された `id` を含みます。
     */
    fun insertUnionRaid(unionRaid: UnionRaid): UnionRaid {
        val query = QueryDsl.insert(unionRaidTable)
            .single(unionRaid)

        return database.runQuery(query)
    }

    /**
     * 通知送信後に、指定されたユニオンレイドの通知時刻をNULLに更新します。
     *
     * @param raidId 更新対象のレイドID
     * @return 更新件数
     */
    fun clearNotifyTime(raidId: Int): Long {
        val query = QueryDsl.update(unionRaidTable)
            .set {
                unionRaidTable.notifyTime eq null
            }
            .where {
                unionRaidTable.id eq raidId
            }

        return database.runQuery(query)
    }

    /**
     * ユニオンレイドを終了します。
     *
     * @param raidId 終了させるレイドID
     * @param now 終了時刻。デフォルトは現在時刻
     * @param ranking ランキング。nullの場合はNULLに更新する
     * @param percentage パーセンテージ。nullの場合はNULLに更新する
     * @return 更新件数（既に終了済みの場合は0）
     */
    fun finishUnionRaid(
        raidId: Int,
        now: OffsetDateTime = OffsetDateTime.now(),
        ranking: Int?,
        percentage: BigDecimal?,
    ): Long {
        val query = QueryDsl.update(unionRaidTable)
            .set {
                unionRaidTable.endTime eq now
                unionRaidTable.notifyTime eq null
                unionRaidTable.ranking eq ranking
                unionRaidTable.percentage eq percentage
                unionRaidTable.finishedAt eq now
            }
            .where {
                unionRaidTable.id eq raidId
                unionRaidTable.finishedAt.isNull()
            }

        return database.runQuery(query)
    }
}
