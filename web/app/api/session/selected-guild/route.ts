import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { validateSnowflakeId } from "@/lib/backend-client";

export async function GET() {
  try {
    const session = await getSession();
    return NextResponse.json({
      selectedGuildId: session.selectedGuildId || null,
      guilds: session.guilds || [],
    });
  } catch (error) {
    console.error("[Session] Failed to get selected guild:", error);
    return NextResponse.json(
      { error: "INTERNAL_ERROR", message: "Failed to get selected guild" },
      { status: 500 }
    );
  }
}

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const { guildId } = body;

    if (typeof guildId !== "string" || !guildId) {
      return NextResponse.json(
        { error: "BAD_REQUEST", message: "guildId is required" },
        { status: 400 }
      );
    }

    const validationError = validateSnowflakeId(guildId, "guildId");
    if (validationError) {
      return NextResponse.json(
        { error: "BAD_REQUEST", message: validationError },
        { status: 400 }
      );
    }

    const session = await getSession();

    if (!session.guildIds?.includes(guildId)) {
      return NextResponse.json(
        { error: "FORBIDDEN", message: "Guild not in user's membership" },
        { status: 403 }
      );
    }

    session.selectedGuildId = guildId;
    await session.save();

    return NextResponse.json({ success: true, selectedGuildId: guildId });
  } catch (error) {
    if (error instanceof SyntaxError) {
      return NextResponse.json(
        { error: "BAD_REQUEST", message: "Invalid JSON" },
        { status: 400 }
      );
    }

    console.error("[Session] Failed to set selected guild:", error);
    return NextResponse.json(
      { error: "INTERNAL_ERROR", message: "Failed to set selected guild" },
      { status: 500 }
    );
  }
}
