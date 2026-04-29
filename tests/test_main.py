import importlib
import logging
import sys
import types
from unittest.mock import AsyncMock

import pytest


def load_main_module(monkeypatch, *, init_db_mock=None, bot_ctor=None):
    monkeypatch.setattr(logging, "FileHandler", lambda *args, **kwargs: logging.NullHandler())

    fake_database = types.ModuleType("database")
    fake_database.init_db = init_db_mock or AsyncMock()

    fake_bot = types.ModuleType("bot")

    class _DefaultBot:
        def __init__(self):
            self.closed = True

        async def start(self, _token):
            return None

        def is_closed(self):
            return self.closed

        async def close(self):
            self.closed = True

    fake_bot.NikkeUnionRaidBot = bot_ctor or _DefaultBot

    monkeypatch.setitem(sys.modules, "database", fake_database)
    monkeypatch.setitem(sys.modules, "bot", fake_bot)

    if "main" in sys.modules:
        del sys.modules["main"]
    return importlib.import_module("main"), fake_database.init_db


@pytest.mark.asyncio
async def test_main_returns_when_token_missing(monkeypatch):
    """DISCORD_TOKEN未設定時はDB初期化を行わず早期リターンすることを確認する。"""
    main, init_db_mock = load_main_module(monkeypatch)
    monkeypatch.delenv("DISCORD_TOKEN", raising=False)

    await main.main()

    init_db_mock.assert_not_awaited()


@pytest.mark.asyncio
async def test_main_returns_when_db_init_fails(monkeypatch):
    """DB初期化が失敗した場合はBotを生成せず処理を終了することを確認する。"""
    monkeypatch.setenv("DISCORD_TOKEN", "dummy-token")
    init_db_mock = AsyncMock(side_effect=RuntimeError("db init failed"))

    bot_ctor_called = False

    class DummyBot:
        pass

    def _bot_ctor():
        nonlocal bot_ctor_called
        bot_ctor_called = True
        return DummyBot()

    main, imported_init_db = load_main_module(monkeypatch, init_db_mock=init_db_mock, bot_ctor=_bot_ctor)

    await main.main()

    imported_init_db.assert_awaited_once()
    assert bot_ctor_called is False


@pytest.mark.asyncio
async def test_main_closes_bot_on_start_error(monkeypatch):
    """Bot起動中に例外が発生した場合でもcloseが呼ばれることを確認する。"""
    monkeypatch.setenv("DISCORD_TOKEN", "dummy-token")
    init_db_mock = AsyncMock()

    class DummyBot:
        def __init__(self):
            self.closed = False
            self.started_with = None
            self.close_called = False

        async def start(self, token):
            self.started_with = token
            raise RuntimeError("start failed")

        def is_closed(self):
            return self.closed

        async def close(self):
            self.close_called = True
            self.closed = True

    bot = DummyBot()
    main, imported_init_db = load_main_module(monkeypatch, init_db_mock=init_db_mock, bot_ctor=lambda: bot)

    await main.main()

    imported_init_db.assert_awaited_once()
    assert bot.started_with == "dummy-token"
    assert bot.close_called is True
