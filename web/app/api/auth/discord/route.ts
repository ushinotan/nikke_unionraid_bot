import { NextResponse } from "next/server";
import { randomBytes } from "crypto";
import { getSession } from "@/lib/session";

const DISCORD_CLIENT_ID = process.env.DISCORD_CLIENT_ID;
const DISCORD_REDIRECT_URI = process.env.DISCORD_REDIRECT_URI || "http://localhost:3000/api/auth/discord/callback";

export async function GET() {
  if (!DISCORD_CLIENT_ID) {
    return NextResponse.json(
      { error: "DISCORD_CLIENT_ID is not configured" },
      { status: 500 }
    );
  }

  const state = randomBytes(16).toString("hex");

  const session = await getSession();
  session.state = state;
  await session.save();

  const params = new URLSearchParams({
    client_id: DISCORD_CLIENT_ID,
    redirect_uri: DISCORD_REDIRECT_URI,
    response_type: "code",
    scope: "identify guilds",
    state,
  });

  const authorizeUrl = `https://discord.com/api/oauth2/authorize?${params.toString()}`;
  return NextResponse.redirect(authorizeUrl);
}
