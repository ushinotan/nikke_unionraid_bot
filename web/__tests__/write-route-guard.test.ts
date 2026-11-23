import { describe, it } from "node:test";
import assert from "node:assert";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";
import { WRITE_METHODS } from "./helpers/raid-edit-fixtures";

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

// 許可リストのファイルが lib から import してよい名前（バックエンドを呼ばないもの）
const ALLOWLIST_SAFE_IMPORTS = new Set(["getSession", "getPublicOrigin", "validateSnowflakeId", "validateNumericId"]);

// ガードより前に呼んでよい関数。レコードの持ち主を調べる読み取りヘルパーを足すときはここに理由付きで追加する
const SAFE_CALLS_BEFORE_GUARD = new Set([
  "getSession",
  "validateSnowflakeId",
  "validateNumericId",
  "encodeURIComponent",
  "Number",
  "String",
  "parseInt",
]);
// request.json() / searchParams.get() / NextResponse.json() など、入力の読み取りと応答の組み立て
const SAFE_METHODS_BEFORE_GUARD = new Set(["json", "text", "formData", "get", "has"]);

const ROUTE_FILE = /\/route\.(?:ts|tsx|js|jsx|mjs|mts)$/;
const SOURCE_FILE = /\.(?:ts|tsx|js|jsx|mjs|mts)$/;
const GUARD = "requireRaidEditPermission";
const isWriteMethod = (name: string) => (WRITE_METHODS as readonly string[]).includes(name);

function listFiles(dir: string, base: string): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const rel = `${base}/${entry.name}`;
    if (entry.isDirectory()) result.push(...listFiles(join(dir, entry.name), rel));
    else if (SOURCE_FILE.test(rel)) result.push(rel);
  }
  return result;
}

function parse(rel: string): ts.SourceFile {
  const source = readFileSync(join(WEB_DIR, rel), "utf8");
  return ts.createSourceFile(rel, source, ts.ScriptTarget.Latest, true, /x$/.test(rel) ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
}

function walk(node: ts.Node, visit: (node: ts.Node) => void): void {
  visit(node);
  node.forEachChild((child) => walk(child, visit));
}

const hasExport = (node: ts.Node) =>
  ts.canHaveModifiers(node) && (ts.getModifiers(node) ?? []).some((m) => m.kind === ts.SyntaxKind.ExportKeyword);

function bindingNames(name: ts.BindingName): string[] {
  if (ts.isIdentifier(name)) return [name.text];
  return name.elements.flatMap((e) => (ts.isOmittedExpression(e) ? [] : bindingNames(e.name)));
}

type ExportedHandler = { method: string; fn: ts.FunctionDeclaration | null; line: number };

/** export された書き込みメソッドを集める。`export async function` 以外の形は fn が null（本体を検査できない） */
function writeExports(file: ts.SourceFile): { handlers: ExportedHandler[]; problems: string[] } {
  const handlers: ExportedHandler[] = [];
  const problems: string[] = [];
  const lineOf = (node: ts.Node) => file.getLineAndCharacterOfPosition(node.getStart()).line + 1;
  for (const stmt of file.statements) {
    if (ts.isFunctionDeclaration(stmt) && hasExport(stmt) && stmt.name && isWriteMethod(stmt.name.text)) {
      handlers.push({ method: stmt.name.text, fn: stmt, line: lineOf(stmt) });
    } else if (ts.isVariableStatement(stmt) && hasExport(stmt)) {
      for (const decl of stmt.declarationList.declarations) {
        for (const name of bindingNames(decl.name).filter(isWriteMethod)) {
          handlers.push({ method: name, fn: null, line: lineOf(decl) });
        }
      }
    } else if (ts.isExportDeclaration(stmt)) {
      if (!stmt.exportClause) {
        problems.push(`L${lineOf(stmt)}: export * は使わないでください（書き込みメソッドを検査できません）`);
      } else if (ts.isNamedExports(stmt.exportClause)) {
        for (const spec of stmt.exportClause.elements.filter((s) => isWriteMethod(s.name.text))) {
          handlers.push({ method: spec.name.text, fn: null, line: lineOf(spec) });
        }
      }
    }
  }
  return { handlers, problems };
}

const BACKEND_MODULE = /(?:^|\/)backend-client$/;
const BACKEND_FUNCTION = "fetchFromBackend";

type BackendImports = { names: Set<string>; problems: string[] };

/** ファイル内で fetchFromBackend を指す名前（別名 import を含む）。名前空間 import は追えないので禁止する */
function backendImports(file: ts.SourceFile): BackendImports {
  const names = new Set([BACKEND_FUNCTION]);
  const problems: string[] = [];
  for (const stmt of file.statements) {
    if (!ts.isImportDeclaration(stmt) || !ts.isStringLiteral(stmt.moduleSpecifier)) continue;
    if (!BACKEND_MODULE.test(stmt.moduleSpecifier.text)) continue;
    const bindings = stmt.importClause?.namedBindings;
    if (bindings && ts.isNamespaceImport(bindings)) {
      const line = file.getLineAndCharacterOfPosition(stmt.getStart()).line + 1;
      problems.push(`L${line}: backend-client は名前空間 import せず、使う名前を import してください（呼び出しを検査できません）`);
    } else if (bindings) {
      for (const e of bindings.elements) {
        if ((e.propertyName ?? e.name).text === BACKEND_FUNCTION) names.add(e.name.text);
      }
    }
  }
  return { names, problems };
}

/** 外部 API の固定 URL（ホストまで文字列リテラルの "https://discord.com/..." など） */
function isExternalUrl(url: ts.Expression): boolean {
  const EXTERNAL = /^https:\/\/[^/]+\//;
  if (ts.isStringLiteralLike(url)) return EXTERNAL.test(url.text);
  return ts.isTemplateExpression(url) && EXTERNAL.test(url.head.text);
}

/** fetch の init の method。指定が無いと確かめられたときだけ "GET"、読めないときは null */
function initMethod(init: ts.Expression | undefined): string | null {
  if (!init) return "GET";
  if (!ts.isObjectLiteralExpression(init)) return null;
  let method = "GET";
  for (const p of init.properties) {
    const key = ts.isSpreadAssignment(p) ? undefined : p.name;
    if (!key || !(ts.isIdentifier(key) || ts.isStringLiteralLike(key))) return null; // スプレッド・計算キー
    if (key.text !== "method") continue;
    if (!ts.isPropertyAssignment(p) || !ts.isStringLiteralLike(p.initializer)) return null; // 省略記法・変数
    method = p.initializer.text.toUpperCase();
  }
  return method;
}

/**
 * バックエンドへの呼び出しなら、その HTTP メソッド（読めなければ null）。バックエンドへの呼び出しでなければ null を返す。
 * - fetchFromBackend（別名 import と `x.fetchFromBackend(...)` を含む）は第 2 引数から読む
 * - fetch は外部 API の固定 URL 以外を常に method 不明とする（`new Request(url, { method })` を渡せるため）
 */
function backendCallInfo(call: ts.CallExpression, names: Set<string>): { method: string | null } | null {
  const callee = call.expression;
  const name = ts.isIdentifier(callee) ? callee.text : ts.isPropertyAccessExpression(callee) ? callee.name.text : null;
  if ((ts.isIdentifier(callee) && names.has(callee.text)) || (!ts.isIdentifier(callee) && name === BACKEND_FUNCTION)) {
    return { method: initMethod(call.arguments[1]) };
  }
  if (name !== "fetch") return null;
  const [url] = call.arguments;
  return url && isExternalUrl(url) ? null : { method: null };
}

/** fetchFromBackend を呼ばずに値として渡している箇所（`const f = fetchFromBackend` など。呼び出しを追えない） */
function backendReferences(file: ts.SourceFile, names: Set<string>): string[] {
  const found: string[] = [];
  walk(file, (node) => {
    if (!ts.isIdentifier(node) || !names.has(node.text)) return;
    const parent = node.parent;
    if (ts.isImportSpecifier(parent)) return;
    if (ts.isCallExpression(parent) && parent.expression === node) return;
    if (ts.isPropertyAccessExpression(parent) && parent.name === node) {
      if (ts.isCallExpression(parent.parent) && parent.parent.expression === parent) return;
    }
    const line = file.getLineAndCharacterOfPosition(node.getStart()).line + 1;
    found.push(`L${line}: ${node.text} は直接呼び出してください（値として渡すと検査できません）`);
  });
  return found;
}

/** `const x = await requireRaidEditPermission(...)` なら x */
function guardVariable(stmt: ts.Statement): string | null {
  if (!ts.isVariableStatement(stmt)) return null;
  const [decl] = stmt.declarationList.declarations;
  if (stmt.declarationList.declarations.length !== 1 || !ts.isIdentifier(decl.name)) return null;
  const init = decl.initializer;
  if (!init || !ts.isAwaitExpression(init) || !ts.isCallExpression(init.expression)) return null;
  const callee = init.expression.expression;
  return ts.isIdentifier(callee) && callee.text === GUARD ? decl.name.text : null;
}

/** `if (!x.ok) return x.response;`（ブロックで囲んでもよい、else なし） */
function isGuardCheck(stmt: ts.Statement | undefined, name: string): boolean {
  if (!stmt || !ts.isIfStatement(stmt) || stmt.elseStatement) return false;
  const cond = stmt.expression;
  const isNotOk =
    ts.isPrefixUnaryExpression(cond) &&
    cond.operator === ts.SyntaxKind.ExclamationToken &&
    ts.isPropertyAccessExpression(cond.operand) &&
    ts.isIdentifier(cond.operand.expression) &&
    cond.operand.expression.text === name &&
    cond.operand.name.text === "ok";
  let ret: ts.Statement | undefined = stmt.thenStatement;
  if (ts.isBlock(ret)) ret = ret.statements.length === 1 ? ret.statements[0] : undefined;
  const returnsResponse =
    !!ret &&
    ts.isReturnStatement(ret) &&
    !!ret.expression &&
    ts.isPropertyAccessExpression(ret.expression) &&
    ts.isIdentifier(ret.expression.expression) &&
    ret.expression.expression.text === name &&
    ret.expression.name.text === "response";
  return isNotOk && returnsResponse;
}

/** ハンドラー直下で、ガードと結果の確認が決まった形で並び、その前に危険な呼び出しが無いか */
function guardProblem(fn: ts.FunctionDeclaration | null, names: Set<string>): string | null {
  if (!fn?.body) return "export async function の形で書いてください（本体を検査できません）";
  const stmts = fn.body.statements;
  const index = stmts.findIndex((s) => guardVariable(s) !== null);
  if (index < 0) {
    return `ハンドラー直下に const auth = await ${GUARD}(...) がありません（入れ子の関数や try の中は数えません）`;
  }
  const name = guardVariable(stmts[index])!;
  if (!isGuardCheck(stmts[index + 1], name)) {
    return `${GUARD} の直後に if (!${name}.ok) return ${name}.response; を置いてください`;
  }
  const unsafe: string[] = [];
  for (const stmt of stmts.slice(0, index)) {
    walk(stmt, (node) => {
      if (!ts.isCallExpression(node)) return;
      const info = backendCallInfo(node, names);
      if (info) {
        if (info.method === "GET") return;
      } else if (
        (ts.isIdentifier(node.expression) && SAFE_CALLS_BEFORE_GUARD.has(node.expression.text)) ||
        (ts.isPropertyAccessExpression(node.expression) && SAFE_METHODS_BEFORE_GUARD.has(node.expression.name.text))
      ) {
        return;
      }
      unsafe.push(`${node.expression.getText()}()`);
    });
  }
  if (unsafe.length > 0) {
    return `ガードより前に呼べるのは getSession・入力の読み取り・GET の fetchFromBackend だけです: ${unsafe.join(", ")}`;
  }
  return null;
}

/** 書き込みメソッドのハンドラーの外で、GET 以外の fetchFromBackend / fetch を呼んでいる箇所 */
function writesOutsideHandlers(file: ts.SourceFile, handlers: ExportedHandler[], names: Set<string>): string[] {
  const inside = handlers.flatMap((h) => (h.fn ? [h.fn] : []));
  const found: string[] = [];
  walk(file, (node) => {
    if (!ts.isCallExpression(node)) return;
    const info = backendCallInfo(node, names);
    if (!info || info.method === "GET") return;
    if (inside.some((fn) => node.pos >= fn.pos && node.end <= fn.end)) return;
    const line = file.getLineAndCharacterOfPosition(node.getStart()).line + 1;
    found.push(`L${line}: 書き込み（または method が読めない呼び出し）は書き込みメソッドのハンドラーの中で、ガードの後に行ってください`);
  });
  return found;
}

function isUseServer(node: ts.Node): boolean {
  return ts.isExpressionStatement(node) && ts.isStringLiteral(node.expression) && node.expression.text === "use server";
}

describe("書き込みハンドラーのガード", () => {
  const files = new Map<string, ts.SourceFile>();
  for (const rel of [...listFiles(join(WEB_DIR, "app"), "app"), ...listFiles(join(WEB_DIR, "lib"), "lib")]) {
    files.set(rel, parse(rel));
  }
  const routeFiles = [...files.keys()].filter((rel) => rel.startsWith("app/") && ROUTE_FILE.test(rel));

  it("書き込みルートは requireRaidEditPermission を通すか、許可リストにメソッド単位で理由付きで載っている", () => {
    const violations: string[] = [];
    for (const rel of routeFiles) {
      const file = files.get(rel)!;
      const { handlers, problems } = writeExports(file);
      const backend = backendImports(file);
      violations.push(...[...problems, ...backend.problems].map((p) => `${rel} ${p}`));
      for (const { method, fn, line } of handlers) {
        if (ALLOWLIST[rel]?.methods.includes(method)) continue;
        const problem = guardProblem(fn, backend.names);
        if (problem) violations.push(`${rel} L${line} ${method}: ${problem}`);
      }
      violations.push(
        ...[...writesOutsideHandlers(file, handlers, backend.names), ...backendReferences(file, backend.names)].map(
          (p) => `${rel} ${p}`
        )
      );
    }
    assert.deepStrictEqual(
      violations,
      [],
      `lib/raid-authorization.ts の requireRaidEditPermission を通すか、バックエンドに書き込まない理由を許可リストに書いてください:\n${violations.join("\n")}`
    );
  });

  it("Server Action（\"use server\"）は使わない", () => {
    // route handler と同じ検査をしていないため、使うときはこのテストを拡張する
    const actions = [...files].filter(([, file]) => {
      let found = false;
      walk(file, (node) => (found ||= isUseServer(node)));
      return found;
    });
    assert.deepStrictEqual(
      actions.map(([rel]) => rel),
      [],
      "Server Action はガードの検査対象外です"
    );
  });

  it("許可リストの各エントリーは、存在するファイルが export する書き込みメソッドである", () => {
    const stale: string[] = [];
    for (const [rel, { methods }] of Object.entries(ALLOWLIST)) {
      const file = files.get(rel);
      if (!file) {
        stale.push(`${rel}: ファイルが存在しません`);
        continue;
      }
      const exported = writeExports(file).handlers.map((h) => h.method);
      for (const method of methods.filter((m) => !exported.includes(m))) {
        stale.push(`${rel} ${method}: export されていません`);
      }
    }
    assert.deepStrictEqual(stale, [], `許可リストを直してください:\n${stale.join("\n")}`);
  });

  it("許可リストのファイルはバックエンドを呼ばない", () => {
    const calling: string[] = [];
    for (const rel of Object.keys(ALLOWLIST)) {
      const file = files.get(rel);
      if (!file) continue;
      const { names } = backendImports(file);
      walk(file, (node) => {
        if (ts.isCallExpression(node) && backendCallInfo(node, names)) calling.push(`${rel}: ${node.getText()}`);
        if (
          ts.isImportDeclaration(node) &&
          ts.isStringLiteral(node.moduleSpecifier) &&
          /^(?:@\/lib\/|\.)/.test(node.moduleSpecifier.text)
        ) {
          const clause = node.importClause;
          if (clause?.isTypeOnly) return;
          const names = [
            ...(clause?.name ? [clause.name.text] : []),
            ...(clause?.namedBindings && ts.isNamedImports(clause.namedBindings)
              ? clause.namedBindings.elements.filter((e) => !e.isTypeOnly).map((e) => (e.propertyName ?? e.name).text)
              : clause?.namedBindings
                ? ["*"]
                : []),
          ];
          for (const name of names.filter((n) => !ALLOWLIST_SAFE_IMPORTS.has(n))) {
            calling.push(`${rel}: ${node.moduleSpecifier.text} の ${name} は許可リストのファイルから使えません`);
          }
        }
      });
    }
    assert.deepStrictEqual(calling, [], `許可リストのファイルがバックエンドを呼ぶおそれがあります:\n${calling.join("\n")}`);
  });
});
