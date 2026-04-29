from datetime import datetime, timedelta, timezone

from utils import DEFAULT_TIMEZONE, ensure_utc_aware, localnow_aware, utcnow_aware


def test_ensure_utc_aware_with_naive_datetime():
    """naiveなdatetimeがUTC awareとして扱われることを確認する。"""
    dt = datetime(2026, 4, 1, 12, 0, 0)

    converted = ensure_utc_aware(dt)

    assert converted.tzinfo == timezone.utc
    assert converted.hour == 12


def test_ensure_utc_aware_with_aware_datetime():
    """awareなdatetimeがUTCへ正しく変換されることを確認する。"""
    jst = timezone(timedelta(hours=9))
    dt = datetime(2026, 4, 1, 12, 0, 0, tzinfo=jst)

    converted = ensure_utc_aware(dt)

    assert converted.tzinfo == timezone.utc
    assert converted.hour == 3


def test_utcnow_aware_returns_utc_datetime():
    """utcnow_awareがUTC awareな現在時刻を返すことを確認する。"""
    current = utcnow_aware()

    assert current.tzinfo == timezone.utc


def test_localnow_aware_uses_default_timezone():
    """localnow_awareがデフォルトタイムゾーンのaware時刻を返すことを確認する。"""
    current = localnow_aware()

    assert current.tzinfo == DEFAULT_TIMEZONE
