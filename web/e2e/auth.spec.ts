import {
  E2E_BASE_URL,
  E2E_DISCORD_CLIENT_ID,
  E2E_DISCORD_REDIRECT_URI,
  GUILD_A,
  GUILD_B,
  RAID_A1,
  RAID_B1,
  SESSION_COOKIE_NAME,
} from "./support/constants";
import { expect, test } from "./support/fixtures";

interface ErrorBody {
  error: string;
}

test.describe("未ログイン", () => {
  test("未ログインで /raids を開くとログインへ 307 でリダイレクトされる", async ({ request }) => {
    const response = await request.get("/raids", { maxRedirects: 0 });

    expect(response.status()).toBe(307);
    // Location は相対パスで返ることがあるため、baseURL で解決して比べる
    const locationHeader = response.headers()["location"];
    expect(locationHeader).toBeDefined();
    const location = new URL(locationHeader, E2E_BASE_URL);
    expect(location.href).toBe(`${E2E_BASE_URL}/api/auth/discord`);
  });

  test("未ログインで /raids を開くとブラウザが Discord の認可画面へ向かう", async ({ page }) => {
    // リダイレクト先への遷移は page.route で止められないため、
    // playwright.config.ts で discord.com の名前解決を止めて本物の Discord に出ないようにしている
    const authorizeRequest = page.waitForRequest((req) =>
      req.url().startsWith("https://discord.com/api/oauth2/authorize?")
    );

    // 名前解決に失敗して goto は例外になる（想定どおり）。それ以外の失敗は握りつぶさない
    await page.goto("/raids").catch((error: Error) => {
      expect(error.message).toContain("ERR_NAME_NOT_RESOLVED");
    });

    const request = await authorizeRequest;
    expect(request.failure()?.errorText).toContain("ERR_NAME_NOT_RESOLVED");
    const params = new URL(request.url()).searchParams;
    expect(params.get("client_id")).toBe(E2E_DISCORD_CLIENT_ID);
    expect(params.get("redirect_uri")).toBe(E2E_DISCORD_REDIRECT_URI);
  });

  const protectedApis = [
    "/api/guilds",
    `/api/guilds/${GUILD_A.id}/raids`,
    `/api/raids/${RAID_A1.id}`,
    "/api/session/selected-guild",
  ];

  for (const path of protectedApis) {
    test(`未ログインで ${path.replace(GUILD_A.id, "{guildId}")} を呼ぶと 401 が返る`, async ({ request }) => {
      const response = await request.get(path);

      expect(response.status()).toBe(401);
      expect(((await response.json()) as ErrorBody).error).toBe("Unauthorized");
    });
  }

  test("不正なクッキーで保護された API を呼ぶと 401 が返る", async ({ request }) => {
    const response = await request.get("/api/guilds", {
      headers: { Cookie: `${SESSION_COOKIE_NAME}=invalid` },
    });

    expect(response.status()).toBe(401);
    expect(((await response.json()) as ErrorBody).error).toBe("Unauthorized");
  });
});

test.describe("一般メンバー", () => {
  test("所属していないギルドのレイド一覧を呼ぶと 403 が返る", async ({ page, loginAs }) => {
    await loginAs("member");

    // 比較: 所属ギルドは 200（クッキーが効いていることを示す）
    const own = await page.request.get(`/api/guilds/${GUILD_A.id}/raids`);
    expect(own.status()).toBe(200);

    const other = await page.request.get(`/api/guilds/${GUILD_B.id}/raids`);
    expect(other.status()).toBe(403);
    expect(((await other.json()) as ErrorBody).error).toBe("Forbidden");
  });

  test("所属していないギルドのレイド詳細を呼ぶと 403 が返る", async ({ page, loginAs }) => {
    await loginAs("member");

    // 比較: 所属ギルドのレイドは 200
    const own = await page.request.get(`/api/raids/${RAID_A1.id}`);
    expect(own.status()).toBe(200);

    const other = await page.request.get(`/api/raids/${RAID_B1.id}`);
    expect(other.status()).toBe(403);
    expect(((await other.json()) as ErrorBody).error).toBe("FORBIDDEN");
  });

  test("所属していないギルドを選択中のギルドにしようとすると 403 が返る", async ({ page, loginAs }) => {
    await loginAs("member");

    // 比較: 所属ギルドは選択できる
    const own = await page.request.post("/api/session/selected-guild", {
      data: { guildId: GUILD_A.id },
    });
    expect(own.status()).toBe(200);

    const other = await page.request.post("/api/session/selected-guild", {
      data: { guildId: GUILD_B.id },
    });
    expect(other.status()).toBe(403);
    expect(((await other.json()) as ErrorBody).error).toBe("FORBIDDEN");
  });
});
