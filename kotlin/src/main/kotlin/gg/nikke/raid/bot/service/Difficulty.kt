package gg.nikke.raid.bot.service

enum class Difficulty(val value: String, val label: String) {
    NORMAL("normal", "ノーマル"),
    HARD("hard", "ハード");

    companion object {
        fun fromValue(value: String): Difficulty =
            entries.first { it.value == value }
    }
}
