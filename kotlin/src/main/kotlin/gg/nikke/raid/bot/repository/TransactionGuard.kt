package gg.nikke.raid.bot.repository

import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 参加者・報告への書き込みが、レイド行ロック（`RaidDomainService` の `@Transactional`）の外から呼ばれていないか確認する。
 * トランザクションの外では `SELECT ... FOR UPDATE` のロックが文の終わりで外れ、排他の役に立たない。
 *
 * @throws IllegalStateException トランザクションが開始されていない場合
 */
internal fun requireActiveTransaction(operation: String) {
    check(TransactionSynchronizationManager.isActualTransactionActive()) {
        "$operation はトランザクション内で呼ぶ必要がある（RaidDomainService 経由でレイド行をロックしてから書き込むこと）"
    }
}
