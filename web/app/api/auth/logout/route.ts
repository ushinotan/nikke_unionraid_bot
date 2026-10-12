import { NextRequest, NextResponse } from "next/server";
import { getSession } from "@/lib/session";
import { getPublicOrigin } from "@/lib/origin";

export async function POST(request: NextRequest) {
  const session = await getSession();
  session.destroy();

  const origin = getPublicOrigin(request);
  return NextResponse.redirect(new URL("/", origin), 303);
}
