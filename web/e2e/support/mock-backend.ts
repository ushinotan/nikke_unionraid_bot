// bot (Kotlin) API のモックサーバー。状態を持たず、応答はパスだけで決まる。
// 実行: node --import tsx e2e/support/mock-backend.ts
import { createServer } from "node:http";
import type { ServerResponse } from "node:http";
import {
  E2E_BACKEND_PORT,
  E2E_INTERNAL_API_TOKEN,
  GUILD_ERROR,
} from "./constants";
import { guildsResponse, raidDetails, raidsByGuild } from "./mock-data";

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

const server = createServer((req, res) => {
  const path = new URL(req.url ?? "/", "http://mock").pathname;

  if (req.method !== "GET") {
    sendJson(res, 405, { error: "METHOD_NOT_ALLOWED", message: "read-only mock" });
    return;
  }

  if (path === "/health") {
    sendJson(res, 200, { status: "ok" });
    return;
  }

  if (path.startsWith("/api/")) {
    if (req.headers["x-internal-api-token"] !== E2E_INTERNAL_API_TOKEN) {
      sendJson(res, 401, { error: "UNAUTHORIZED", message: "invalid internal token" });
      return;
    }

    if (path === "/api/guilds") {
      sendJson(res, 200, guildsResponse);
      return;
    }

    const guildRaids = path.match(/^\/api\/guilds\/(\d+)\/raids$/);
    if (guildRaids) {
      const guildId = guildRaids[1];
      if (guildId === GUILD_ERROR.id) {
        sendJson(res, 500, { error: "INTERNAL_ERROR", message: "mock failure" });
        return;
      }
      const raids = raidsByGuild[guildId];
      if (raids) {
        sendJson(res, 200, raids);
        return;
      }
    }

    const raidDetail = path.match(/^\/api\/raids\/(\d+)$/);
    if (raidDetail && raidDetails[raidDetail[1]]) {
      sendJson(res, 200, raidDetails[raidDetail[1]]);
      return;
    }
  }

  sendJson(res, 404, { error: "NOT_FOUND", message: "not found" });
});

server.on("error", (error) => {
  console.error(
    `モックサーバーを ${E2E_BACKEND_PORT} 番ポートで起動できません（使用中なら E2E_BACKEND_PORT を変えてください）: ${error.message}`
  );
  process.exit(1);
});

server.listen(E2E_BACKEND_PORT, "127.0.0.1");

function shutdown(): void {
  server.close(() => process.exit(0));
  server.closeAllConnections();
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
