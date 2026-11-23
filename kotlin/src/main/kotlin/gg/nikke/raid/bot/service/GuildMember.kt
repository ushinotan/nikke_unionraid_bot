package gg.nikke.raid.bot.service

/**
 * メンバー一覧 API 用のメンバー情報
 */
data class GuildMember(
    val userId: Long,
    val displayName: String,
    val avatarUrl: String,
)

/**
 * メンバー一覧の結果。件数上限で切られた場合 [truncated] が true になる
 */
data class GuildMemberPage(
    val members: List<GuildMember>,
    val truncated: Boolean,
)
