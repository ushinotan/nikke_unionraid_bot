package gg.nikke.raid.bot.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuildMemberServiceTest {

    private fun snapshot(
        userId: Long,
        username: String = "user$userId",
        globalName: String? = null,
        nickname: String? = null,
        isBot: Boolean = false,
    ) = GuildMemberSnapshot(
        userId = userId,
        username = username,
        globalName = globalName,
        nickname = nickname,
        avatarUrl = "https://cdn.example.com/$userId.png",
        isBot = isBot,
    )

    private fun service(result: Result<List<GuildMemberSnapshot>?>) = GuildMemberService(
        object : GuildMemberSource {
            override fun findMembers(guildId: Long) = result
        },
    )

    private fun service(vararg members: GuildMemberSnapshot) = service(Result.success(members.toList()))

    // ---- findGuildMembers ----

    @Test
    fun `メンバー一覧_ニックネームがあればニックネームを表示名にする`() {
        val page = service(snapshot(1, nickname = "ニック", globalName = "グローバル")).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals("ニック", page.members.single().displayName)
    }

    @Test
    fun `メンバー一覧_ニックネームが無ければグローバル名を表示名にする`() {
        val page = service(snapshot(1, globalName = "グローバル")).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals("グローバル", page.members.single().displayName)
    }

    @Test
    fun `メンバー一覧_ニックネームもグローバル名も無ければユーザー名を表示名にする`() {
        val page = service(snapshot(1, username = "uname")).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals("uname", page.members.single().displayName)
    }

    @Test
    fun `メンバー一覧_空白だけの値は次の候補に回す`() {
        val page = service(snapshot(1, username = "uname", globalName = "  ", nickname = "")).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals("uname", page.members.single().displayName)
    }

    @Test
    fun `メンバー一覧_botとsystemは除外される`() {
        val page = service(snapshot(1, isBot = true), snapshot(2)).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals(listOf(2L), page.members.map { it.userId })
    }

    @Test
    fun `メンバー一覧_queryはニックネームに部分一致し大文字小文字を無視する`() {
        val page = service(snapshot(1, nickname = "AliceNick"), snapshot(2)).findGuildMembers(1L, "alicen", 100).getOrThrow()

        assertEquals(listOf(1L), page.members.map { it.userId })
    }

    @Test
    fun `メンバー一覧_queryはグローバル名に部分一致する`() {
        val page = service(snapshot(1, globalName = "Bobby", nickname = "x"), snapshot(2)).findGuildMembers(1L, "BOB", 100).getOrThrow()

        assertEquals(listOf(1L), page.members.map { it.userId })
    }

    @Test
    fun `メンバー一覧_queryはユーザー名に部分一致する`() {
        val page = service(snapshot(1, username = "charlie", nickname = "x"), snapshot(2)).findGuildMembers(1L, " harl ", 100).getOrThrow()

        assertEquals(listOf(1L), page.members.map { it.userId })
    }

    @Test
    fun `メンバー一覧_一致なしは空配列を返す`() {
        val page = service(snapshot(1, username = "a")).findGuildMembers(1L, "zzz", 100).getOrThrow()

        assertTrue(page.members.isEmpty())
        assertFalse(page.truncated)
    }

    @Test
    fun `メンバー一覧_limitを超えるとtruncatedがtrueになる`() {
        val page = service(snapshot(1), snapshot(2), snapshot(3)).findGuildMembers(1L, null, 2).getOrThrow()

        assertEquals(2, page.members.size)
        assertTrue(page.truncated)
    }

    @Test
    fun `メンバー一覧_ちょうどlimitならtruncatedはfalse`() {
        val page = service(snapshot(1), snapshot(2)).findGuildMembers(1L, null, 2).getOrThrow()

        assertEquals(2, page.members.size)
        assertFalse(page.truncated)
    }

    @Test
    fun `メンバー一覧_表示名順で同名はuserId順に並ぶ`() {
        val page = service(
            snapshot(3, nickname = "b"),
            snapshot(2, nickname = "a"),
            snapshot(1, nickname = "b"),
        ).findGuildMembers(1L, null, 100).getOrThrow()

        assertEquals(listOf(2L, 1L, 3L), page.members.map { it.userId })
    }

    @Test
    fun `メンバー一覧_ソースがnullならGuildNotFoundで失敗する`() {
        val result = service(Result.success(null)).findGuildMembers(1L, null, 100)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.GuildNotFound::class.java, result.exceptionOrNull())
    }

    @Test
    fun `メンバー一覧_ソースが失敗ならGuildMembersUnavailableで失敗する`() {
        val result = service(Result.failure(RaidDomainError.GuildMembersUnavailable)).findGuildMembers(1L, null, 100)

        assertTrue(result.isFailure)
        assertInstanceOf(RaidDomainError.GuildMembersUnavailable::class.java, result.exceptionOrNull())
    }
}
