package gg.nikke.raid.bot.service

/**
 * ギルドのメンバーを取得するポート。
 * JDA への依存は bot パッケージの実装に閉じ込める。
 */
interface GuildMemberSource {
    /**
     * ギルドのメンバーを返す。
     *
     * @param guildId ギルドID
     * @return 成功時はメンバー一覧。bot がそのギルドにいなければ `null`。
     *   取得できなければ [RaidDomainError.GuildMembersUnavailable] の失敗
     */
    fun findMembers(guildId: Long): Result<List<GuildMemberSnapshot>?>
}

/**
 * ソースから得た生のメンバー情報
 */
data class GuildMemberSnapshot(
    val userId: Long,
    val username: String,
    val globalName: String?,
    val nickname: String?,
    val avatarUrl: String,
    val isBot: Boolean,
)
