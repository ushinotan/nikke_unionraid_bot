const HOST_PATTERN = /^(?:[a-z0-9-]+(?:\.[a-z0-9-]+)*|\[[0-9a-f:]+\])(?::\d{1,5})?$/i;

function parseOrigin(name: string, value: string | undefined): string | null {
  if (!value) {
    return null;
  }
  try {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      console.warn(`[origin] ${name} must be an http(s) URL`);
      return null;
    }
    return url.origin;
  } catch {
    console.warn(`[origin] ${name} is not a valid URL`);
    return null;
  }
}

/**
 * 環境変数から設定された公開 origin を返す（リクエスト非依存）。
 * 優先順位: APP_BASE_URL > DISCORD_REDIRECT_URI の origin
 * Secure Cookie 判定など、リクエストが無い場面でも使える。
 */
export function getConfiguredOrigin(): string | null {
  return (
    parseOrigin("APP_BASE_URL", process.env.APP_BASE_URL) ??
    parseOrigin("DISCORD_REDIRECT_URI", process.env.DISCORD_REDIRECT_URI)
  );
}

function firstHeaderValue(value: string | null): string | null {
  if (!value) {
    return null;
  }
  // 複数プロキシ経由の場合 "a, b" のようにカンマ区切りになる。先頭がクライアントに最も近い値。
  const first = value.split(",")[0]?.trim();
  return first ? first : null;
}

function getForwardedOrigin(request: Request): string | null {
  const proto = firstHeaderValue(request.headers.get("x-forwarded-proto"))?.toLowerCase();
  const host = firstHeaderValue(request.headers.get("x-forwarded-host"));
  if (!proto || !host) {
    return null;
  }
  if (proto !== "http" && proto !== "https") {
    return null;
  }
  if (!HOST_PATTERN.test(host)) {
    return null;
  }
  try {
    return new URL(`${proto}://${host}`).origin;
  } catch {
    return null;
  }
}

/**
 * リダイレクト先に使う公開 origin を解決する。
 * 優先順位:
 *  1. APP_BASE_URL（明示指定）
 *  2. x-forwarded-proto / x-forwarded-host（リバースプロキシ。実際にアクセスされたホストを優先）
 *  3. DISCORD_REDIRECT_URI の origin
 *  4. request.url の origin（ローカル開発用）
 */
export function getPublicOrigin(request: Request): string {
  const explicit = parseOrigin("APP_BASE_URL", process.env.APP_BASE_URL);
  if (explicit) {
    return explicit;
  }

  const forwarded = getForwardedOrigin(request);
  if (forwarded) {
    return forwarded;
  }

  const redirectOrigin = parseOrigin("DISCORD_REDIRECT_URI", process.env.DISCORD_REDIRECT_URI);
  if (redirectOrigin) {
    return redirectOrigin;
  }

  return new URL(request.url).origin;
}
