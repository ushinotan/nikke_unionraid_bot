import { NextRequest, NextResponse } from "next/server";
import { getIronSession } from "iron-session";
import { sessionOptions, type SessionData } from "@/lib/session";

const protectedPaths = ["/raids"];
const protectedApiPaths = ["/api/guilds", "/api/raids", "/api/session"];

export async function middleware(request: NextRequest) {
  const path = request.nextUrl.pathname;

  const isProtectedPath = protectedPaths.some((protectedPath) =>
    path.startsWith(protectedPath)
  );
  const isProtectedApi = protectedApiPaths.some((apiPath) =>
    path.startsWith(apiPath)
  );

  if (!isProtectedPath && !isProtectedApi) {
    return NextResponse.next();
  }

  const response = NextResponse.next();
  const session = await getIronSession<SessionData>(
    request,
    response,
    sessionOptions
  );

  if (!session.userId) {
    if (isProtectedApi) {
      return NextResponse.json(
        { error: "Unauthorized", message: "Authentication required" },
        { status: 401 }
      );
    }
    const loginUrl = new URL("/api/auth/discord", request.url);
    return NextResponse.redirect(loginUrl);
  }

  if (isProtectedApi && path.startsWith("/api/guilds/")) {
    const guildIdMatch = path.match(/^\/api\/guilds\/(\d+)/);
    if (guildIdMatch) {
      const requestedGuildId = guildIdMatch[1];
      if (!session.guildIds?.includes(requestedGuildId)) {
        return NextResponse.json(
          { error: "Forbidden", message: "Access to this guild is not allowed" },
          { status: 403 }
        );
      }
    }
  }

  return response;
}

export const config = {
  matcher: [
    "/((?!api/auth|_next/static|_next/image|favicon.ico).*)",
  ],
};
