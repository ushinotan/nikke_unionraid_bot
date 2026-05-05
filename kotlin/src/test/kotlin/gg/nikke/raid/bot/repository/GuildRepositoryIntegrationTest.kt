package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.Guild
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class GuildRepositoryIntegrationTest(
    private val guildRepository: GuildRepository,
) {

    @Test
    fun `guildを作成して取得できる`() {
        val guildId = 10_000_001L

        guildRepository.deleteGuildById(guildId)

        val inserted = guildRepository.insertGuild(
            Guild(
                guildId = guildId,
                createdAt = OffsetDateTime.now(),
            )
        )

        val found = guildRepository.findGuildById(guildId)

        assertEquals(1L, inserted)
        assertNotNull(found)
        assertEquals(guildId, found?.guildId)

        guildRepository.deleteGuildById(guildId)
    }

    @Test
    fun `同じguildIdを再登録しても重複作成されない`() {
        val guildId = 10_000_002L

        guildRepository.deleteGuildById(guildId)

        val guild = Guild(
            guildId = guildId,
            createdAt = OffsetDateTime.now(),
        )

        val firstInsert = guildRepository.insertGuild(guild)
        val secondInsert = guildRepository.insertGuild(guild)

        assertEquals(1L, firstInsert)
        assertEquals(0L, secondInsert)

        guildRepository.deleteGuildById(guildId)
    }

    @Test
    fun `guildを削除できる`() {
        val guildId = 10_000_003L

        guildRepository.deleteGuildById(guildId)

        guildRepository.insertGuild(
            Guild(
                guildId = guildId,
                createdAt = OffsetDateTime.now(),
            )
        )

        val deleted = guildRepository.deleteGuildById(guildId)
        val found = guildRepository.findGuildById(guildId)

        assertEquals(1L, deleted)
        assertNull(found)
    }
}