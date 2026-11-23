package gg.nikke.raid.bot.service

enum class Difficulty(val value: String, val label: String) {
    NORMAL("normal", "ノーマル"),
    HARD("hard", "ハード");

    companion object {
        fun fromValue(value: String): Difficulty =
            entries.first { it.value == value }

        /** [value] に一致する難易度を返す。一致しなければ `null`（大文字小文字は区別する）。 */
        fun fromValueOrNull(value: String): Difficulty? =
            entries.firstOrNull { it.value == value }
    }
}
