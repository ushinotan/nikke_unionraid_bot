package gg.nikke.raid.bot.config

import jakarta.annotation.PostConstruct
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * `/api` 配下に対する内部API認証フィルタ。
 *
 * Next.js BFF から付与される [HEADER_NAME] ヘッダーの値を、
 * 環境変数 `INTERNAL_API_TOKEN` (`app.internal-api-token`) と定数時間で比較する。
 *
 * - 一致しない / ヘッダー無し: 401
 * - サーバー側トークン未設定: fail-closed として全リクエストを 401 で拒否
 * - `/health` など `/api/` 配下以外は対象外
 */
@Component
class InternalApiTokenFilter(
    private val appConfig: AppConfig,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(InternalApiTokenFilter::class.java)

    @PostConstruct
    fun warnIfTokenMissing() {
        if (appConfig.internalApiToken.isBlank()) {
            log.warn("INTERNAL_API_TOKEN が未設定のため、/api/** へのリクエストはすべて拒否されます")
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return !(path == "/api" || path.startsWith("/api/"))
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val expected = appConfig.internalApiToken
        val provided = request.getHeader(HEADER_NAME)

        if (expected.isBlank() || provided == null || !constantTimeEquals(expected, provided)) {
            log.warn("内部API認証に失敗しました: method=${request.method} path=${request.requestURI}")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"error":"UNAUTHORIZED","message":"Authentication required"}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(expected: String, provided: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            provided.toByteArray(StandardCharsets.UTF_8),
        )

    companion object {
        const val HEADER_NAME = "X-Internal-Api-Token"
    }
}
