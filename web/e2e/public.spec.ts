import { expect, test } from "./support/fixtures";

test("トップページを開くと見出しとレイド一覧へのリンクが表示される", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "レイド一覧を見る" })).toHaveAttribute(
    "href",
    "/raids"
  );
});
