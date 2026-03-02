import os
from datetime import datetime, timedelta, timezone

# デフォルトタイムゾーン設定（環境変数 DEFAULT_TIMEZONE で変更可能、デフォルト: JST）
DEFAULT_TIMEZONE_OFFSET = int(os.getenv('DEFAULT_TIMEZONE_HOURS', '9'))
DEFAULT_TIMEZONE = timezone(timedelta(hours=DEFAULT_TIMEZONE_OFFSET))

# ヘルパー: タイムゾーン情報付きの現在UTCと、DB保存用のナイーブUTCを返す
def utcnow_aware() -> datetime:
    return datetime.now(timezone.utc)

def localnow_aware() -> datetime:
    """デフォルトタイムゾーンでの現在時刻（aware）"""
    return datetime.now(DEFAULT_TIMEZONE)