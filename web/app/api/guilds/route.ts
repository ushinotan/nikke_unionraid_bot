import { NextResponse } from "next/server";
import {
  fetchFromBackend,
  handleBackendError,
} from "@/lib/backend-client";
import type { GuildsResponse } from "@/lib/api-types";
import { getSession } from "@/lib/session";

export async function GET() {
  try {
    const session = await getSession();
    const data = await fetchFromBackend<GuildsResponse>("/api/guilds");

    const userGuildIds = session.guildIds || [];
    const filteredGuilds = data.guilds.filter((guild) =>
      userGuildIds.includes(guild.guildId)
    );

    return NextResponse.json({ guilds: filteredGuilds });
  } catch (error) {
    return handleBackendError(error);
  }
}
