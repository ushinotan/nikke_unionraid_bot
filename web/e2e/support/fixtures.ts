import { test as base } from "@playwright/test";
import { E2E_BASE_URL, SESSION_COOKIE_NAME } from "./constants";
import { createSessionCookieValue } from "./session";
import type { Persona } from "./session";

type Fixtures = {
  loginAs: (persona: Persona) => Promise<void>;
};

export const test = base.extend<Fixtures>({
  // 第 2 引数は React の use フックと誤認されないよう provide と呼ぶ
  loginAs: async ({ context }, provide) => {
    await provide(async (persona) => {
      const value = await createSessionCookieValue(persona);
      if (value === null) {
        return;
      }
      await context.addCookies([
        {
          name: SESSION_COOKIE_NAME,
          value,
          url: E2E_BASE_URL,
          httpOnly: true,
          sameSite: "Lax",
        },
      ]);
    });
  },
});

export { expect } from "@playwright/test";
