package gg.nikke.raid.bot.controller

import gg.nikke.raid.bot.config.InternalApiTokenFilter
import gg.nikke.raid.bot.service.GuildMemberSnapshot
import gg.nikke.raid.bot.service.GuildMemberSource
import gg.nikke.raid.bot.service.RaidDomainError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * QA: GET /api/guilds/{guildId}/members (Task #94, plan: docs/initiatives/91/qa-plan-94.md)
 *
 * 実際の InternalApiTokenFilter を通した結合テスト。メンバー供給元のみフェイクに差し替える。
 * DB への書き込みは無いためクリーンアップ不要。
 *
 * Divergence: なし（ステータス・エラーコードは計画どおり。limit は 1..100、query は 100 文字以内）。
 * Note: 開発側の GuildMemberApiControllerTest はフィルター無しで構築しているため、
 *       本クラスではトークン付き/無しの両方を実フィルター経由で確認する。
 */
@SpringBootTest
@Import(GuildMemberApiQaTest.QaSourceConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuildMemberApiQaTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
    @Autowired private val internalApiTokenFilter: InternalApiTokenFilter,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .addFilters<DefaultMockMvcBuilder>(internalApiTokenFilter)
            .build()
    }

    class QaSource : GuildMemberSource {
        override fun findMembers(guildId: Long): Result<List<GuildMemberSnapshot>?> = when (guildId) {
            GUILD_OK -> Result.success(
                listOf(
                    snap(1, "zed_user", nickname = "Nick", globalName = "GlobalOne"),
                    snap(2, "yuki", globalName = "GlobalTwo"),
                    snap(3, "plain_name"),
                    snap(4, "self_bot", isBot = true),
                    snap(5, "system_user", isBot = true),
                ),
            )
            GUILD_MANY -> Result.success((1L..5L).map { snap(it, "m$it") })
            GUILD_UNAVAILABLE -> Result.failure(RaidDomainError.GuildMembersUnavailable)
            else -> Result.success(null)
        }

        private fun snap(
            id: Long,
            username: String,
            globalName: String? = null,
            nickname: String? = null,
            isBot: Boolean = false,
        ) = GuildMemberSnapshot(id, username, globalName, nickname, "https://cdn.example.com/$id.png", isBot)
    }

    @TestConfiguration
    class QaSourceConfig {
        @Bean
        @Primary
        fun qaGuildMemberSource(): GuildMemberSource = QaSource()
    }

    private fun members(guildId: Long) =
        get("/api/guilds/$guildId/members").header(InternalApiTokenFilter.HEADER_NAME, TOKEN)

    @Test
    fun `I-01 正常系 userId は文字列で displayName avatarUrl truncated を返す`() {
        mockMvc.perform(members(GUILD_OK))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(3))
            .andExpect(jsonPath("$.members[*].userId").isArray)
            .andExpect(jsonPath("$.members[0].userId").isString)
            .andExpect(jsonPath("$.members[0].displayName").isString)
            .andExpect(jsonPath("$.members[0].avatarUrl").value(org.hamcrest.Matchers.startsWith("https://")))
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `I-02 bot と system ユーザーは結果に含まれない`() {
        mockMvc.perform(members(GUILD_OK))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members[?(@.userId == '4')]").doesNotExist())
            .andExpect(jsonPath("$.members[?(@.userId == '5')]").doesNotExist())
    }

    @Test
    fun `I-03 表示名はニックネーム then グローバル名 then ユーザー名の順で決まる`() {
        mockMvc.perform(members(GUILD_OK))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members[?(@.userId == '1')].displayName").value("Nick"))
            .andExpect(jsonPath("$.members[?(@.userId == '2')].displayName").value("GlobalTwo"))
            .andExpect(jsonPath("$.members[?(@.userId == '3')].displayName").value("plain_name"))
    }

    @Test
    fun `I-04 query は部分一致で大文字小文字を無視する`() {
        mockMvc.perform(members(GUILD_OK).param("query", "GLOBALTW"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].userId").value("2"))
    }

    @Test
    fun `I-05 query に一致するメンバーがいなければ空配列`() {
        mockMvc.perform(members(GUILD_OK).param("query", "no-such-member"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(0))
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `I-06 limit を超えると先頭だけ返り truncated が true`() {
        mockMvc.perform(members(GUILD_MANY).param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].userId").value("1"))
            .andExpect(jsonPath("$.members[1].userId").value("2"))
            .andExpect(jsonPath("$.truncated").value(true))
    }

    @Test
    fun `I-06 limit がちょうど件数なら truncated は false`() {
        mockMvc.perform(members(GUILD_MANY).param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(5))
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `I-07 bot が参加していないギルドは404 NOT_FOUND`() {
        mockMvc.perform(members(GUILD_MISSING))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `I-08 メンバー取得不能は503 SERVICE_UNAVAILABLE`() {
        mockMvc.perform(members(GUILD_UNAVAILABLE))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
    }

    @Test
    fun `I-09 guildId が数値でなければ400 BAD_REQUEST`() {
        mockMvc.perform(get("/api/guilds/abc/members").header(InternalApiTokenFilter.HEADER_NAME, TOKEN))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `I-09 limit が範囲外なら400 BAD_REQUEST`() {
        mockMvc.perform(members(GUILD_OK).param("limit", "-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        mockMvc.perform(members(GUILD_OK).param("limit", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `I-09 limit の境界値 1 と 100 は受理される`() {
        mockMvc.perform(members(GUILD_OK).param("limit", "1")).andExpect(status().isOk)
        mockMvc.perform(members(GUILD_OK).param("limit", "100")).andExpect(status().isOk)
    }

    @Test
    fun `I-09 query が101文字なら400 かつ100文字ちょうどは200`() {
        mockMvc.perform(members(GUILD_OK).param("query", "a".repeat(101)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        mockMvc.perform(members(GUILD_OK).param("query", "a".repeat(100)))
            .andExpect(status().isOk)
    }

    @Test
    fun `I-10 トークン無しは401`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
    }

    @Test
    fun `I-10 トークン不一致は401`() {
        mockMvc.perform(
            get("/api/guilds/$GUILD_OK/members").header(InternalApiTokenFilter.HEADER_NAME, "wrong-token"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `G-01 既存の api guilds はトークン無しで401 トークンありで200`() {
        mockMvc.perform(get("/api/guilds"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/guilds").header(InternalApiTokenFilter.HEADER_NAME, TOKEN))
            .andExpect(status().isOk)
    }

    companion object {
        private const val TOKEN = "test-internal-token"
        private const val GUILD_OK = 70_094_101L
        private const val GUILD_MANY = 70_094_102L
        private const val GUILD_MISSING = 70_094_103L
        private const val GUILD_UNAVAILABLE = 70_094_104L
    }
}
