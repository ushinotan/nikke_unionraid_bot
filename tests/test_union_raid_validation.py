import importlib
from datetime import datetime, timezone
import sys
import types
from unittest.mock import AsyncMock, Mock

import discord
import pytest


class _Expr:
    def __and__(self, _other):
        return self


class _Column:
    def __eq__(self, _other):
        return _Expr()

    def __gt__(self, _other):
        return _Expr()

    def isnot(self, _other):
        return _Expr()


class _Query:
    def where(self, *_args, **_kwargs):
        return self

    def values(self, **_kwargs):
        return self


class _Table:
    def select(self):
        return _Query()

    def update(self):
        return _Query()


class _SessionContextManager:
    def __init__(self, session):
        self.session = session

    async def __aenter__(self):
        return self.session

    async def __aexit__(self, exc_type, exc, tb):
        return False


class _SessionFactory:
    def __init__(self, session):
        self.session = session

    def __call__(self):
        return _SessionContextManager(self.session)


def _load_union_raid_module(monkeypatch, session_factory=None):
    fake_database = types.ModuleType("database")

    if session_factory is None:
        class _NoDbSessionFactory:
            def __call__(self):
                raise AssertionError("DB session factory should not be called in validation tests")

        fake_database.async_session_factory = _NoDbSessionFactory()
    else:
        fake_database.async_session_factory = session_factory

    fake_models = types.ModuleType("models")

    class Guild:
        __table__ = _Table()

    class UnionRaid:
        __table__ = _Table()
        notify_time = _Column()
        end_time = _Column()
        guild_id = _Column()
        id = _Column()

    class RaidReport:
        __table__ = _Table()
        raid_id = _Column()
        user_id = _Column()
        difficulty = _Column()
        is_3t = _Column()
        id = _Column()

    fake_models.Guild = Guild
    fake_models.UnionRaid = UnionRaid
    fake_models.RaidReport = RaidReport

    monkeypatch.setitem(sys.modules, "database", fake_database)
    monkeypatch.setitem(sys.modules, "models", fake_models)
    if "cogs.union_raid" in sys.modules:
        del sys.modules["cogs.union_raid"]

    return importlib.import_module("cogs.union_raid")


def _make_interaction():
    response = types.SimpleNamespace(
        send_message=AsyncMock(),
        defer=AsyncMock(),
    )
    followup = types.SimpleNamespace(send=AsyncMock())
    return types.SimpleNamespace(response=response, followup=followup, guild_id=123)


class _DummyBot:
    def add_view(self, _view):
        return None


@pytest.mark.asyncio
async def test_raid_create_rejects_duration_less_than_one(monkeypatch):
    """レイド期間が1時間未満なら即時にエラーメッセージを返すことを確認する。"""
    union_raid = _load_union_raid_module(monkeypatch)
    cog = union_raid.UnionRaidCog(_DummyBot())
    interaction = _make_interaction()

    await union_raid.UnionRaidCog.raid_create.callback(cog, interaction, 0)

    interaction.response.send_message.assert_awaited_once_with(
        "レイドの期間は1時間以上で指定してください。",
        ephemeral=True,
    )


@pytest.mark.asyncio
async def test_raid_end_rejects_rank_and_percentage_together(monkeypatch):
    """順位とパーセンテージを同時指定した場合に入力エラーとなることを確認する。"""
    union_raid = _load_union_raid_module(monkeypatch)
    cog = union_raid.UnionRaidCog(_DummyBot())
    interaction = _make_interaction()

    await union_raid.UnionRaidCog.raid_end.callback(cog, interaction, 1, 5.0)

    interaction.response.defer.assert_awaited_once_with(ephemeral=False)
    interaction.followup.send.assert_awaited_once_with(
        "順位かパーセンテージのどちらか一方のみ指定してください。",
        ephemeral=True,
    )


@pytest.mark.asyncio
async def test_raid_end_rejects_rank_less_than_one(monkeypatch):
    """順位が1未満の場合に入力エラーとなることを確認する。"""
    union_raid = _load_union_raid_module(monkeypatch)
    cog = union_raid.UnionRaidCog(_DummyBot())
    interaction = _make_interaction()

    await union_raid.UnionRaidCog.raid_end.callback(cog, interaction, 0, None)

    interaction.followup.send.assert_awaited_once_with(
        "順位は1以上で指定してください。",
        ephemeral=True,
    )


@pytest.mark.asyncio
async def test_raid_end_rejects_percentage_out_of_range(monkeypatch):
    """パーセンテージが範囲外の場合に入力エラーとなることを確認する。"""
    union_raid = _load_union_raid_module(monkeypatch)
    cog = union_raid.UnionRaidCog(_DummyBot())
    interaction = _make_interaction()

    await union_raid.UnionRaidCog.raid_end.callback(cog, interaction, None, 100.1)

    interaction.followup.send.assert_awaited_once_with(
        "パーセンテージは0より大きく100以下で指定してください。",
        ephemeral=True,
    )


@pytest.mark.asyncio
async def test_resume_schedules_registers_future_and_past_notify(monkeypatch):
    """通知時刻が未来/過去のレイドをそれぞれスケジュール対象にすることを確認する。"""
    now = datetime(2026, 4, 29, 12, 0, tzinfo=timezone.utc)

    rows = [
        types.SimpleNamespace(
            _mapping={
                "id": 1,
                "guild_id": 100,
                "raid_name": "A",
                "start_time": now,
                "end_time": now,
                "notify_time": now.replace(hour=13),
                "channel_id": 10,
            }
        ),
        types.SimpleNamespace(
            _mapping={
                "id": 2,
                "guild_id": 200,
                "raid_name": "B",
                "start_time": now,
                "end_time": now,
                "notify_time": now.replace(hour=11),
                "channel_id": 20,
            }
        ),
    ]

    session = types.SimpleNamespace(
        execute=AsyncMock(return_value=types.SimpleNamespace(fetchall=lambda: rows)),
        commit=AsyncMock(),
    )
    union_raid = _load_union_raid_module(monkeypatch, session_factory=_SessionFactory(session))
    monkeypatch.setattr(union_raid, "utcnow_aware", lambda: now)

    cog = union_raid.UnionRaidCog(_DummyBot())
    schedule_mock = Mock()
    monkeypatch.setattr(cog, "_schedule_notification_task", schedule_mock)

    await cog.resume_schedules()

    assert schedule_mock.call_count == 2
    first_raid = schedule_mock.call_args_list[0].args[0]
    second_raid = schedule_mock.call_args_list[1].args[0]
    assert first_raid.id == 1
    assert second_raid.id == 2
    assert second_raid.notify_time == now


@pytest.mark.asyncio
async def test_update_raid_message_reflects_normal_and_hard_users(monkeypatch):
    """報告一覧をもとにノーマル/ハードの埋め込み表示が更新されることを確認する。"""
    reports = [
        types.SimpleNamespace(_mapping={"username": "Alice", "difficulty": "normal"}),
        types.SimpleNamespace(_mapping={"username": "Bob", "difficulty": "hard"}),
    ]
    session = types.SimpleNamespace(
        execute=AsyncMock(return_value=types.SimpleNamespace(fetchall=lambda: reports)),
        commit=AsyncMock(),
    )
    union_raid = _load_union_raid_module(monkeypatch, session_factory=_SessionFactory(session))

    cog = union_raid.UnionRaidCog(_DummyBot())
    embed = discord.Embed(title="通知")
    embed.add_field(name="ノーマル 3凸", value="なし", inline=False)
    embed.add_field(name="ハード 3凸", value="なし", inline=False)
    message = types.SimpleNamespace(embeds=[embed], edit=AsyncMock())
    cog._raid_notify_messages[999] = message

    await cog._update_raid_message(999)

    assert embed.fields[0].value == "Alice"
    assert embed.fields[1].value == "Bob"
    message.edit.assert_awaited_once()


@pytest.mark.asyncio
async def test_schedule_notification_task_sends_and_clears_state(monkeypatch):
    """通知タスク実行でメッセージ送信・DB更新・タスク管理辞書削除が行われることを確認する。"""
    now = datetime(2026, 4, 29, 12, 0, tzinfo=timezone.utc)
    session = types.SimpleNamespace(
        execute=AsyncMock(return_value=types.SimpleNamespace(fetchall=lambda: [])),
        commit=AsyncMock(),
    )
    union_raid = _load_union_raid_module(monkeypatch, session_factory=_SessionFactory(session))
    monkeypatch.setattr(union_raid, "utcnow_aware", lambda: now)

    message = types.SimpleNamespace(embeds=[])
    channel = types.SimpleNamespace(send=AsyncMock(return_value=message))

    class BotWithChannel(_DummyBot):
        def __init__(self):
            self.get_channel = lambda _cid: channel
            self.fetch_channel = AsyncMock()

    cog = union_raid.UnionRaidCog(BotWithChannel())
    raid = union_raid.RaidInfo(
        id=777,
        guild_id=333,
        raid_name="ユニオンレイド",
        start_time=now,
        end_time=now,
        notify_time=now,
        channel_id=444,
    )

    cog._schedule_notification_task(raid)
    task = cog._scheduled_tasks[333]
    await task

    channel.send.assert_awaited_once()
    session.execute.assert_awaited_once()
    session.commit.assert_awaited_once()
    assert 333 not in cog._scheduled_tasks
    assert cog._raid_notify_messages[777] is message
