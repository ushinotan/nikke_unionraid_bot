import { NextResponse } from "next/server";

const SPRING_API_URL =
  process.env.SPRING_API_URL || "http://localhost:8080";

type Params = Promise<{ raidId: string }>;

export async function GET(_request: Request, { params }: { params: Params }) {
  const { raidId } = await params;

  try {
    const response = await fetch(`${SPRING_API_URL}/api/raids/${raidId}`, {
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
    console.error(`Failed to fetch raid ${raidId}:`, error);
    return NextResponse.json(
      { error: "Backend unavailable" },
      { status: 503 }
    );
  }
}
