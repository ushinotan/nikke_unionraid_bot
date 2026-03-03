import logging
import asyncio
from datetime import datetime, timedelta, timezone
import discord
from discord import app_commands
from discord.ext import commands
from discord import ui
from discord.ui import Modal, TextInput, View, Select, Button
from typing import Dict
from database import async_session_factory
from models import Guild, UnionRaid, RaidReport
from utils import utcnow_aware, localnow_aware, DEFAULT_TIMEZONE
from sqlalchemy.dialects.postgresql import insert as pg_insert

logger = logging.getLogger(__name__)

class ReportView(View):
    def __init__(self):
        super().__init__(timeout=None)

    @ui.button(label="報告", style=discord.ButtonStyle.primary, custom_id="raid_report")
    async def report_button(self, interaction: discord.Interaction, button: Button):
        # embed から raid_id を取得
        embed = interaction.message.embeds[0]
        raid_id = int(embed.fields[1].value.strip('`'))
        # Modal を開く
        modal = ReportModal(raid_id)
        await interaction.response.send_modal(modal)

class ReportModal(Modal):
    def __init__(self, raid_id: int):
        super().__init__(title="レイド報告")
        self.raid_id = raid_id
        self.difficulty = Select(
            placeholder="難易度を選択してください",
            options=[
                discord.SelectOption(label="ノーマル", value="normal"),
                discord.SelectOption(label="ハード", value="hard")
            ]
        )
        self.add_item(self.difficulty)

    async def on_submit(self, modal_interaction: discord.Interaction):
        difficulty = self.difficulty.values[0]
        async with async_session_factory() as session:
            q = await session.execute(
                RaidReport.__table__.select().where(
                    (RaidReport.raid_id == self.raid_id) & (RaidReport.user_id == modal_interaction.user.id) & (RaidReport.difficulty == difficulty)
                )
            )
            existing = q.first()
            if existing:
                existing_id = existing._mapping.get('id') if hasattr(existing, '_mapping') else existing.id
                await session.execute(
                    RaidReport.__table__.update().where(RaidReport.id == existing_id).values(is_3t=1, reported_at=utcnow_aware(), username=modal_interaction.user.display_name)
                )
            else:
                report = RaidReport(raid_id=self.raid_id, user_id=modal_interaction.user.id, username=modal_interaction.user.display_name, difficulty=difficulty, is_3t=1)
                session.add(report)
            await session.commit()
        await modal_interaction.response.send_message('報告を受け付けました。', ephemeral=True)

class UnionRaidCog(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self._scheduled_tasks: Dict[int, asyncio.Task] = {}
        self.bot.add_view(ReportView())

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
                # Build a lightweight raid-like object
                class _R: pass
                r = _R()
                r.id = data.get('id')
                r.guild_id = data.get('guild_id')
                r.raid_name = data.get('raid_name')
                r.start_time = data.get('start_time')
                r.end_time = data.get('end_time')
                r.notify_time = data.get('notify_time')
                r.channel_id = data.get('channel_id')
                if r.notify_time and r.notify_time > now:
                    self._schedule_notification_task(r)
                elif r.notify_time and r.notify_time <= now:
                    r.notify_time = now
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
                    # store naive UTC datetimes in DB to match existing schema
                    start_time = start_time_aware.replace(tzinfo=None)
                    end_time = end_time_aware.replace(tzinfo=None)
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
                                class _R: pass
                                r = _R()
                                r.id = raid.id
                                r.guild_id = raid.guild_id
                                r.raid_name = "ユニオンレイド"
                                # pass aware times for scheduling/display
                                r.start_time = start_time_aware
                                r.end_time = end_time_aware
                                r.notify_time = start_time_aware
                                r.channel_id = raid.channel_id
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

    def _schedule_notification_task(self, raid: UnionRaid):
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

                    view = ReportView()
                    await channel.send(embed=embed, view=view)
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
    async def raid_end(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=False)
        try:
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
                embed.add_field(name="ノーマル 3凸", value=("\n".join(normal_users) if normal_users else "なし"), inline=False)
                embed.add_field(name="ハード 3凸", value=("\n".join(hard_users) if hard_users else "なし"), inline=False)

                # 終了処理: end_time を現在にセット、notify_time をクリア
                await session.execute(
                    UnionRaid.__table__.update().where(UnionRaid.id == raid_id).values(end_time=now, notify_time=None)
                )
                await session.commit()

                # cancel scheduled task if any
                gid = data.get('guild_id')
                if gid in self._scheduled_tasks:
                    t = self._scheduled_tasks.pop(gid)
                    t.cancel()

                await interaction.followup.send(embed=embed, content="人間、今回もおつかれさま。")
        except Exception as e:
            logger.error(f"レイド終了時にエラー: {e}")
            await interaction.followup.send(f"エラーが発生しました: {e}", ephemeral=True)

async def setup(bot):
    await bot.add_cog(UnionRaidCog(bot))