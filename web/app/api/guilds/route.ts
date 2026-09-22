import { NextResponse } from "next/server";
import { fetchFromBackend, BackendError } from "@/lib/backend-client";
import type { GuildsResponse } from "@/lib/api-types";

export async function GET() {
  try {
    const data = await fetchFromBackend<GuildsResponse>("/api/guilds");
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
