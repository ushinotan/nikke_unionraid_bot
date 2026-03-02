import logging
import asyncio
import os
import discord
from discord.ext import commands
from utils import utcnow_aware
from database import async_session_factory, Base
from sqlalchemy import delete
from models import Guild
from cogs.union_raid import UnionRaidCog

logger = logging.getLogger(__name__)

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