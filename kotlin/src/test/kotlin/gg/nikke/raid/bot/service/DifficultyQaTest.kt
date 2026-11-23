package gg.nikke.raid.bot.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** QA #93 U-07: 難易度の文字列変換（例外を投げない版）。DB・Spring 不要。 */
class DifficultyQaTest {
    @Test
    fun `U-07 fromValueOrNullは正しい値で難易度を返し不明な値ではnullを返す`() {
        assertEquals(Difficulty.NORMAL, Difficulty.fromValueOrNull("normal"))
        assertEquals(Difficulty.HARD, Difficulty.fromValueOrNull("hard"))
        // 大文字小文字は区別する・空文字・前後空白・未知の値は null（例外を投げない）
        assertNull(Difficulty.fromValueOrNull("HARD"))
        assertNull(Difficulty.fromValueOrNull("Normal"))
        assertNull(Difficulty.fromValueOrNull(""))
        assertNull(Difficulty.fromValueOrNull(" hard"))
        assertNull(Difficulty.fromValueOrNull("extreme"))
    }
}
