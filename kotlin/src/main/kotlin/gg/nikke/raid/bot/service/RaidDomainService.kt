package gg.nikke.raid.bot.service

import gg.nikke.raid.bot.entity.RaidParticipant
import gg.nikke.raid.bot.entity.RaidReport
import gg.nikke.raid.bot.entity.UnionRaid
import gg.nikke.raid.bot.repository.RaidParticipantRepository
import gg.nikke.raid.bot.repository.RaidReportRepository
import gg.nikke.raid.bot.repository.UnionRaidRepository
import gg.nikke.raid.bot.util.TimeUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime

/**
 * レイド管理のドメインサービスクラス。
 * ギルド内のレイド管理、レイド結果の集計、および関連する操作を提供します。
 *
 * このクラスは、他のリポジトリとやり取りする責務を持ち、レイドの作成、完了、報告、および結果の集計を行います。
 */
@Service
class RaidDomainService(
    private val unionRaidRepository: UnionRaidRepository,
    private val raidReportRepository: RaidReportRepository,
    private val raidParticipantRepository: RaidParticipantRepository,
) {

    companion object {
        private val MIN_RAID_DURATION = Duration.ofHours(1)
        private const val MIN_RANKING = 1
        private val MAX_PERCENTAGE = BigDecimal("100")
    }

    /**
     * 新しいユニオンレイドを作成します。
     *
     * @param guildId レイドを作成するギルドのID
     * @param raidName レイドの名前
     * @param startTime レイドの開始時刻
     * @param endTime レイドの終了時刻
     * @param notifyTime レイド通知のための時刻（省略可能）
     * @param channelId 通知やアクションに使用するチャンネルのID
     * @param now 処理時の現在時刻（省略可能、デフォルトは現在のUTC時刻）
     * @return 作成されたユニオンレイドを含む `Result`。失敗した場合はエラー情報を含みます。
     */
    fun createRaid(
        guildId: Long,
        raidName: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime,
        notifyTime: OffsetDateTime? = null,
        channelId: Long,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<UnionRaid> {
        if (Duration.between(startTime, endTime) < MIN_RAID_DURATION) {
            return Result.failure(RaidDomainError.DurationTooShort)
        }

        if (unionRaidRepository.findActiveUnionRaidByGuildId(guildId) != null) {
            return Result.failure(RaidDomainError.ActiveRaidAlreadyExists)
        }

        val raid = unionRaidRepository.insertUnionRaid(
            UnionRaid(
                guildId = guildId,
                raidName = raidName,
                startTime = startTime,
                endTime = endTime,
                notifyTime = notifyTime,
                channelId = channelId,
            )
        )
        return Result.success(raid)
    }

    /**
     * 指定されたレイドを終了します。
     *
     * @param raidId 終了するレイドのID
     * @param ranking 終了時の順位（任意）。1以上で指定。percentageと同時指定不可
     * @param percentage 終了時の上位パーセンテージ（任意）。0より大きく100以下。rankingと同時指定不可
     * @param now 現在時刻（デフォルト値はUTC現在時刻）
     * @return 更新されたレコード数を成功値として返却。失敗時はエラーを含むResultオブジェクトを返却
     *
     * 単一の UPDATE 文のみなのでレイド行のロックは取らない。ここに参加者・報告の更新を足すときは、
     * 先に `lockRaid` でレイド行をロックし、`union_raids` → `raid_reports` → `raid_participants` の順を守ること。
     */
    fun finishRaid(
        raidId: Int,
        ranking: Int? = null,
        percentage: BigDecimal? = null,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<Long> {
        if (ranking != null && percentage != null) {
            return Result.failure(RaidDomainError.RankingAndPercentageBothSpecified)
        }

        if (ranking != null && ranking < MIN_RANKING) {
            return Result.failure(RaidDomainError.RankingOutOfRange(ranking))
        }

        if (percentage != null && (percentage <= BigDecimal.ZERO || percentage > MAX_PERCENTAGE)) {
            return Result.failure(RaidDomainError.PercentageOutOfRange(percentage))
        }

        val updated = unionRaidRepository.finishUnionRaid(raidId, now, ranking, percentage)
        return Result.success(updated)
    }

    /**
     * ユーザーが3ターン撃破を報告したことを記録し、まだ参加者でなければスコア 0 で参加者に自動登録します。
     *
     * レイド行をロックしたトランザクションの中で、報告の保存と参加者の登録を行います。
     * 既に参加者（手動登録など）の場合は `username`・`score`・`joined_at` を変更しません。
     * 参加者・報告への書き込みは、必ずこのサービスでレイド行をロックしてから行うこと。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @param username ユーザー名（参加者の自動登録にも使う）
     * @param difficulty 難易度
     * @param now 報告の時刻（デフォルトは現在のUTC時刻）
     * @param allowFinished `true` なら終了済みレイドでも報告できる。`true` にするのは、終了済みレイドを操作してよいかを
     *   呼び出し側（Web の BFF の権限判定）が確かめた場合だけ。Discord からは渡さない（既定の `false`）
     * @return 報告結果を含む `Result`。レイドが存在しない場合は `RaidNotFound`、`allowFinished` が `false` で終了済みの場合は `RaidAlreadyFinished`
     * @throws IllegalStateException トランザクションの外で呼ばれた場合
     */
    @Transactional(rollbackFor = [Exception::class])
    fun reportThreeTurn(
        raidId: Int,
        userId: Long,
        username: String,
        difficulty: Difficulty,
        now: OffsetDateTime = TimeUtils.utcNow(),
        allowFinished: Boolean = false,
    ): Result<ThreeTurnReportResult> {
        val lock = if (allowFinished) lockRaid(raidId) else lockActiveRaid(raidId)
        lock.onFailure { return Result.failure(it) }

        val alreadyReported = raidReportRepository.findRaidReport(raidId, userId, difficulty.value) != null

        raidReportRepository.saveRaidReport(
            RaidReport(
                raidId = raidId,
                userId = userId,
                username = username,
                difficulty = difficulty.value,
                reportedAt = now,
            )
        )

        val participantRegistered = raidParticipantRepository.insertParticipantIfAbsent(
            RaidParticipant(
                raidId = raidId,
                userId = userId,
                username = username,
                score = 0,
                joinedAt = now,
            )
        ) == 1L

        return Result.success(ThreeTurnReportResult(alreadyReported, participantRegistered))
    }

    /**
     * 指定したレイドが存在し、進行中（未終了）であることを確認する。
     *
     * @param raidId レイドのID
     * @return 進行中のレイドを含む `Result`。存在しない場合は `RaidNotFound`、終了済みの場合は `RaidAlreadyFinished`
     */
    fun ensureRaidActive(raidId: Int): Result<UnionRaid> {
        val raid = unionRaidRepository.findUnionRaidById(raidId)
            ?: return Result.failure(RaidDomainError.RaidNotFound)
        if (raid.finishedAt != null) {
            return Result.failure(RaidDomainError.RaidAlreadyFinished)
        }
        return Result.success(raid)
    }

    /**
     * ユーザーが指定の難易度で既に3凸報告済みかを返す。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @param difficulty 難易度
     * @return 報告済みなら `true`
     */
    fun hasReported(raidId: Int, userId: Long, difficulty: Difficulty): Boolean =
        raidReportRepository.findRaidReport(raidId, userId, difficulty.value) != null

    /**
     * ユーザーが報告済みの難易度を返す（ノーマル、ハードの順）。
     *
     * @param raidId レイドのID
     * @param userId ユーザーのID
     * @return 報告済みの難易度のリスト。報告がなければ空
     */
    fun findReportedDifficulties(raidId: Int, userId: Long): List<Difficulty> {
        val reported = raidReportRepository.findRaidReportsByRaidIdAndUserId(raidId, userId)
            .map { it.difficulty }
            .toSet()
        return Difficulty.entries.filter { it.value in reported }
    }

    /**
     * ユーザー自身の3凸報告を、指定した難易度分だけ取り消します。
     * 指定していない難易度の報告は残ります。終了済みのレイドは取り消せません。
     * 取り消しの結果、3凸報告が 0 件になった場合は参加者も削除します（手動登録・入力済みスコアも消えます）。
     * 何も取り消さなかった場合は参加者を削除しません。
     *
     * @param raidId レイドのID
     * @param userId 取り消すユーザーのID（本人の報告のみ対象）
     * @param difficulties 取り消す難易度。空の場合は `NoDifficultySelected`
     * @param allowFinished `true` なら終了済みレイドでも取り消せる。`true` にするのは、終了済みレイドを操作してよいかを
     *   呼び出し側（Web の BFF の権限判定）が確かめた場合だけ。Discord からは渡さない（既定の `false`）
     * @return 取り消し結果を含む `Result`。レイドが存在しない場合は `RaidNotFound`、`allowFinished` が `false` で終了済みの場合は `RaidAlreadyFinished`
     * @throws IllegalStateException トランザクションの外で呼ばれた場合
     */
    @Transactional(rollbackFor = [Exception::class])
    fun cancelThreeTurnReports(
        raidId: Int,
        userId: Long,
        difficulties: Set<Difficulty>,
        allowFinished: Boolean = false,
    ): Result<ThreeTurnCancelResult> {
        if (difficulties.isEmpty()) {
            return Result.failure(RaidDomainError.NoDifficultySelected)
        }
        val lock = if (allowFinished) lockRaid(raidId) else lockActiveRaid(raidId)
        lock.onFailure { return Result.failure(it) }

        // 別のメニューで先に取り消された場合などに備え、実際に報告済みの難易度だけを対象にする
        val targets = findReportedDifficulties(raidId, userId).filter { it in difficulties }.toSet()
        if (targets.isEmpty()) {
            return Result.success(ThreeTurnCancelResult(emptySet(), participantRemoved = false))
        }

        raidReportRepository.deleteRaidReports(raidId, userId, targets.map { it.value })

        val remaining = raidReportRepository.findRaidReportsByRaidIdAndUserId(raidId, userId)
        val participantRemoved = remaining.isEmpty() &&
            raidParticipantRepository.deleteParticipant(raidId, userId) > 0L

        return Result.success(ThreeTurnCancelResult(targets, participantRemoved))
    }

    /**
     * ユーザーの参加を取り消し、参加者の行とそのユーザーの3凸報告（全難易度）をまとめて削除します。
     *
     * 終了済みレイドかどうかは判定しません。終了済みレイドを操作してよいか（特権ユーザーのみ可など）と、
     * 操作者が本人か特権ユーザーかの確認は、呼び出し側（Web の BFF など）が必ず行うこと。
     * 参加者でも報告者でもない場合も成功（両方 0）を返します（冪等）。
     *
     * @param raidId レイドのID
     * @param userId 参加を取り消すユーザーのID
     * @return 削除結果を含む `Result`。レイドが存在しない場合は `RaidNotFound`
     * @throws IllegalStateException トランザクションの外で呼ばれた場合
     */
    @Transactional(rollbackFor = [Exception::class])
    fun cancelParticipation(raidId: Int, userId: Long): Result<ParticipationCancelResult> {
        lockRaid(raidId).onFailure { return Result.failure(it) }

        val removedReportCount = raidReportRepository.deleteRaidReportsByRaidIdAndUserId(raidId, userId)
        val participantRemoved = raidParticipantRepository.deleteParticipant(raidId, userId) > 0L

        return Result.success(ParticipationCancelResult(participantRemoved, removedReportCount))
    }

    /**
     * 参加者を指定のスコアとユーザー名で登録します（Web からの代理登録・本人登録用）。
     *
     * 終了済みレイドかどうかは判定しません。終了済みレイドを操作してよいか（特権ユーザーのみ可など）と、
     * 操作者が本人か特権ユーザーかの確認は、呼び出し側（Web の BFF など）が必ず行うこと。
     * 入力の検証は呼び出し側で済んでいること（`score` は 0 以上、`username` は空でなく 255 文字以内）。
     *
     * @param raidId レイドのID
     * @param userId 登録するユーザーのID
     * @param username ユーザー名
     * @param score スコア（0 以上。呼び出し側で検証済み）
     * @param now 参加時刻（デフォルトは現在のUTC時刻）
     * @return 登録した参加者を含む `Result`。レイドが存在しない場合は `RaidNotFound`、既に参加者の場合は `ParticipantAlreadyExists`（何も変更しない）
     * @throws IllegalStateException トランザクションの外で呼ばれた場合
     */
    @Transactional(rollbackFor = [Exception::class])
    fun registerParticipant(
        raidId: Int,
        userId: Long,
        username: String,
        score: Int,
        now: OffsetDateTime = TimeUtils.utcNow(),
    ): Result<RaidParticipant> {
        lockRaid(raidId).onFailure { return Result.failure(it) }

        val inserted = raidParticipantRepository.insertParticipantIfAbsent(
            RaidParticipant(
                raidId = raidId,
                userId = userId,
                username = username,
                score = score,
                joinedAt = now,
            )
        )
        if (inserted == 0L) {
            return Result.failure(RaidDomainError.ParticipantAlreadyExists)
        }

        val participant = raidParticipantRepository.findParticipantByRaidIdAndUserId(raidId, userId)
            ?: error("挿入直後の参加者が見つからない")
        return Result.success(participant)
    }

    /**
     * 参加者のスコアだけを更新します。`username` と `joined_at` は変更しません。
     *
     * 終了済みレイドかどうかは判定しません。終了済みレイドを操作してよいか（特権ユーザーのみ可など）と、
     * 操作者が本人か特権ユーザーかの確認は、呼び出し側（Web の BFF など）が必ず行うこと。
     *
     * @param raidId レイドのID
     * @param userId 更新する参加者のユーザーID
     * @param score 新しいスコア（0 以上。呼び出し側で検証済み）
     * @return 更新後の参加者を含む `Result`。レイドが存在しない場合は `RaidNotFound`、参加者でない場合は `ParticipantNotFound`
     * @throws IllegalStateException トランザクションの外で呼ばれた場合
     */
    @Transactional(rollbackFor = [Exception::class])
    fun updateParticipantScore(raidId: Int, userId: Long, score: Int): Result<RaidParticipant> {
        lockRaid(raidId).onFailure { return Result.failure(it) }

        val updated = raidParticipantRepository.updateParticipantScore(raidId, userId, score)
        if (updated == 0L) {
            return Result.failure(RaidDomainError.ParticipantNotFound)
        }

        val participant = raidParticipantRepository.findParticipantByRaidIdAndUserId(raidId, userId)
            ?: error("更新直後の参加者が見つからない")
        return Result.success(participant)
    }

    /**
     * レイド行をロックして返す。無ければ `RaidNotFound`。
     * トランザクションの外ではロックが文の終わりで外れ整合性を守れないため、書き込む前に止める。
     */
    private fun lockRaid(raidId: Int): Result<UnionRaid> {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "参加者・報告の更新はトランザクション内で呼ぶ必要がある"
        }
        val raid = unionRaidRepository.findUnionRaidByIdForUpdate(raidId)
            ?: return Result.failure(RaidDomainError.RaidNotFound)
        return Result.success(raid)
    }

    /** [lockRaid] に加えて、終了済みなら `RaidAlreadyFinished` を返す。 */
    private fun lockActiveRaid(raidId: Int): Result<UnionRaid> {
        val raid = lockRaid(raidId).getOrElse { return Result.failure(it) }
        if (raid.finishedAt != null) {
            return Result.failure(RaidDomainError.RaidAlreadyFinished)
        }
        return Result.success(raid)
    }

    /**
     * 指定されたレイドの3凸報告をnormal/hard別のユーザー名リストに集計する。
     *
     * @param raidId レイド ID
     * @return 難易度ごとの3凸済みユーザー名リストを含む `RaidAggregation`
     */
    fun aggregateRaidResults(raidId: Int): RaidAggregation {
        val reports = raidReportRepository.findRaidReportsByRaidId(raidId)

        return RaidAggregation(
            normalUsers = reports.filter { it.difficulty == Difficulty.NORMAL.value }.map { it.username },
            hardUsers = reports.filter { it.difficulty == Difficulty.HARD.value }.map { it.username },
        )
    }
}
