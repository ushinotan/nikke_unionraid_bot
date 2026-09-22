import { NextResponse } from "next/server";
import {
  fetchFromBackend,
  handleBackendError,
  validateNumericId,
} from "@/lib/backend-client";
import type { RaidDetailResponse } from "@/lib/api-types";
import { getSession } from "@/lib/session";

export async function GET(
  _request: Request,
  props: { params: Promise<{ raidId: string }> }
) {
  const params = await props.params;
  const { raidId } = params;

  const validationError = validateNumericId(raidId, "raidId");
  if (validationError) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: validationError },
      { status: 400 }
    );
  }

  try {
    const data = await fetchFromBackend<RaidDetailResponse>(
      `/api/raids/${encodeURIComponent(raidId)}`
    );

    const session = await getSession();
    const userGuildIds = session.guildIds || [];

    if (!userGuildIds.includes(data.raid.guildId)) {
      return NextResponse.json(
        { error: "FORBIDDEN", message: "Access to this raid is not allowed" },
        { status: 403 }
      );
    }

    return NextResponse.json(data);
  } catch (error) {
    return handleBackendError(error);
  }
}
