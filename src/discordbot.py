import logging
import asyncio
import os
from datetime import datetime, timedelta, timezone
import discord
from discord import app_commands
from discord.ext import commands
from discord import ui
from discord.ui import Modal, TextInput, View, Select, Button
from typing import Dict
from sqlalchemy import delete
from database import async_session_factory, Base
from sqlalchemy import Column, BigInteger, Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from sqlalchemy.dialects.postgresql import insert as pg_insert

logger = logging.getLogger(__name__)

# デフォルトタイムゾーン設定（環境変数 DEFAULT_TIMEZONE で変更可能、デフォルト: JST）
DEFAULT_TIMEZONE_OFFSET = int(os.getenv('DEFAULT_TIMEZONE_HOURS', '9'))
DEFAULT_TIMEZONE = timezone(timedelta(hours=DEFAULT_TIMEZONE_OFFSET))

# ヘルパー: タイムゾーン情報付きの現在UTCと、DB保存用のナイーブUTCを返す
def utcnow_aware() -> datetime:
    return datetime.now(timezone.utc)

def utcnow_naive() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)

def localnow_aware() -> datetime:
    """デフォルトタイムゾーンでの現在時刻（aware）"""
    return datetime.now(DEFAULT_TIMEZONE)

# データベースモデル
class Guild(Base):
    __tablename__ = 'guilds'
    
    guild_id = Column(BigInteger, primary_key=True)
    created_at = Column(DateTime(timezone=True), default=utcnow_aware)
    
    raids = relationship("UnionRaid", back_populates="guild", cascade="all, delete-orphan")

class UnionRaid(Base):
    __tablename__ = 'union_raids'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    guild_id = Column(BigInteger, ForeignKey('guilds.guild_id', ondelete='CASCADE'))
    raid_name = Column(String(255), nullable=False)
    start_time = Column(DateTime(timezone=True), nullable=False)
    end_time = Column(DateTime(timezone=True), nullable=False)
    notify_time = Column(DateTime(timezone=True), nullable=True)
    channel_id = Column(BigInteger, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow_aware)
    
    guild = relationship("Guild", back_populates="raids")
    participants = relationship("RaidParticipant", back_populates="raid", cascade="all, delete-orphan")

class RaidParticipant(Base):
    __tablename__ = 'raid_participants'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    raid_id = Column(Integer, ForeignKey('union_raids.id', ondelete='CASCADE'))
    user_id = Column(BigInteger, nullable=False)
    username = Column(String(255), nullable=False)
    score = Column(Integer, default=0)
    joined_at = Column(DateTime(timezone=True), default=utcnow_aware)
    
    raid = relationship("UnionRaid", back_populates="participants")

class RaidReport(Base):
    __tablename__ = 'raid_reports'

    id = Column(Integer, primary_key=True, autoincrement=True)
    raid_id = Column(Integer, ForeignKey('union_raids.id', ondelete='CASCADE'))
    user_id = Column(BigInteger, nullable=False)
    username = Column(String(255), nullable=False)
    difficulty = Column(String(32), nullable=False)  # 'normal' or 'hard'
    is_3t = Column(Integer, default=0)  # 1 = true, 0 = false
    reported_at = Column(DateTime(timezone=True), default=utcnow_aware)

    raid = relationship("UnionRaid")

class NikkeUnionRaidBot(commands.Bot):
    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = True
        intents.guilds = True
        intents.members = True
        
        super().__init__(
            command_prefix="!",
            intents=intents,
            help_command=None
        )
    
    async def setup_hook(self):
        """Botの起動時に呼ばれる"""
        # コマンドを登録
        cog = UnionRaidCog(self)
        await self.add_cog(cog)
        
        # Discordとコマンドを同期
        logger.info("コマンドを同期しています...")
        try:
            # タイムアウト設定（20秒）
            await asyncio.wait_for(self.tree.sync(), timeout=20.0)
            logger.info("コマンドの同期が完了しました")
        except Exception as e:
            logger.warning(f"コマンド同期中にエラーが発生しました: {e}。Bot は継続して起動します。")
        # resume schedules for pending raid notifications
        try:
            await cog.resume_schedules()
        except Exception as e:
            logger.error(f"スケジュール再開中にエラーが発生しました: {e}", exc_info=True)
    
    async def on_ready(self):
        """Botの準備が完了したときに呼ばれる"""
        logger.info(f'{self.user} (ID: {self.user.id}) としてログインしました')
        logger.info(f'{len(self.guilds)}個のサーバーに接続しています')
        
        # Botのステータスを設定
        await self.change_presence(
            activity=discord.Game(name="NIKKE ユニオンレイド管理")
        )
    
    async def on_guild_join(self, guild: discord.Guild):
        """Botがサーバーに参加したときに呼ばれる"""
        logger.info(f'サーバーに参加しました: {guild.name} (ID: {guild.id})')
        
        # データベースにサーバーを登録
        async with async_session_factory() as session:
            session.add(Guild(guild_id=guild.id))
            await session.commit()
    
    async def on_guild_remove(self, guild: discord.Guild):
        """Botがサーバーから退出したときに呼ばれる"""
        logger.info(f'サーバーから退出しました: {guild.name} (ID: {guild.id})')
        
        # データベースからサーバーを削除
        async with async_session_factory() as session:
            await session.execute(
                delete(Guild).where(Guild.guild_id == guild.id)
            )
            await session.commit()

class UnionRaidCog(commands.Cog):
    def __init__(self, bot: NikkeUnionRaidBot):
        self.bot = bot
        self._scheduled_tasks: Dict[int, asyncio.Task] = {}

    async def resume_schedules(self):
        """起動時にDBから残っているレイドを読み、通知をスケジュールする"""
        now = utcnow_aware()
        async with async_session_factory() as session:
            result = await session.execute(
                UnionRaid.__table__.select().where(
                    (UnionRaid.notify_time != None) & (UnionRaid.end_time > now)
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
    
    @app_commands.command(name="レイド作成", description="新しいユニオンレイドを作成します")
    @app_commands.describe(
        期間時間="レイドの期間（時間単位、デフォルト: 24時間）"
    )
    async def raid_create(
        self,
        interaction: discord.Interaction,
        期間時間: int = 24
    ):
        """レイド作成: モーダルで開始時刻を受け取る（通知は開始時刻と同じ）"""
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
                            # ensure we have the guild object afterwards
                            guild = await session.get(Guild, interaction.guild_id)

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
                            pass
                except Exception as e:
                    logger.exception(f"レイド作成モーダル処理中にエラー: {e}")
                    try:
                        await modal_interaction.followup.send(f"入力の解析に失敗しました: {e}", ephemeral=True)
                    except discord.errors.NotFound:
                        logger.exception(f"モーダル例外メッセージ送信失敗（Unknown interaction）")
                    except Exception as se:
                        logger.error(f"例外送信時にさらにエラー: {se}")

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

                    class ReportView(View):
                        def __init__(self, raid_id: int):
                            super().__init__(timeout=None)
                            self.raid_id = raid_id

                        @ui.button(label="報告", style=discord.ButtonStyle.primary, custom_id="raid_report_button")
                        async def report_button(self, interaction: discord.Interaction, button: Button):
                            raid_id_ref = self.raid_id
                            
                            class DifficultySelect(Select):
                                def __init__(self):
                                    options = [
                                        discord.SelectOption(label="ノーマル", value="normal"),
                                        discord.SelectOption(label="ハード", value="hard")
                                    ]
                                    super().__init__(placeholder="難易度を選択してください", options=options)
                                
                                async def callback(self, select_interaction: discord.Interaction):
                                    difficulty = self.values[0]
                                    async with async_session_factory() as session:
                                        q = await session.execute(
                                            RaidReport.__table__.select().where(
                                                (RaidReport.raid_id == raid_id_ref) & (RaidReport.user_id == select_interaction.user.id) & (RaidReport.difficulty == difficulty)
                                            )
                                        )
                                        existing = q.first()
                                        if existing:
                                            existing_id = existing._mapping.get('id') if hasattr(existing, '_mapping') else existing.id
                                            await session.execute(
                                                RaidReport.__table__.update().where(RaidReport.id == existing_id).values(is_3t=1, reported_at=utcnow_aware(), username=select_interaction.user.display_name)
                                            )
                                        else:
                                            report = RaidReport(raid_id=raid_id_ref, user_id=select_interaction.user.id, username=select_interaction.user.display_name, difficulty=difficulty, is_3t=1)
                                            session.add(report)
                                        await session.commit()
                                    await select_interaction.response.send_message('報告を受け付けました。', ephemeral=True)
                            
                            view = View()
                            view.add_item(DifficultySelect())
                            await interaction.response.send_message('難易度を選択してください:', view=view, ephemeral=True)

                    view = ReportView(raid_id=raid.id)
                    await channel.send(embed=embed, view=view)
                except Exception as e:
                    logger.error(f"通知送信中にエラー: {e}")
            finally:
                # Task completion時に必ず削除する
                if guild_id in self._scheduled_tasks:
                    self._scheduled_tasks.pop(guild_id, None)

        task = asyncio.create_task(_task())
        self._scheduled_tasks[guild_id] = task

    @app_commands.command(name="レイド終了", description="進行中のレイドを終了し、3凸報告を集計します")
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

async def setup(bot: NikkeUnionRaidBot):
    await bot.add_cog(UnionRaidCog(bot))
