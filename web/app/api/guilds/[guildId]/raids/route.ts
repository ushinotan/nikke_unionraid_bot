import { NextResponse } from "next/server";
import { fetchFromBackend, BackendError } from "@/lib/backend-client";
import type { RaidsResponse } from "@/lib/api-types";

export async function GET(
  _request: Request,
  props: { params: Promise<{ guildId: string }> }
) {
  const params = await props.params;
  const { guildId } = params;

  try {
    const data = await fetchFromBackend<RaidsResponse>(
      `/api/guilds/${guildId}/raids`
    );
    return NextResponse.json(data);
  } catch (error) {
    if (error instanceof BackendError) {
      return NextResponse.json(
        error.errorData || { error: "BACKEND_ERROR", message: error.message },
        { status: error.status }
      );
    }

    return NextResponse.json(
      { error: "INTERNAL_ERROR", message: "An unexpected error occurred" },
      { status: 500 }
    );
  }
}
