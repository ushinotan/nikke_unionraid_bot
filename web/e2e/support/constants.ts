// E2E の設定・モック・スペックで共有する定数。ここ以外で二重に定義しない。
// 秘密値はすべてテスト専用のダミー。

function readPort(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined) {
    return fallback;
  }
  const port = Number(raw);
  if (raw.trim() === "" || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`${name} は 1〜65535 の整数で指定してください: "${raw}"`);
  }
  return port;
}

export const E2E_PORT = readPort("E2E_PORT", 3100);
export const E2E_BACKEND_PORT = readPort("E2E_BACKEND_PORT", 18080);

export const E2E_BASE_URL = `http://localhost:${E2E_PORT}`;
export const E2E_BACKEND_URL = `http://127.0.0.1:${E2E_BACKEND_PORT}`;

export const E2E_SESSION_SECRET = "e2e-session-secret-0123456789abcdef0123";
export const E2E_INTERNAL_API_TOKEN = "e2e-internal-token";
export const E2E_DISCORD_CLIENT_ID = "e2e-dummy-client-id";
export const E2E_DISCORD_CLIENT_SECRET = "e2e-dummy-client-secret";
export const E2E_DISCORD_REDIRECT_URI = `${E2E_BASE_URL}/api/auth/discord/callback`;
export const SESSION_COOKIE_NAME = "nikke_raid_session";

export const GUILD_A = { id: "900000000000000001", name: "E2E ギルド A" };
export const GUILD_B = { id: "900000000000000002", name: "E2E ギルド B" };
export const GUILD_ERROR = { id: "900000000000000003", name: "E2E ギルド エラー" };

export const RAID_A1 = { id: 101, name: "E2E レイド Alpha-1" };
export const RAID_A2 = { id: 102, name: "E2E レイド Alpha-2" };
export const RAID_B1 = { id: 201, name: "E2E レイド Beta-1" };

export const USER_ID = "900000000000000100";
