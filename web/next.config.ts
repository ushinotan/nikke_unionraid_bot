import type { NextConfig } from "next";
import { config as dotenvConfig } from "dotenv";
import path from "path";

// リポジトリルートの .env を読み込む
// Issue #41 で docker-compose 統合後は不要（環境変数が注入される）
dotenvConfig({ path: path.resolve(__dirname, "../.env") });

const nextConfig: NextConfig = {
  /* config options here */
};

export default nextConfig;
