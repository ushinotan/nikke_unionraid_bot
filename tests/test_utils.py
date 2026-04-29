from datetime import datetime, timedelta, timezone
import importlib

import utils as utils_module
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


def test_default_timezone_hours_defaults_to_9(monkeypatch):
    """DEFAULT_TIMEZONE_HOURS環境変数が未設定の場合、デフォルト値が9（JST）であることを確認する。"""
    with monkeypatch.context() as m:
        m.delenv('DEFAULT_TIMEZONE_HOURS', raising=False)
        reloaded = importlib.reload(utils_module)

        assert reloaded.DEFAULT_TIMEZONE_OFFSET == 9
        assert reloaded.DEFAULT_TIMEZONE == timezone(timedelta(hours=9))

    importlib.reload(utils_module)


def test_default_timezone_hours_can_be_overridden(monkeypatch):
    """DEFAULT_TIMEZONE_HOURS環境変数を設定すると、タイムゾーンが変わることを確認する。"""
    with monkeypatch.context() as m:
        m.setenv('DEFAULT_TIMEZONE_HOURS', '0')
        reloaded = importlib.reload(utils_module)

        assert reloaded.DEFAULT_TIMEZONE_OFFSET == 0
        assert reloaded.DEFAULT_TIMEZONE == timezone(timedelta(hours=0))

    importlib.reload(utils_module)
