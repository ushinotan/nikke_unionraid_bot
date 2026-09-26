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

    const guildMetadataMap = new Map(
      (session.guilds || []).map((g) => [g.id, g])
    );

    const guildsWithMetadata = filteredGuilds.map((guild) => {
      const metadata = guildMetadataMap.get(guild.guildId);
      return {
        ...guild,
        name: metadata?.name,
        icon: metadata?.icon,
      };
    });

    return NextResponse.json({ guilds: guildsWithMetadata });
  } catch (error) {
    return handleBackendError(error);
  }
}
