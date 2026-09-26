import { NextResponse } from "next/server";
import {
  fetchFromBackend,
  handleBackendError,
  validateSnowflakeId,
} from "@/lib/backend-client";
import type { RaidsResponse } from "@/lib/api-types";
import { getSession } from "@/lib/session";

export async function GET(
  _request: Request,
  props: { params: Promise<{ guildId: string }> }
) {
  const params = await props.params;
  const { guildId } = params;

  const validationError = validateSnowflakeId(guildId, "guildId");
  if (validationError) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: validationError },
      { status: 400 }
    );
  }

  try {
    const data = await fetchFromBackend<RaidsResponse>(
      `/api/guilds/${encodeURIComponent(guildId)}/raids`
    );

    const session = await getSession();
    const guildMetadata = (session.guilds || []).find((g) => g.id === guildId);

    const enrichedData = {
      ...data,
      raids: data.raids.map((raid) => ({
        ...raid,
        guildName: guildMetadata?.name,
        guildIcon: guildMetadata?.icon,
      })),
    };

    return NextResponse.json(enrichedData);
  } catch (error) {
    return handleBackendError(error);
  }
}
