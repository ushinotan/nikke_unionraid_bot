package gg.nikke.raid.bot.service

import org.springframework.stereotype.Service
import java.util.Locale

/**
 * ギルドメンバー一覧の検索・整列を行うサービス。
 *
 * リクエストごとにキャッシュ済みの全メンバーを絞り込み・ソートする（インデックスは持たない）。
 * 現在想定するギルド規模では十分な性能であることを前提とする。
 */
@Service
class GuildMemberService(
    private val guildMemberSource: GuildMemberSource,
) {

    /**
     * ギルドのメンバー一覧を取得する。bot / system ユーザーは除外する。
     *
     * リクエストごとに全メンバーを絞り込み・ソートするため、現在のギルド規模を前提とする。
     *
     * @param guildId ギルドID
     * @param query 名前（ニックネーム・グローバル名・ユーザー名）の部分一致検索。null・空なら絞り込まない
     * @param limit 返す最大件数
     * @return 成功時はメンバーのページ。
     *   失敗時: [RaidDomainError.GuildNotFound]（bot がギルドにいない）、
     *   [RaidDomainError.GuildMembersUnavailable]（取得不能）
     */
    fun findGuildMembers(guildId: Long, query: String?, limit: Int): Result<GuildMemberPage> {
        val snapshots = guildMemberSource.findMembers(guildId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(RaidDomainError.GuildNotFound)

        val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        val sorted = snapshots
            .filterNot { it.isBot }
            .filter { normalizedQuery == null || it.matches(normalizedQuery) }
            .map { GuildMember(userId = it.userId, displayName = it.displayName(), avatarUrl = it.avatarUrl) }
            .sortedWith(compareBy({ it.displayName }, { it.userId }))

        return Result.success(
            GuildMemberPage(
                members = sorted.take(limit),
                truncated = sorted.size > limit,
            ),
        )
    }

    private fun GuildMemberSnapshot.displayName(): String =
        listOfNotNull(nickname, globalName).firstOrNull { it.isNotBlank() } ?: username

    private fun GuildMemberSnapshot.matches(lowerQuery: String): Boolean =
        listOfNotNull(nickname, globalName, username).any { it.lowercase(Locale.ROOT).contains(lowerQuery) }
}
