import { NextResponse } from "next/server";
import {
  fetchFromBackend,
  handleBackendError,
} from "@/lib/backend-client";
import type { GuildsResponse } from "@/lib/api-types";

export async function GET() {
  try {
    const data = await fetchFromBackend<GuildsResponse>("/api/guilds");
    return NextResponse.json(data);
  } catch (error) {
    return handleBackendError(error);
  }
}
