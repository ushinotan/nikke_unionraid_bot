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
     *
     * @param guildId 検索対象のギルドのID
     * @param now 現在時刻として使用するオフセット日時。デフォルトは `OffsetDateTime.now()`。
     * @return 指定された条件に一致する有効な `UnionRaid`。存在しない場合は `null`。
     */
    fun findActiveUnionRaidByGuildId(
        guildId: Long,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): UnionRaid? {
        val query = QueryDsl.from(unionRaidTable)
            .where {
                unionRaidTable.guildId eq guildId
                unionRaidTable.endTime greater now
            }
            .firstOrNull()
        return database.runQuery(query)
    }

    /**
     * 通知時刻が設定されており、まだ終了していないユニオンレイド一覧を取得します。
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
                unionRaidTable.endTime greater now
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
     * @return 更新件数
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
            }
            .where {
                unionRaidTable.id eq raidId
            }

        return database.runQuery(query)
    }
}