import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // next/image は未使用。Image Optimization API (/_next/image) を無効化して攻撃面を減らす
  images: {
    unoptimized: true,
  },
};

export default nextConfig;
