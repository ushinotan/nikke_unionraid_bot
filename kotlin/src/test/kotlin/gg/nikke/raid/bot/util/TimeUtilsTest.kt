package gg.nikke.raid.bot.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TimeUtilsTest {

    @Test
    fun `utcNow はUTCオフセットを返す`() {
        val result = TimeUtils.utcNow()
        assertEquals(ZoneOffset.UTC, result.offset)
    }

    @Test
    fun `localNow はJSTオフセットを返す`() {
        val result = TimeUtils.localNow(9)
        assertEquals(ZoneOffset.ofHours(9), result.offset)
    }

    @Test
    fun `ensureUtc はUTC入力をそのまま返す`() {
        val utcTime = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val result = TimeUtils.ensureUtc(utcTime)
        assertEquals(ZoneOffset.UTC, result.offset)
        assertEquals(utcTime.toInstant(), result.toInstant())
    }

    @Test
    fun `ensureUtc はJST入力をUTCに変換する`() {
        val jstTime = OffsetDateTime.parse("2024-01-01T21:00:00+09:00")
        val result = TimeUtils.ensureUtc(jstTime)
        assertEquals(ZoneOffset.UTC, result.offset)
        assertEquals(OffsetDateTime.parse("2024-01-01T12:00:00Z").toInstant(), result.toInstant())
    }

    @Test
    fun `secondsUntil は未来の時刻に対して正の値を返す`() {
        val now = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val target = now.plusSeconds(300)
        val result = TimeUtils.secondsUntil(target, now)
        assertEquals(300L, result)
    }

    @Test
    fun `secondsUntil は過去の時刻に対して負の値を返す`() {
        val now = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val target = now.minusSeconds(60)
        val result = TimeUtils.secondsUntil(target, now)
        assertEquals(-60L, result)
    }

    @Test
    fun `secondsUntil は同じ時刻に対してゼロを返す`() {
        val now = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val result = TimeUtils.secondsUntil(now, now)
        assertEquals(0L, result)
    }

    @Test
    fun `secondsUntil はタイムゾーンが異なっても同じ瞬間を正しく扱う`() {
        val utcTime = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val jstTime = OffsetDateTime.parse("2024-01-01T21:00:00+09:00")
        val result = TimeUtils.secondsUntil(utcTime, jstTime)
        assertEquals(0L, result)
    }
}
