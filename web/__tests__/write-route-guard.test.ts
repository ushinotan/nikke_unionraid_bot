import { describe, it } from "node:test";
import assert from "node:assert";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const API_DIR = fileURLToPath(new URL("../app/api", import.meta.url));

// バックエンドへ書き込みを転送しない書き込みルート（requireRaidEditPermission 不要）
const ALLOWLIST: Record<string, string> = {
  "auth/logout/route.ts": "セッションを破棄するだけで、バックエンドに書き込まない",
  "session/selected-guild/route.ts":
    "セッション内の選択ギルドを変えるだけ（guildIds で検証済み）で、バックエンドに書き込まない",
};

const WRITE_EXPORT = /export\s+(?:async\s+)?function\s+(?:POST|PUT|PATCH|DELETE)\b|export\s+const\s+(?:POST|PUT|PATCH|DELETE)\b/;

function listRouteFiles(dir: string, base = ""): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const rel = base ? `${base}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      result.push(...listRouteFiles(join(dir, entry.name), rel));
    } else if (entry.name === "route.ts") {
      result.push(rel);
    }
  }
  return result;
}

describe("書き込み系 API ルートのガード", () => {
  const writeRoutes = listRouteFiles(API_DIR).filter((rel) =>
    WRITE_EXPORT.test(readFileSync(join(API_DIR, rel), "utf8"))
  );

  it("書き込みルートは requireRaidEditPermission を呼ぶか、許可リストに理由付きで載っている", () => {
    const violations = writeRoutes.filter((rel) => {
      if (rel in ALLOWLIST) return false;
      return !readFileSync(join(API_DIR, rel), "utf8").includes("requireRaidEditPermission(");
    });
    assert.deepStrictEqual(
      violations,
      [],
      `lib/raid-authorization.ts の requireRaidEditPermission を通すか、バックエンドに書き込まない理由を許可リストに書いてください: ${violations.join(", ")}`
    );
  });

  it("許可リストのファイルがすべて存在する", () => {
    const missing = Object.keys(ALLOWLIST).filter((rel) => !existsSync(join(API_DIR, rel)));
    assert.deepStrictEqual(missing, [], `許可リストのファイルが存在しません: ${missing.join(", ")}`);
  });

  it("許可リストのファイルは書き込みルートである", () => {
    const stale = Object.keys(ALLOWLIST).filter((rel) => !writeRoutes.includes(rel));
    assert.deepStrictEqual(stale, [], `書き込みルートでないものが許可リストにあります: ${stale.join(", ")}`);
  });
});
