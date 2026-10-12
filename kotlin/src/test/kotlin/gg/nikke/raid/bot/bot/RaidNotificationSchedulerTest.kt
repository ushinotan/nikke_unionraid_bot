package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.service.RaidDomainService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.function.Consumer
import kotlin.test.assertEquals

/**
 * 通知メッセージを DB の message ID から再取得して更新/ボタン無効化できること(Bot 再起動後も動作すること)の検証。
 * JDA と各リポジトリは Mockito のモックを使うため、DB や Discord への接続は不要。
 */
class RaidNotificationSchedulerTest {

    private val raidId = 10
    private val channelId = 456L
    private val messageId = 123L

    private val unionRaidRepository = mock(UnionRaidRepository::class.java)
    private val raidReportRepository = mock(RaidReportRepository::class.java)
    private val raidDomainService = mock(RaidDomainService::class.java)
    private val jda = mock(JDA::class.java)

    private lateinit var scheduler: RaidNotificationScheduler

    @BeforeEach
    fun setup() {
        scheduler = RaidNotificationScheduler(unionRaidRepository, raidReportRepository, raidDomainService)
    }

    @AfterEach
    fun tearDown() {
        scheduler.shutdown()
    }

    private fun raid(notifyMessageId: Long?): UnionRaid {
        val now = OffsetDateTime.now()
        return UnionRaid(
            id = raidId,
            guildId = 1L,
            raidName = "ユニオンレイド",
            startTime = now.minusHours(1),
            endTime = now.plusHours(1),
            channelId = channelId,
            notifyMessageId = notifyMessageId,
        )
    }

    private fun report(userId: Long, username: String, difficulty: String) = RaidReport(
        raidId = raidId,
        userId = userId,
        username = username,
        difficulty = difficulty,
        is3t = 1,
    )

    /** channelId のチャンネルが解決でき、messageId の取得が成功して [message] を返す状態にする。 */
    private fun stubRetrieveMessage(message: Message) {
        val channel = mock(TextChannel::class.java)
        @Suppress("UNCHECKED_CAST")
        val restAction = mock(RestAction::class.java) as RestAction<Message>

        `when`(jda.getTextChannelById(channelId)).thenReturn(channel)
        `when`(channel.retrieveMessageById(messageId)).thenReturn(restAction)
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Consumer<Message>).accept(message)
            null
        }.`when`(restAction).queue(
            ArgumentMatchers.any<Consumer<in Message>>(),
            ArgumentMatchers.any<Consumer<in Throwable>>(),
        )
    }

    private fun notifyEmbed(): MessageEmbed = EmbedBuilder()
        .setTitle("📣 ユニオンレイド通知")
        .setDescription("人間、ユニオンレイドが始まったわ。")
        .addField("レイドID", "`$raidId`", true)
        .addField("ノーマル 3凸", "なし", false)
        .addField("ハード 3凸", "なし", false)
        .build()

    @Test
    fun `updateRaidMessage_保存済みmessageIdから取得したEmbedを最新の報告で更新する`() {
        val message = mock(Message::class.java)
        val editAction = mock(MessageEditAction::class.java)
        `when`(message.embeds).thenReturn(listOf(notifyEmbed()))
        `when`(message.editMessageEmbeds(ArgumentMatchers.any(MessageEmbed::class.java))).thenReturn(editAction)
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = messageId))
        `when`(raidReportRepository.findRaidReportsByRaidId(raidId)).thenReturn(
            listOf(
                report(1L, "alice", "normal"),
                report(2L, "bob", "hard"),
                report(3L, "carol", "normal"),
            ),
        )
        stubRetrieveMessage(message)

        scheduler.updateRaidMessage(jda, raidId)

        val captor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        verify(message).editMessageEmbeds(captor.capture())
        val fields = captor.value.fields.associate { it.name to it.value }
        assertEquals("alice\ncarol", fields["ノーマル 3凸"])
        assertEquals("bob", fields["ハード 3凸"])
        assertEquals("`$raidId`", fields["レイドID"])
    }

    @Test
    fun `updateRaidMessage_messageIdが未保存ならDiscordに問い合わせず何もしない`() {
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = null))

        scheduler.updateRaidMessage(jda, raidId)

        verifyNoInteractions(jda)
    }

    @Test
    fun `updateRaidMessage_レイドが存在しなければ何もしない`() {
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(null)

        scheduler.updateRaidMessage(jda, raidId)

        verifyNoInteractions(jda)
    }

    @Test
    fun `updateRaidMessage_チャンネルを解決できなければメッセージ取得を試みない`() {
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = messageId))
        `when`(jda.getTextChannelById(channelId)).thenReturn(null)
        `when`(jda.guilds).thenReturn(emptyList())

        scheduler.updateRaidMessage(jda, raidId)

        verify(raidReportRepository, never()).findRaidReportsByRaidId(raidId)
    }

    @Test
    fun `disableReportButton_保存済みmessageIdから取得したメッセージのボタンを外す`() {
        val message = mock(Message::class.java)
        val editAction = mock(MessageEditAction::class.java)
        `when`(message.editMessageComponents()).thenReturn(editAction)
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = messageId))
        stubRetrieveMessage(message)

        scheduler.disableReportButton(jda, raidId)

        verify(message).editMessageComponents()
        verify(editAction).queue(
            ArgumentMatchers.any<Consumer<in Message>>(),
            ArgumentMatchers.any<Consumer<in Throwable>>(),
        )
    }

    @Test
    fun `disableReportButton_messageIdが未保存ならDiscordに問い合わせず何もしない`() {
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = null))

        scheduler.disableReportButton(jda, raidId)

        verifyNoInteractions(jda)
    }

    @Test
    fun `cleanupAfterRaidEnd_保存済みmessageIdがあればボタンを外す`() {
        val message = mock(Message::class.java)
        val editAction = mock(MessageEditAction::class.java)
        `when`(message.editMessageComponents()).thenReturn(editAction)
        `when`(unionRaidRepository.findUnionRaidById(raidId)).thenReturn(raid(notifyMessageId = messageId))
        stubRetrieveMessage(message)

        scheduler.cleanupAfterRaidEnd(jda, guildId = 1L, raidId = raidId)

        verify(message).editMessageComponents()
    }
}
