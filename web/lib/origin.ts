import { NextRequest } from "next/server";

export function getPublicOrigin(request: NextRequest): string {
  const APP_BASE_URL = process.env.APP_BASE_URL;
  const DISCORD_REDIRECT_URI = process.env.DISCORD_REDIRECT_URI;

  if (APP_BASE_URL) {
    try {
      return new URL(APP_BASE_URL).origin;
    } catch (e) {
      console.warn("[getPublicOrigin] Invalid APP_BASE_URL:", APP_BASE_URL, e);
    }
  }

  if (DISCORD_REDIRECT_URI) {
    try {
      return new URL(DISCORD_REDIRECT_URI).origin;
    } catch (e) {
      console.warn("[getPublicOrigin] Invalid DISCORD_REDIRECT_URI:", DISCORD_REDIRECT_URI, e);
    }
  }

  const forwardedProto = request.headers.get("x-forwarded-proto");
  const forwardedHost = request.headers.get("x-forwarded-host");
  if (forwardedProto && forwardedHost) {
    return `${forwardedProto}://${forwardedHost}`;
  }

  return new URL(request.url).origin;
}
