package gg.nikke.raid.bot.service

enum class Difficulty(val value: String) {
    NORMAL("normal"),
    HARD("hard");

    companion object {
        fun fromValue(value: String): Difficulty =
            entries.first { it.value == value }
    }
}
