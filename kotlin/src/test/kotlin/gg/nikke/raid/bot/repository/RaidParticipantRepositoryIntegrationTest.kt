package gg.nikke.raid.bot.repository

import gg.nikke.raid.bot.entity.Guild
import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.UnionRaid
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
open class RaidParticipantRepositoryIntegrationTest(
    private val guildRepository: GuildRepository,
    private val unionRaidRepository: UnionRaidRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
) {

    @Test
    fun `レイドIDで参加者一覧を取得できる`() {
        val guildId = 70_000_001L
        val now = OffsetDateTime.now()
        val raid = createRaid(guildId, now)

        // 3人の参加者を登録（スコアが異なる）
        insertParticipant(raid.id, 80_000_001L, "user-1", 1500000, now)
        insertParticipant(raid.id, 80_000_002L, "user-2", 1200000, now)
        insertParticipant(raid.id, 80_000_003L, "user-3", 1800000, now)

        val participants = raidParticipantRepository.findParticipantsByRaidId(raid.id)

        assertEquals(3, participants.size)
        assertTrue(participants.any { it.userId == 80_000_001L })
        assertTrue(participants.any { it.userId == 80_000_002L })
        assertTrue(participants.any { it.userId == 80_000_003L })
    }

    @Test
    fun `参加者一覧がスコア降順でソートされる`() {
        val guildId = 70_000_002L
        val now = OffsetDateTime.now()
        val raid = createRaid(guildId, now)

        // スコアが異なる参加者を順不同で登録
        insertParticipant(raid.id, 80_000_004L, "user-4", 1200000, now)
        insertParticipant(raid.id, 80_000_005L, "user-5", 1800000, now)
        insertParticipant(raid.id, 80_000_006L, "user-6", 1500000, now)

        val participants = raidParticipantRepository.findParticipantsByRaidId(raid.id)

        assertEquals(3, participants.size)
        // 最高スコアが先頭
        assertEquals(80_000_005L, participants[0].userId)
        assertEquals(1800000, participants[0].score)
        // 2番目
        assertEquals(80_000_006L, participants[1].userId)
        assertEquals(1500000, participants[1].score)
        // 3番目
        assertEquals(80_000_004L, participants[2].userId)
        assertEquals(1200000, participants[2].score)
    }

    @Test
    fun `異なるレイドIDの参加者は取得されない`() {
        val guildId1 = 70_000_003L
        val guildId2 = 70_000_004L
        val now = OffsetDateTime.now()
        val raid1 = createRaid(guildId1, now)
        val raid2 = createRaid(guildId2, now)

        // raid1の参加者
        insertParticipant(raid1.id, 80_000_007L, "raid1-user", 1000000, now)
        // raid2の参加者
        insertParticipant(raid2.id, 80_000_008L, "raid2-user", 2000000, now)

        val raid1Participants = raidParticipantRepository.findParticipantsByRaidId(raid1.id)
        val raid2Participants = raidParticipantRepository.findParticipantsByRaidId(raid2.id)

        assertEquals(1, raid1Participants.size)
        assertEquals(1, raid2Participants.size)
        assertEquals(80_000_007L, raid1Participants[0].userId)
        assertEquals(80_000_008L, raid2Participants[0].userId)
    }

    @Test
    fun `参加者がいないレイドは空リストを返す`() {
        val guildId = 70_000_005L
        val now = OffsetDateTime.now()
        val raid = createRaid(guildId, now)

        val participants = raidParticipantRepository.findParticipantsByRaidId(raid.id)

        assertTrue(participants.isEmpty())
    }

    private fun createRaid(guildId: Long, now: OffsetDateTime): UnionRaid {
        guildRepository.insertGuild(
            Guild(
                guildId = guildId,
                createdAt = now,
            )
        )

        return unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = "test-participant-raid-$guildId",
                startTime = now.minusHours(1),
                endTime = now.plusHours(1),
                channelId = 90_000_000L + guildId,
                createdAt = now,
            )
        )
    }

    private fun insertParticipant(
        raidId: Int,
        userId: Long,
        username: String,
        score: Int,
        now: OffsetDateTime
    ) {
        raidParticipantRepository.insertOrUpdateParticipant(
            RaidParticipant(
                raidId = raidId,
                userId = userId,
                username = username,
                score = score,
                joinedAt = now,
            )
        )
    }
}
