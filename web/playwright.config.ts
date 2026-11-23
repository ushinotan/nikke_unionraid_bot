import { defineConfig, devices } from "@playwright/test";
import {
  E2E_BACKEND_PORT,
  E2E_BACKEND_URL,
  E2E_BASE_URL,
  E2E_DISCORD_CLIENT_ID,
  E2E_DISCORD_CLIENT_SECRET,
  E2E_DISCORD_REDIRECT_URI,
  E2E_INTERNAL_API_TOKEN,
  E2E_PORT,
  E2E_SESSION_SECRET,
} from "./e2e/support/constants";

const evidenceMode = process.env.EVIDENCE_MODE;
const evidence = Boolean(evidenceMode) && evidenceMode !== "0" && evidenceMode !== "false";

// .env* に本物の値があっても、明示した値が優先される（Next は既存の process.env を上書きしない）
const nextEnv = {
  SESSION_SECRET: E2E_SESSION_SECRET,
  BACKEND_URL: E2E_BACKEND_URL,
  INTERNAL_API_TOKEN: E2E_INTERNAL_API_TOKEN,
  APP_BASE_URL: E2E_BASE_URL,
  DISCORD_REDIRECT_URI: E2E_DISCORD_REDIRECT_URI,
  DISCORD_CLIENT_ID: E2E_DISCORD_CLIENT_ID,
  DISCORD_CLIENT_SECRET: E2E_DISCORD_CLIENT_SECRET,
};

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.spec.ts",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // モックは状態を持たず結果が決まるため、リトライせず失敗をそのまま見せる
  retries: 0,
  workers: process.env.CI ? 2 : undefined,
  outputDir: "test-results",
  // html / junit は outputDir の外に出す（実行開始時に outputDir は消される）
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["junit", { outputFile: "e2e-results/junit.xml" }],
  ],
  use: {
    baseURL: E2E_BASE_URL,
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
    trace: evidence ? "on" : "retain-on-failure",
    screenshot: evidence ? "on" : "only-on-failure",
    video: evidence ? "on" : "retain-on-failure",
    launchOptions: {
      // 本物の Discord へ出ないよう discord.com の名前解決を止める（リダイレクト先は page.route で止められない）
      args: ["--host-resolver-rules=MAP discord.com ~NOTFOUND"],
      ...(evidence ? { slowMo: Number(process.env.SLOW_MO ?? 500) } : {}),
    },
  },
  projects: [
    {
      name: "chromium",
      // devices のビューポートが use より優先されるため、証跡モードの解像度はここで上書きする
      use: {
        ...devices["Desktop Chrome"],
        ...(evidence ? { viewport: { width: 1920, height: 1080 } } : {}),
      },
    },
  ],
  webServer: [
    {
      command: "node --import tsx e2e/support/mock-backend.ts",
      url: `http://127.0.0.1:${E2E_BACKEND_PORT}/health`,
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: `npm run build && npm run start -- --port ${E2E_PORT}`,
      url: E2E_BASE_URL,
      reuseExistingServer: false,
      timeout: 180_000,
      env: nextEnv,
    },
  ],
});
