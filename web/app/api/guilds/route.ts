import { NextResponse } from "next/server";

const SPRING_API_URL =
  process.env.SPRING_API_URL || "http://localhost:8080";

export async function GET() {
  try {
    const response = await fetch(`${SPRING_API_URL}/api/guilds`, {
      headers: {
        "Content-Type": "application/json",
      },
    });

    if (!response.ok) {
      return NextResponse.json(
        { error: "Backend unavailable" },
        { status: response.status }
      );
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    console.error("Failed to fetch guilds:", error);
    return NextResponse.json(
      { error: "Backend unavailable" },
      { status: 503 }
    );
  }
}
