package gg.nikke.raid.bot.controller

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(GuildMemberApiControllerTest.FakeSourceConfig::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuildMemberApiControllerTest(
    @Autowired private val webApplicationContext: WebApplicationContext,
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    class FakeGuildMemberSource : GuildMemberSource {
        override fun findMembers(guildId: Long): Result<List<GuildMemberSnapshot>?> = when (guildId) {
            GUILD_OK -> Result.success(
                listOf(
                    snapshot(300_000_001L, "alice", nickname = "ニック"),
                    snapshot(300_000_002L, "bob", globalName = "Bobby"),
                    snapshot(300_000_003L, "botuser", isBot = true),
                ),
            )
            GUILD_UNAVAILABLE -> Result.failure(RaidDomainError.GuildMembersUnavailable)
            else -> Result.success(null)
        }

        private fun snapshot(
            id: Long,
            username: String,
            globalName: String? = null,
            nickname: String? = null,
            isBot: Boolean = false,
        ) = GuildMemberSnapshot(id, username, globalName, nickname, "https://cdn.example.com/$id.png", isBot)
    }

    @TestConfiguration
    class FakeSourceConfig {
        @Bean
        @Primary
        fun fakeGuildMemberSource(): GuildMemberSource = FakeGuildMemberSource()
    }

    @Test
    fun `GET guilds guildId members - メンバー一覧を取得できる`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].userId").isString)
            .andExpect(jsonPath("$.members[0].displayName").value("Bobby"))
            .andExpect(jsonPath("$.members[1].displayName").value("ニック"))
            .andExpect(jsonPath("$.members[0].avatarUrl").exists())
            .andExpect(jsonPath("$.members[?(@.userId == '300000003')]").doesNotExist())
            .andExpect(jsonPath("$.truncated").value(false))
    }

    @Test
    fun `GET guilds guildId members - queryで絞り込める`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("query", "bobb"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].userId").value("300000002"))
    }

    @Test
    fun `GET guilds guildId members - 空白のみのqueryは絞り込まない`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("query", "   "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(2))
    }

    @Test
    fun `GET guilds guildId members - 前後に空白のあるqueryはtrimして絞り込む`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("query", "  bobb  "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].userId").value("300000002"))
    }

    @Test
    fun `GET guilds guildId members - limitで切られるとtruncatedがtrueになる`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.truncated").value(true))
    }

    @Test
    fun `GET guilds guildId members - ギルドがなければ404`() {
        mockMvc.perform(get("/api/guilds/$GUILD_MISSING/members"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `GET guilds guildId members - 取得不能なら503`() {
        mockMvc.perform(get("/api/guilds/$GUILD_UNAVAILABLE/members"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
    }

    @Test
    fun `GET guilds guildId members - guildIdが数値でなければ400`() {
        mockMvc.perform(get("/api/guilds/abc/members"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `GET guilds guildId members - limitが0なら400`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("limit", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `GET guilds guildId members - limitが101なら400`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("limit", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `GET guilds guildId members - limitが数値でなければ400`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("limit", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `GET guilds guildId members - queryが101文字なら400`() {
        mockMvc.perform(get("/api/guilds/$GUILD_OK/members").param("query", "a".repeat(101)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    companion object {
        private const val GUILD_OK = 70_094_001L
        private const val GUILD_MISSING = 70_094_002L
        private const val GUILD_UNAVAILABLE = 70_094_003L
    }
}
