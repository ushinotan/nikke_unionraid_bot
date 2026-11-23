package gg.nikke.raid.bot.bot

import gg.nikke.raid.bot.config.AppConfig
import gg.nikke.raid.bot.repository.GuildRepository
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.service.Difficulty
import gg.nikke.raid.bot.service.RaidDomainService
import gg.nikke.raid.bot.service.ThreeTurnCancelResult
import gg.nikke.raid.bot.service.ThreeTurnReportResult
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Answers
import org.mockito.Mockito.*

/**
 * QA #92 U-01..U-05: 選択メニュー（3T報告・取り消し）の返答の分岐。
 * RaidDomainService は Result を返すため Mockito でスタブできない（value class）ので、リポジトリをモックにした
 * 匿名サブクラス（kotlin-spring で open）で差し替える。JDA のイベント・アクションは Mockito のモック。DB・Discord 不要。
 *
 * Divergence: plan は「報告を受け付けました」「既に報告済み」等の文言を想定。実装の文言は
 * "報告を受け付けました。" / "<難易度>は既に報告済みでした。報告時刻を更新しました。" /
 * 取り消し後に "参加者からも外れました" 付き / 汎用エラー "エラーが発生しました。時間をおいて再度お試しください。"。
 */
class UnionRaidEventListenerQaTest {

    private val scheduler = mock(RaidNotificationScheduler::class.java)
    private val jda = mock(JDA::class.java)
    private val raidId = 42

    private fun service(
        report: () -> Result<ThreeTurnReportResult> = { error("unused") },
        cancel: () -> Result<ThreeTurnCancelResult> = { error("unused") },
    ): RaidDomainService =
        object : RaidDomainService(
            mock(UnionRaidRepository::class.java),
            mock(RaidReportRepository::class.java),
            mock(RaidParticipantRepository::class.java),
        ) {
            override fun reportThreeTurn(
                raidId: Int, userId: Long, username: String, difficulty: Difficulty, now: java.time.OffsetDateTime,
                allowFinished: Boolean,
            ) = report()

            override fun cancelThreeTurnReports(
                raidId: Int, userId: Long, difficulties: Set<Difficulty>, allowFinished: Boolean,
            ) = cancel()
        }

    private fun listener(service: RaidDomainService) = UnionRaidEventListener(
        service,
        mock(GuildRepository::class.java),
        mock(UnionRaidRepository::class.java),
        scheduler,
        mock(AppConfig::class.java),
    )

    /** 送信されたメッセージ本文を返す。 */
    private fun dispatch(componentIdPrefix: String, value: String, l: UnionRaidEventListener): String {
        val event = mock(StringSelectInteractionEvent::class.java)
        val user = mock(User::class.java)
        `when`(user.idLong).thenReturn(1001L)
        `when`(user.name).thenReturn("tester")
        `when`(event.user).thenReturn(user)
        `when`(event.componentId).thenReturn("$componentIdPrefix$raidId")
        `when`(event.values).thenReturn(listOf(value))
        `when`(event.jda).thenReturn(jda)
        val action = mock(MessageEditCallbackAction::class.java, Answers.RETURNS_SELF)
        `when`(event.editMessage(anyString())).thenReturn(action)

        l.onStringSelectInteraction(event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(event).editMessage(captor.capture())
        verify(action).queue()
        return captor.value
    }

    private fun report(l: UnionRaidEventListener, value: String = "normal") = dispatch("difficulty_select_", value, l)
    private fun cancel(l: UnionRaidEventListener, value: String = "normal") = dispatch("cancel_report_select_", value, l)

    @Test
    fun `U-01 新規の報告は受け付け応答になりレイドメッセージを更新する`() {
        val msg = report(listener(service(report = { Result.success(ThreeTurnReportResult(false, true)) })))

        assertEquals("報告を受け付けました。", msg)
        verify(scheduler).updateRaidMessage(jda, raidId)
    }

    @Test
    fun `U-02 同じ難易度の再報告は既に報告済みで時刻を更新した旨の応答になる`() {
        val msg = report(
            listener(service(report = { Result.success(ThreeTurnReportResult(true, false)) })),
            value = "hard",
        )

        assertEquals("ハードは既に報告済みでした。報告時刻を更新しました。", msg)
    }

    @Test
    fun `U-03 取り消しで参加者から外れたらスコアも消える旨が応答に付く`() {
        val msg = cancel(listener(service(cancel = {
            Result.success(ThreeTurnCancelResult(setOf(Difficulty.NORMAL), participantRemoved = true))
        })))

        assertTrue(msg.startsWith("ノーマルの報告を取り消しました。"), msg)
        assertTrue(msg.contains("参加者からも外れました"), msg)
        assertTrue(msg.contains("スコアも削除"), msg)
        verify(scheduler).updateRaidMessage(jda, raidId)
    }

    @Test
    fun `U-04 取り消し後も報告が残るなら参加者から外れた旨は付かない`() {
        val msg = cancel(listener(service(cancel = {
            Result.success(ThreeTurnCancelResult(setOf(Difficulty.NORMAL), participantRemoved = false))
        })))

        assertEquals("ノーマルの報告を取り消しました。", msg.trim())
        assertFalse(msg.contains("参加者"), msg)
    }

    @Test
    fun `U-05 サービスが想定外の例外を投げると汎用エラー文になり例外メッセージは出ない`() {
        val secret = "jdbc:postgresql://secret-host SQL leak"
        val expected = "エラーが発生しました。時間をおいて再度お試しください。"

        val reportMsg = report(listener(service(report = { throw IllegalStateException(secret) })))
        val cancelMsg = cancel(listener(service(cancel = { throw IllegalStateException(secret) })))

        assertEquals(expected, reportMsg)
        assertEquals(expected, cancelMsg)
        verifyNoInteractions(scheduler)
    }
}
