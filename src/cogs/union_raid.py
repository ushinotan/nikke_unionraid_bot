import logging
import asyncio
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timedelta, timezone
import discord
from discord import app_commands
from discord.ext import commands
from discord import ui
from discord.ui import Modal, TextInput, View, Select, Button
from typing import Dict, Optional
from database import async_session_factory
from models import Guild, UnionRaid, RaidReport
from utils import utcnow_aware, localnow_aware, DEFAULT_TIMEZONE, ensure_utc_aware
from sqlalchemy.dialects.postgresql import insert as pg_insert

logger = logging.getLogger(__name__)

PERCENTAGE_QUANT = Decimal("0.01")


def to_percentage_decimal(value: Optional[float]) -> Optional[Decimal]:
    """コマンド入力のfloatをDB保存用Decimal(小数第2位)へ正規化する。"""
    if value is None:
        return None
    return Decimal(str(value)).quantize(PERCENTAGE_QUANT, rounding=ROUND_HALF_UP)

@dataclass
class RaidInfo:
    id: int
    guild_id: int
    raid_name: str
    start_time: datetime
    end_time: datetime
    notify_time: Optional[datetime]
    channel_id: int

class ReportView(View):
    def __init__(self, cog: "UnionRaidCog"):
        super().__init__(timeout=None)
        self.cog = cog

    @ui.button(label="報告", style=discord.ButtonStyle.primary, custom_id="raid_report")
    async def report_button(self, interaction: discord.Interaction, button: Button):
        try:
            # embed から raid_id を取得
            embed = interaction.message.embeds[0]
            raid_id = int(embed.fields[1].value.strip('`'))
            view = DifficultySelectView(raid_id, self.cog)
            await interaction.response.send_message('難易度を選択してください:', view=view, ephemeral=True)
        except discord.errors.NotFound:
            # インタラクショントークンの失効（ボット再起動後の古いメッセージ等）
            logger.warning("report_button: Unknown interaction (token expired or already acknowledged)")
        except Exception:
            logger.exception("report_button でエラーが発生しました")


class DifficultySelect(Select):
    def __init__(self, raid_id: int, cog: "UnionRaidCog"):
        self.raid_id = raid_id
        self.cog = cog
        super().__init__(
            placeholder="難易度を選択してください",
            options=[
                discord.SelectOption(label="ノーマル", value="normal"),
                discord.SelectOption(label="ハード", value="hard"),
            ]
        )

    async def callback(self, interaction: discord.Interaction):
        difficulty = self.values[0]
        async with async_session_factory() as session:
            q = await session.execute(
                RaidReport.__table__.select().where(
                    (RaidReport.raid_id == self.raid_id) & (RaidReport.user_id == interaction.user.id) & (RaidReport.difficulty == difficulty)
                )
            )
            existing = q.first()
            if existing:
                existing_id = existing._mapping.get('id') if hasattr(existing, '_mapping') else existing.id
                await session.execute(
                    RaidReport.__table__.update().where(RaidReport.id == existing_id).values(is_3t=1, reported_at=utcnow_aware(), username=interaction.user.display_name)
                )
            else:
                report = RaidReport(raid_id=self.raid_id, user_id=interaction.user.id, username=interaction.user.display_name, difficulty=difficulty, is_3t=1)
                session.add(report)
            await session.commit()
        await interaction.response.send_message('報告を受け付けました。', ephemeral=True)
        
        # メッセージを更新
        await self.cog._update_raid_message(self.raid_id)


class DifficultySelectView(View):
    def __init__(self, raid_id: int, cog: "UnionRaidCog"):
        super().__init__(timeout=60)
        self.add_item(DifficultySelect(raid_id, cog))

class UnionRaidCog(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self._scheduled_tasks: Dict[int, asyncio.Task] = {}
        self._raid_notify_messages: Dict[int, discord.Message] = {}  # raid_id -> message
        self.bot.add_view(ReportView(self))

    async def resume_schedules(self):
        """起動時にDBから残っているレイドを読み、通知をスケジュールする"""
        now = utcnow_aware()
        async with async_session_factory() as session:
            result = await session.execute(
                UnionRaid.__table__.select().where(
                    UnionRaid.notify_time.isnot(None) & (UnionRaid.end_time > now)
                )
            )
            rows = result.fetchall()
            for row in rows:
                # row is a SQLAlchemy RowMapping
                data = dict(row._mapping)
                r = RaidInfo(
                    id=data.get('id'),
                    guild_id=data.get('guild_id'),
                    raid_name=data.get('raid_name'),
                    start_time=ensure_utc_aware(data.get('start_time')),
                    end_time=ensure_utc_aware(data.get('end_time')),
                    notify_time=ensure_utc_aware(data.get('notify_time')) if data.get('notify_time') else None,
                    channel_id=data.get('channel_id'),
                )
                if r.notify_time and r.notify_time > now:
                    self._schedule_notification_task(r)
                elif r.notify_time and r.notify_time <= now:
                    r = RaidInfo(
                        id=r.id,
                        guild_id=r.guild_id,
                        raid_name=r.raid_name,
                        start_time=r.start_time,
                        end_time=r.end_time,
                        notify_time=now,
                        channel_id=r.channel_id,
                    )
                    self._schedule_notification_task(r)
    
    @app_commands.command(name="レイド作成", description="新しいユニオンレイドを作成します")
    @app_commands.guild_only()
    @app_commands.describe(
        期間時間="レイドの期間（時間単位、デフォルト: 24時間）"
    )
    async def raid_create(
        self,
        interaction: discord.Interaction,
        期間時間: int = 24
    ):
        """レイド作成: モーダルで開始時刻を受け取る（通知は開始時刻と同じ）"""
        if 期間時間 < 1:
            await interaction.response.send_message("レイドの期間は1時間以上で指定してください。", ephemeral=True)
            return

        class RaidStartModal(Modal, title="レイド開始設定"):
            def __init__(self):
                super().__init__()
                now_local = localnow_aware()
                default_time = now_local.strftime("%Y-%m-%d %H:%M")
                self.開始時刻 = TextInput(label=f"開始時刻 (YYYY-MM-DD HH:MM {DEFAULT_TIMEZONE})", default=default_time, required=True)
                self.add_item(self.開始時刻)

            async def on_submit(self, modal_interaction: discord.Interaction):
                await modal_interaction.response.defer()
                try:
                    start_text = self.開始時刻.value.strip()
                    # parse as DEFAULT_TIMEZONE and convert to UTC for display/scheduling
                    start_time_local = datetime.strptime(start_text, "%Y-%m-%d %H:%M").replace(tzinfo=DEFAULT_TIMEZONE)
                    start_time_aware = start_time_local.astimezone(timezone.utc)
                    end_time_aware = start_time_aware + timedelta(hours=期間時間)
                    # store aware UTC datetimes in DB
                    start_time = start_time_aware
                    end_time = end_time_aware
                    notify_time = start_time

                    async with async_session_factory() as session:
                        guild = await session.get(Guild, interaction.guild_id)
                        if not guild:
                            # upsert: race-safe insert (ON CONFLICT DO NOTHING)
                            stmt = pg_insert(Guild.__table__).values(
                                guild_id=interaction.guild_id,
                                created_at=utcnow_aware()
                            ).on_conflict_do_nothing(index_elements=['guild_id'])
                            await session.execute(stmt)

                        now = utcnow_aware()
                        q = await session.execute(
                            UnionRaid.__table__.select().where(
                                (UnionRaid.guild_id == interaction.guild_id) & (UnionRaid.end_time > now)
                            )
                        )
                        active = q.first()
                        if active:
                            await modal_interaction.followup.send("既に進行中のレイドが存在してるわ。先に終了させて頂戴。（/レイド終了）", ephemeral=True)
                            return

                        raid = UnionRaid(
                            guild_id=interaction.guild_id,
                            raid_name="ユニオンレイド",
                            start_time=start_time,
                            end_time=end_time,
                            notify_time=notify_time,
                            channel_id=interaction.channel_id
                        )
                        session.add(raid)
                        await session.commit()
                        await session.refresh(raid)

                        embed = discord.Embed(title="🎯 ユニオンレイドを作成しました", description="**ユニオンレイド**", color=discord.Color.green())
                        embed.add_field(name="レイドID", value=f"`{raid.id}`", inline=True)
                        embed.add_field(name="期間", value=f"{期間時間}時間", inline=True)
                        embed.add_field(name="開始時刻", value=f"<t:{int(start_time_aware.timestamp())}:F>", inline=False)
                        embed.add_field(name="終了時刻", value=f"<t:{int(end_time_aware.timestamp())}:F>", inline=False)
                        embed.set_footer(text=f"作成者: {interaction.user.display_name}")

                        await modal_interaction.followup.send(embed=embed)

                        try:
                            cog: UnionRaidCog = interaction.client.get_cog('UnionRaidCog')
                            if cog:
                                r = RaidInfo(
                                    id=raid.id,
                                    guild_id=raid.guild_id,
                                    raid_name="ユニオンレイド",
                                    start_time=start_time_aware,
                                    end_time=end_time_aware,
                                    notify_time=start_time_aware,
                                    channel_id=raid.channel_id,
                                )
                                cog._schedule_notification_task(r)
                        except Exception:
                            logger.exception(f"レイド通知スケジュール中にエラー")
                            pass
                except Exception as e:
                    logger.exception(f"レイド作成モーダル処理中にエラー")
                    try:
                        await modal_interaction.followup.send(f"入力の解析に失敗しました: {e}", ephemeral=True)
                    except discord.errors.NotFound:
                        logger.exception(f"モーダル例外メッセージ送信失敗（Unknown interaction）")
                    except Exception:
                        logger.exception(f"例外送信時にさらにエラー")

        modal = RaidStartModal()
        await interaction.response.send_modal(modal)

    async def _update_raid_message(self, raid_id: int):
        """レイド通知メッセージを更新（最新の報告状況を反映）"""
        try:
            if raid_id not in self._raid_notify_messages:
                return
            
            message = self._raid_notify_messages[raid_id]
            
            async with async_session_factory() as session:
                # 現在のレイドの全報告を取得
                rq = await session.execute(
                    RaidReport.__table__.select().where((RaidReport.raid_id == raid_id) & (RaidReport.is_3t == 1))
                )
                reports = rq.fetchall()
                normal_users = []
                hard_users = []
                for rep in reports:
                    repm = rep._mapping if hasattr(rep, '_mapping') else rep
                    uname = repm.get('username')
                    diff = repm.get('difficulty')
                    if diff == 'normal':
                        normal_users.append(uname)
                    else:
                        hard_users.append(uname)
            
            # embed を更新
            if message.embeds:
                embed = message.embeds[0]
                # フィールドを更新（ノーマル 3凸とハード 3凸）
                for field in embed.fields:
                    if field.name == "ノーマル 3凸":
                        embed.set_field_at(
                            embed.fields.index(field),
                            name="ノーマル 3凸",
                            value=("\n".join(normal_users) if normal_users else "なし"),
                            inline=False
                        )
                    elif field.name == "ハード 3凸":
                        embed.set_field_at(
                            embed.fields.index(field),
                            name="ハード 3凸",
                            value=("\n".join(hard_users) if hard_users else "なし"),
                            inline=False
                        )
                
                await message.edit(embed=embed)
        except Exception as e:
            logger.error(f"レイドメッセージ更新中にエラー: {e}")

    def _schedule_notification_task(self, raid: RaidInfo):
        """レイドの通知タスクを作成して管理辞書に登録する"""
        guild_id = raid.guild_id
        if guild_id in self._scheduled_tasks:
            return

        async def _task():
            try:
                # compute wait using timestamps to avoid aware/naive subtraction issues
                if not raid.notify_time:
                    return
                # convert notify_time to epoch (handle aware or naive)
                if getattr(raid.notify_time, 'tzinfo', None) is None:
                    notify_ts = raid.notify_time.replace(tzinfo=timezone.utc).timestamp()
                else:
                    notify_ts = raid.notify_time.timestamp()
                now_ts = utcnow_aware().timestamp()
                wait = notify_ts - now_ts
                if wait > 0:
                    await asyncio.sleep(wait)
                try:
                    channel = self.bot.get_channel(raid.channel_id)
                    if not channel:
                        channel = await self.bot.fetch_channel(raid.channel_id)

                    embed = discord.Embed(title="📣 ユニオンレイド通知", description=f"人間、ユニオンレイドが始まったわ。", color=discord.Color.blue())
                    # ensure correct timestamp (make aware if naive)
                    if getattr(raid.start_time, 'tzinfo', None) is None:
                        ts_dt = raid.start_time.replace(tzinfo=timezone.utc)
                    else:
                        ts_dt = raid.start_time
                    embed.add_field(name="開始時刻", value=f"<t:{int(ts_dt.timestamp())}:F>")
                    embed.add_field(name="レイドID", value=f"`{raid.id}`")
                    embed.add_field(name="ノーマル 3凸", value="なし", inline=False)
                    embed.add_field(name="ハード 3凸", value="なし", inline=False)

                    view = ReportView(self)
                    message = await channel.send(embed=embed, view=view)
                    self._raid_notify_messages[raid.id] = message
                    async with async_session_factory() as session:
                        await session.execute(
                            UnionRaid.__table__.update().where(UnionRaid.id == raid.id).values(notify_time=None)
                        )
                        await session.commit()
                except Exception as e:
                    logger.error(f"通知送信中にエラー: {e}")
            finally:
                # Task completion時に必ず削除する
                if guild_id in self._scheduled_tasks:
                    self._scheduled_tasks.pop(guild_id, None)

        task = asyncio.create_task(_task())
        self._scheduled_tasks[guild_id] = task

    @app_commands.command(name="レイド終了", description="進行中のレイドを終了し、3凸報告を集計します")
    @app_commands.guild_only()
    @app_commands.describe(
        順位="最終ランキング順位（例: 1, 42）",
        パーセンテージ="最終ランキングの上位パーセンテージ（例: 5.3）"
    )
    async def raid_end(
        self,
        interaction: discord.Interaction,
        順位: Optional[int] = None,
        パーセンテージ: Optional[float] = None,
    ):
        await interaction.response.defer(ephemeral=False)
        try:
            if 順位 is not None and パーセンテージ is not None:
                await interaction.followup.send("順位かパーセンテージのどちらか一方のみ指定してください。", ephemeral=True)
                return
            if 順位 is not None and 順位 < 1:
                await interaction.followup.send("順位は1以上で指定してください。", ephemeral=True)
                return
            if パーセンテージ is not None and (パーセンテージ <= 0 or パーセンテージ > 100):
                await interaction.followup.send("パーセンテージは0より大きく100以下で指定してください。", ephemeral=True)
                return
            percentage_decimal = to_percentage_decimal(パーセンテージ)

            now = utcnow_aware()
            async with async_session_factory() as session:
                q = await session.execute(
                    UnionRaid.__table__.select().where((UnionRaid.guild_id == interaction.guild_id) & (UnionRaid.end_time > now))
                )
                row = q.first()
                if not row:
                    await interaction.followup.send("進行中のレイドが見つかりません。", ephemeral=True)
                    return
                data = dict(row._mapping) if hasattr(row, '_mapping') else row
                raid_id = data.get('id')
                # 集計: ノーマルとハードで3凸のユーザーを取得
                rq = await session.execute(
                    RaidReport.__table__.select().where((RaidReport.raid_id == raid_id) & (RaidReport.is_3t == 1))
                )
                reports = rq.fetchall()
                normal_users = []
                hard_users = []
                for rep in reports:
                    repm = rep._mapping if hasattr(rep, '_mapping') else rep
                    uname = repm.get('username')
                    diff = repm.get('difficulty')
                    if diff == 'normal':
                        normal_users.append(uname)
                    else:
                        hard_users.append(uname)

                embed = discord.Embed(title=f"レイド {data.get('raid_name')} の終了報告", color=discord.Color.gold())
                if 順位 is not None:
                    embed.add_field(name="最終順位", value=f"{順位}位", inline=True)
                if percentage_decimal is not None:
                    embed.add_field(name="上位パーセンテージ", value=f"{percentage_decimal:.2f}%", inline=True)
                embed.add_field(name="ノーマル 3凸", value=("\n".join(normal_users) if normal_users else "なし"), inline=False)
                embed.add_field(name="ハード 3凸", value=("\n".join(hard_users) if hard_users else "なし"), inline=False)

                # 終了処理: end_time を現在にセット、notify_time をクリア
                await session.execute(
                    UnionRaid.__table__.update().where(UnionRaid.id == raid_id).values(
                        end_time=now,
                        notify_time=None,
                        ranking=順位,
                        percentage=percentage_decimal,
                    )
                )
                await session.commit()

                # cancel scheduled task if any
                gid = data.get('guild_id')
                if gid in self._scheduled_tasks:
                    t = self._scheduled_tasks.pop(gid)
                    t.cancel()
                
                # メッセージもメモリから削除
                if raid_id in self._raid_notify_messages:
                    del self._raid_notify_messages[raid_id]

                await interaction.followup.send(embed=embed, content="人間、今回もおつかれさま。")
        except Exception as e:
            logger.error(f"レイド終了時にエラー: {e}")
            await interaction.followup.send(f"エラーが発生しました: {e}", ephemeral=True)

async def setup(bot):
    await bot.add_cog(UnionRaidCog(bot))