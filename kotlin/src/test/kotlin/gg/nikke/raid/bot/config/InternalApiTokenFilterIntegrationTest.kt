package gg.nikke.raid.bot.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

/**
 * 実際のアプリケーションコンテキストに登録された [InternalApiTokenFilter] 経由で
 * `/api` 配下が保護されていることを確認する。
 * テスト用トークンは `src/test/resources/application.properties` で設定している。
 */
@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class InternalApiTokenFilterIntegrationTest(
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

    @Test
    fun `トークン無しの api リクエストは401`() {
        mockMvc.perform(get("/api/guilds"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
    }

    @Test
    fun `誤ったトークンの api リクエストは401`() {
        mockMvc.perform(get("/api/guilds").header(InternalApiTokenFilter.HEADER_NAME, "wrong"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `正しいトークンの api リクエストは200`() {
        mockMvc.perform(get("/api/guilds").header(InternalApiTokenFilter.HEADER_NAME, "test-internal-token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `health はトークン無しでも200`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
    }
}
