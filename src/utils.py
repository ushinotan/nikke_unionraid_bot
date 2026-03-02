import os
from datetime import datetime, timedelta, timezone

# デフォルトタイムゾーン設定（環境変数 DEFAULT_TIMEZONE_HOURS で変更可能、デフォルト: JST）
DEFAULT_TIMEZONE_OFFSET = int(os.getenv('DEFAULT_TIMEZONE_HOURS', '9'))
DEFAULT_TIMEZONE = timezone(timedelta(hours=DEFAULT_TIMEZONE_OFFSET))

def utcnow_aware() -> datetime:
    """UTCの現在時刻（aware）"""
    return datetime.now(timezone.utc)

def localnow_aware() -> datetime:
    """デフォルトタイムゾーンでの現在時刻（aware）"""
    return datetime.now(DEFAULT_TIMEZONE)