import { describe, it } from "node:test";
import assert from "node:assert";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const WEB_DIR = fileURLToPath(new URL("..", import.meta.url));

// バックエンドへ書き込みを転送しない書き込みハンドラー（requireRaidEditPermission 不要）。メソッド単位で許可する
const ALLOWLIST: Record<string, { methods: string[]; reason: string }> = {
  "app/api/auth/logout/route.ts": {
    methods: ["POST"],
    reason: "セッションを破棄するだけで、バックエンドに書き込まない",
  },
  "app/api/session/selected-guild/route.ts": {
    methods: ["POST"],
    reason: "セッション内の選択ギルドを変えるだけ（guildIds で検証済み）で、バックエンドに書き込まない",
  },
};

const WRITE_METHODS = ["POST", "PUT", "PATCH", "DELETE"];
const ROUTE_FILE = /^route\.(?:ts|tsx|js|jsx|mjs|mts)$/;
const SOURCE_FILE = /\.(?:ts|tsx|js|jsx|mjs|mts)$/;

function listFiles(dir: string, base: string): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const rel = `${base}/${entry.name}`;
    if (entry.isDirectory()) {
      result.push(...listFiles(join(dir, entry.name), rel));
    } else {
      result.push(rel);
    }
  }
  return result;
}

/** コメントを取り除く（URL の "://" は残す）。文字列内の "/*" などは考慮しない簡易版 */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:])\/\/.*$/gm, "$1");
}

type WriteExport = { method: string; body: string | null };

/**
 * export された書き込みメソッドを集める。body は `export (async) function METHOD` の本体で、
 * それ以外の形（export const / export { x as METHOD } / 再 export）は本体を特定できないので null
 */
function writeExports(source: string): WriteExport[] {
  const found: WriteExport[] = [];
  for (const m of source.matchAll(/export\s+(?:async\s+)?function\s*\*?\s*(\w+)\s*\(/g)) {
    if (!WRITE_METHODS.includes(m[1])) continue;
    found.push({ method: m[1], body: functionBody(source, m.index + m[0].length) });
  }
  for (const m of source.matchAll(/export\s+(?:const|let|var)\s+(\w+)/g)) {
    if (WRITE_METHODS.includes(m[1])) found.push({ method: m[1], body: null });
  }
  for (const m of source.matchAll(/export\s*(?:type\s*)?\{([^}]*)\}/g)) {
    for (const spec of m[1].split(",")) {
      const name = spec.trim().split(/\s+as\s+/).pop();
      if (name && WRITE_METHODS.includes(name)) found.push({ method: name, body: null });
    }
  }
  return found;
}

/** 引数リストの後ろの最初の `{` から対応する `}` までを返す */
function functionBody(source: string, from: number): string | null {
  const start = source.indexOf("{", source.indexOf(")", from));
  if (start < 0) return null;
  let depth = 0;
  for (let i = start; i < source.length; i++) {
    if (source[i] === "{") depth++;
    else if (source[i] === "}" && --depth === 0) return source.slice(start, i + 1);
  }
  return null;
}

/** ガードを呼び、ok を確かめ、その後でバックエンドを呼んでいるか */
function guardProblem(body: string | null): string | null {
  if (body === null) {
    return "export async function の形で書いてください（本体を検査できません）";
  }
  const guardAt = body.indexOf("requireRaidEditPermission(");
  if (guardAt < 0) return "requireRaidEditPermission を呼んでいません";
  if (!/\.ok\b/.test(body.slice(guardAt))) return "requireRaidEditPermission の結果（ok）を確かめていません";
  const backendAt = body.search(/\b(?:fetchFromBackend|fetch)\s*[<(]/);
  if (backendAt >= 0 && backendAt < guardAt) return "requireRaidEditPermission より前にバックエンドを呼んでいます";
  return null;
}

describe("書き込みハンドラーのガード", () => {
  const sources = new Map<string, string>();
  for (const rel of listFiles(join(WEB_DIR, "app"), "app").concat(listFiles(join(WEB_DIR, "lib"), "lib"))) {
    if (SOURCE_FILE.test(rel)) sources.set(rel, stripComments(readFileSync(join(WEB_DIR, rel), "utf8")));
  }
  const routeFiles = [...sources.keys()].filter(
    (rel) => rel.startsWith("app/") && ROUTE_FILE.test(rel.slice(rel.lastIndexOf("/") + 1))
  );

  it("書き込みルートは requireRaidEditPermission を通すか、許可リストにメソッド単位で理由付きで載っている", () => {
    const violations: string[] = [];
    for (const rel of routeFiles) {
      const source = sources.get(rel)!;
      if (/export\s*\*/.test(source)) {
        violations.push(`${rel}: export * は使わないでください（書き込みメソッドを検査できません）`);
      }
      for (const { method, body } of writeExports(source)) {
        if (ALLOWLIST[rel]?.methods.includes(method)) continue;
        const problem = guardProblem(body);
        if (problem) violations.push(`${rel} ${method}: ${problem}`);
      }
    }
    assert.deepStrictEqual(
      violations,
      [],
      `lib/raid-authorization.ts の requireRaidEditPermission を通すか、バックエンドに書き込まない理由を許可リストに書いてください:\n${violations.join("\n")}`
    );
  });

  it("Server Action（\"use server\"）は使わない", () => {
    // route handler と同じ検査をしていないため、使うときはこのテストを拡張する
    const actions = [...sources].filter(([, source]) => /^\s*["']use server["']/m.test(source)).map(([rel]) => rel);
    assert.deepStrictEqual(actions, [], `Server Action はガードの検査対象外です: ${actions.join(", ")}`);
  });

  it("許可リストの各エントリーは、存在するファイルが export する書き込みメソッドである", () => {
    const stale: string[] = [];
    for (const [rel, { methods }] of Object.entries(ALLOWLIST)) {
      const source = sources.get(rel);
      if (source === undefined) {
        stale.push(`${rel}: ファイルが存在しません`);
        continue;
      }
      const exported = writeExports(source).map((e) => e.method);
      for (const method of methods.filter((m) => !exported.includes(m))) {
        stale.push(`${rel} ${method}: export されていません`);
      }
    }
    assert.deepStrictEqual(stale, [], `許可リストを直してください:\n${stale.join("\n")}`);
  });

  it("許可リストのファイルはバックエンドを呼ばない", () => {
    const calling = Object.keys(ALLOWLIST).filter((rel) =>
      /\b(?:fetchFromBackend|fetch)\b/.test(sources.get(rel) ?? "")
    );
    assert.deepStrictEqual(calling, [], `許可リストのファイルがバックエンドを呼んでいます: ${calling.join(", ")}`);
  });
});
