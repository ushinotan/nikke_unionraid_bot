import logging
import asyncio
from datetime import datetime, timedelta
import discord
from discord import app_commands
from discord.ext import commands
from sqlalchemy import delete
from database import async_session_factory, Base
from sqlalchemy import Column, BigInteger, Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship

logger = logging.getLogger(__name__)

# データベースモデル
class Guild(Base):
    __tablename__ = 'guilds'
    
    guild_id = Column(BigInteger, primary_key=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    raids = relationship("UnionRaid", back_populates="guild", cascade="all, delete-orphan")

class UnionRaid(Base):
    __tablename__ = 'union_raids'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    guild_id = Column(BigInteger, ForeignKey('guilds.guild_id', ondelete='CASCADE'))
    raid_name = Column(String(255), nullable=False)
    start_time = Column(DateTime, nullable=False)
    end_time = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    guild = relationship("Guild", back_populates="raids")
    participants = relationship("RaidParticipant", back_populates="raid", cascade="all, delete-orphan")

class RaidParticipant(Base):
    __tablename__ = 'raid_participants'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    raid_id = Column(Integer, ForeignKey('union_raids.id', ondelete='CASCADE'))
    user_id = Column(BigInteger, nullable=False)
    username = Column(String(255), nullable=False)
    score = Column(Integer, default=0)
    joined_at = Column(DateTime, default=datetime.utcnow)
    
    raid = relationship("UnionRaid", back_populates="participants")

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
        await self.add_cog(UnionRaidCog(self))
        
        # Discordとコマンドを同期
        logger.info("コマンドを同期しています...")
        try:
            # タイムアウト設定（20秒）
            await asyncio.wait_for(self.tree.sync(), timeout=20.0)
            logger.info("コマンドの同期が完了しました")
        except Exception as e:
            logger.warning(f"コマンド同期中にエラーが発生しました: {e}。Bot は継続して起動します。")
    
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
    
    @app_commands.command(name="レイド作成", description="新しいユニオンレイドを作成します")
    @app_commands.describe(
        名前="レイドの名前",
        期間時間="レイドの期間（時間単位、デフォルト: 24時間）"
    )
    async def raid_create(
        self,
        interaction: discord.Interaction,
        名前: str,
        期間時間: int = 24
    ):
        """新しいユニオンレイドを作成"""
        await interaction.response.defer()
        
        try:
            async with async_session_factory() as session:
                # サーバーが存在することを確認
                guild = await session.get(Guild, interaction.guild_id)
                if not guild:
                    guild = Guild(guild_id=interaction.guild_id)
                    session.add(guild)
                    await session.flush()
                
                # レイドを作成
                start_time = datetime.utcnow()
                end_time = start_time + timedelta(hours=期間時間)
                
                raid = UnionRaid(
                    guild_id=interaction.guild_id,
                    raid_name=名前,
                    start_time=start_time,
                    end_time=end_time
                )
                session.add(raid)
                await session.commit()
                await session.refresh(raid)
                
                embed = discord.Embed(
                    title="🎯 ユニオンレイドを作成しました",
                    description=f"**{名前}**",
                    color=discord.Color.green()
                )
                embed.add_field(name="レイドID", value=f"`{raid.id}`", inline=True)
                embed.add_field(name="期間", value=f"{期間時間}時間", inline=True)
                embed.add_field(name="開始時刻", value=f"<t:{int(start_time.timestamp())}:F>", inline=False)
                embed.add_field(name="終了時刻", value=f"<t:{int(end_time.timestamp())}:F>", inline=False)
                embed.set_footer(text=f"作成者: {interaction.user.display_name}")
                
                await interaction.followup.send(embed=embed)
                logger.info(f"レイドを作成しました: {名前} (ID: {raid.id}) サーバー: {interaction.guild_id}")

        except Exception as e:
            logger.error(f"レイド作成時にエラーが発生しました: {e}")
            await interaction.followup.send(f"❌ レイドの作成中にエラーが発生しました: {str(e)}", ephemeral=True)

async def setup(bot: NikkeUnionRaidBot):
    await bot.add_cog(UnionRaidCog(bot))
