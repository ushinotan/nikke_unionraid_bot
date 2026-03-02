import os
import asyncio
import logging
from dotenv import load_dotenv
from bot import NikkeUnionRaidBot
from database import init_db

# Load environment variables
load_dotenv()

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/bot.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)

async def main():
    """Botのメインエントリーポイント"""
    # 環境変数からDiscordトークンを取得
    token = os.getenv('DISCORD_TOKEN')
    
    if not token:
        logger.error("環境変数にDISCORD_TOKENが見つかりません")
        return
    
    # データベースの初期化
    logger.info("データベースを初期化しています...")
    try:
        await init_db()
        logger.info("データベースの初期化が完了しました")
    except Exception as e:
        logger.error(f"データベースの初期化に失敗しました: {e}")
        return
    
    # Botの作成と起動
    bot = NikkeUnionRaidBot()
    
    try:
        logger.info("Botを起動しています...")
        await bot.start(token)
    except KeyboardInterrupt:
        logger.info("キーボード割り込みを受信しました。シャットダウンします...")
    except Exception as e:
        logger.error(f"Botでエラーが発生しました: {e}")
    finally:
        if not bot.is_closed():
            await bot.close()
        logger.info("Botを停止しました")

if __name__ == "__main__":
    # logsディレクトリが存在しない場合は作成
    os.makedirs('logs', exist_ok=True)
    
    # Botを実行
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Botのシャットダウンが完了しました")