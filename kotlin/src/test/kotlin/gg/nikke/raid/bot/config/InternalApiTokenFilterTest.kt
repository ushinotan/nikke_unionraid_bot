package gg.nikke.raid.bot.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InternalApiTokenFilterTest {

    private fun filter(token: String): InternalApiTokenFilter =
        InternalApiTokenFilter(AppConfig().apply { internalApiToken = token })

    private fun execute(
        filter: InternalApiTokenFilter,
        path: String,
        tokenHeader: String? = null,
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest("GET", path).apply { servletPath = path }
        if (tokenHeader != null) {
            request.addHeader(InternalApiTokenFilter.HEADER_NAME, tokenHeader)
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `トークンが一致する場合は通過する`() {
        val (response, chain) = execute(filter("secret"), "/api/guilds", "secret")

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }

    @Test
    fun `ヘッダーが無い場合は401になる`() {
        val (response, chain) = execute(filter("secret"), "/api/guilds")

        assertEquals(401, response.status)
        assertNull(chain.request)
        assertTrue(response.contentAsString.contains("UNAUTHORIZED"))
    }

    @Test
    fun `トークンが一致しない場合は401になる`() {
        val (response, chain) = execute(filter("secret"), "/api/raids/1", "wrong")

        assertEquals(401, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `トークンの前方一致や長さ違いも401になる`() {
        val f = filter("secret")

        assertEquals(401, execute(f, "/api/guilds", "secre").first.status)
        assertEquals(401, execute(f, "/api/guilds", "secret-extra").first.status)
        assertEquals(401, execute(f, "/api/guilds", "").first.status)
    }

    @Test
    fun `サーバー側トークン未設定の場合は全リクエストを拒否する`() {
        val f = filter("")

        assertEquals(401, execute(f, "/api/guilds", "").first.status)
        assertEquals(401, execute(f, "/api/guilds", "anything").first.status)
        assertEquals(401, execute(f, "/api/guilds").first.status)
    }

    @Test
    fun `api配下以外のパスは認証対象外`() {
        val (response, chain) = execute(filter("secret"), "/health")

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }

    @Test
    fun `パスパラメータやエンコード付きのパスでもapi配下として扱う`() {
        val f = filter("secret")

        // Tomcat がデコード・正規化した後の値が servletPath に入る想定
        listOf(
            "/api;x/guilds" to "/api/guilds", // パスパラメータ
            "/%61pi/guilds" to "/api/guilds", // パーセントエンコーディング
            "//api/guilds" to "/api/guilds", // 重複スラッシュ
        ).forEach { (rawUri, servletPath) ->
            val request = MockHttpServletRequest("GET", rawUri).apply { this.servletPath = servletPath }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()

            f.doFilter(request, response, chain)

            assertEquals(401, response.status, "rawUri=$rawUri")
            assertNull(chain.request, "rawUri=$rawUri")
        }
    }

    @Test
    fun `pathInfo がある場合も servletPath と合わせて判定する`() {
        val request = MockHttpServletRequest("GET", "/api/guilds").apply {
            servletPath = "/api"
            pathInfo = "/guilds"
        }
        val response = MockHttpServletResponse()

        filter("secret").doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
    }

    @Test
    fun `api配下のパスのみ対象で類似パスは対象外`() {
        val f = filter("secret")

        assertEquals(401, execute(f, "/api/guilds/1/raids").first.status)
        assertEquals(200, execute(f, "/apix").first.status)
    }
}
