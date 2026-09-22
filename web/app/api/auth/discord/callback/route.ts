import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { fetchFromBackend } from "@/lib/backend-client";
import type { GuildsResponse } from "@/lib/api-types";

const DISCORD_CLIENT_ID = process.env.DISCORD_CLIENT_ID;
const DISCORD_CLIENT_SECRET = process.env.DISCORD_CLIENT_SECRET;
const DISCORD_REDIRECT_URI = process.env.DISCORD_REDIRECT_URI || "http://localhost:3000/api/auth/discord/callback";

interface DiscordTokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  scope: string;
}

interface DiscordUser {
  id: string;
  username: string;
  discriminator: string;
  avatar: string | null;
}

interface DiscordGuild {
  id: string;
  name: string;
  icon: string | null;
  owner: boolean;
  permissions: string;
}

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const code = searchParams.get("code");
  const state = searchParams.get("state");

  if (!code || !state) {
    return NextResponse.json(
      { error: "Missing code or state parameter" },
      { status: 400 }
    );
  }

  const session = await getSession();

  if (!session.state || session.state !== state) {
    return NextResponse.json(
      { error: "Invalid state parameter (CSRF check failed)" },
      { status: 400 }
    );
  }

  delete session.state;
  await session.save();

  if (!DISCORD_CLIENT_ID || !DISCORD_CLIENT_SECRET) {
    return NextResponse.json(
      { error: "Discord OAuth credentials are not configured" },
      { status: 500 }
    );
  }

  try {
    const tokenParams = new URLSearchParams({
      client_id: DISCORD_CLIENT_ID,
      client_secret: DISCORD_CLIENT_SECRET,
      grant_type: "authorization_code",
      code,
      redirect_uri: DISCORD_REDIRECT_URI,
    });

    const tokenResponse = await fetch("https://discord.com/api/oauth2/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: tokenParams.toString(),
    });

    if (!tokenResponse.ok) {
      console.error("[Discord OAuth] Token exchange failed:", tokenResponse.status);
      return NextResponse.json(
        { error: "Failed to exchange authorization code" },
        { status: 502 }
      );
    }

    const tokens: DiscordTokenResponse = await tokenResponse.json();

    const [userResponse, guildsResponse] = await Promise.all([
      fetch("https://discord.com/api/users/@me", {
        headers: {
          Authorization: `Bearer ${tokens.access_token}`,
        },
      }),
      fetch("https://discord.com/api/users/@me/guilds", {
        headers: {
          Authorization: `Bearer ${tokens.access_token}`,
        },
      }),
    ]);

    if (!userResponse.ok || !guildsResponse.ok) {
      console.error("[Discord OAuth] Failed to fetch user or guilds");
      return NextResponse.json(
        { error: "Failed to fetch Discord user data" },
        { status: 502 }
      );
    }

    const user: DiscordUser = await userResponse.json();
    let userGuilds: DiscordGuild[] = await guildsResponse.json();

    // Discord API returns max 200 guilds per request, paginate if needed
    while (userGuilds.length % 200 === 0 && userGuilds.length > 0) {
      const lastGuildId = userGuilds[userGuilds.length - 1].id;
      const nextPageResponse = await fetch(
        `https://discord.com/api/users/@me/guilds?after=${lastGuildId}`,
        {
          headers: {
            Authorization: `Bearer ${tokens.access_token}`,
          },
        }
      );

      if (!nextPageResponse.ok) break;

      const nextPage: DiscordGuild[] = await nextPageResponse.json();
      if (nextPage.length === 0) break;

      userGuilds = userGuilds.concat(nextPage);
    }

    const userGuildIds = userGuilds.map((g) => g.id);

    const backendGuilds = await fetchFromBackend<GuildsResponse>("/api/guilds");
    const registeredGuildIds = backendGuilds.guilds.map((g) => g.guildId);

    const intersectedGuildIds = userGuildIds.filter((gid) =>
      registeredGuildIds.includes(gid)
    );

    session.userId = user.id;
    session.guildIds = intersectedGuildIds;

    if (
      session.selectedGuildId &&
      !intersectedGuildIds.includes(session.selectedGuildId)
    ) {
      delete session.selectedGuildId;
    }

    await session.save();

    return NextResponse.redirect(new URL("/raids", request.url));
  } catch (error) {
    console.error("[Discord OAuth] Unexpected error:", error);
    return NextResponse.json(
      { error: "Authentication failed" },
      { status: 500 }
    );
  }
}
