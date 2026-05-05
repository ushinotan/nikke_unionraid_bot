package gg.nikke.raid.bot.util

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 日付や時刻を操作するためのユーティリティクラス。
 * オフセット日時（OffsetDateTime）を利用して、現在時刻の取得や変換、
 * 時刻間の差分計算をサポートする。
 */
object TimeUtils {
    /**
     * 現在の時刻をUTCタイムゾーン（協定世界時）で返します。
     *
     * @return 現在のUTC時刻を表すOffsetDateTimeオブジェクト
     */
    fun utcNow(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    /**
     * 指定された時間オフセットに基づいて現在の日時を取得します。
     *
     * @param offsetHours 時間オフセットを時間単位で指定します。例えば、日本標準時 (JST) の場合は 9 を指定します。
     * @return 指定された時間オフセットの現在日時を表す OffsetDateTime インスタンス。
     */
    fun localNow(offsetHours: Int): OffsetDateTime =
        OffsetDateTime.now(ZoneOffset.ofHours(offsetHours))

    /**
     * 指定された日時をUTCタイムゾーンに変換します。
     * すでにUTCタイムゾーンの場合はそのまま返します。
     *
     * @param dt 変換対象の日時。任意のタイムゾーンが指定可能。
     * @return UTCタイムゾーンに変換された日時。
     */
    fun ensureUtc(dt: OffsetDateTime): OffsetDateTime =
        dt.withOffsetSameInstant(ZoneOffset.UTC)

    /**
     * 指定された現在時刻から目標時刻までの秒数を計算します。
     *
     * @param target 目標時刻を表すOffsetDateTimeオブジェクト。
     * @param now 現在時刻を表すOffsetDateTimeオブジェクト。デフォルトはUTCの現在時刻。
     * @return 現在時刻から目標時刻までの秒数。未来の目標時刻では正の値、過去の目標時刻では負の値が返されます。
     */
    fun secondsUntil(target: OffsetDateTime, now: OffsetDateTime = utcNow()): Long =
        Duration.between(now, target).seconds
}
