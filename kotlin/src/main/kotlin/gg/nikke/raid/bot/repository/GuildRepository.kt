package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.Guild
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.query.firstOrNull
import org.komapper.jdbc.JdbcDatabase
import org.springframework.stereotype.Repository

@Repository
class GuildRepository(
    private val database: JdbcDatabase,
): KomapperMeta() {

    /**
     * GuildIdに基づいてGuild情報を取得
     *
     * @param guildId
     * @return Guild情報
     */
    fun findGuildById(guildId: Long): Guild? {
        val query = QueryDsl.from(guildTable)
            .where { guildTable.guildId eq guildId }.firstOrNull()
        return database.runQuery(query)
    }

    /**
     * ディスコードのGuild情報を保存
     *
     * @param guild Guild情報
     * @return 挿入された行数。既に同じGuildIdの情報が存在する場合は0を返す
     */
    fun insertGuild(guild: Guild): Long {
        val query = QueryDsl.insert(guildTable)
            .onDuplicateKeyIgnore()
            .single(guild)
        return database.runQuery(query)
    }

    /**
     * ディスコードのGuild情報をGuildIdに基づいて削除
     *
     * @param guildId
     * @return 削除された行数
     */
    fun deleteGuildById(guildId: Long): Long {
        val query = QueryDsl.delete(guildTable)
            .where { guildTable.guildId eq guildId }
        return database.runQuery(query)
    }
}
