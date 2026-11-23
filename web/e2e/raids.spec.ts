import {
  GUILD_A,
  GUILD_B,
  GUILD_ERROR,
  RAID_A1,
  RAID_A2,
} from "./support/constants";
import { expect, test } from "./support/fixtures";

test("ログイン済みで /raids を開くとギルド名とレイド一覧が表示される", async ({ page, loginAs }) => {
  await loginAs("member");
  await page.goto("/raids");

  await expect(page.getByRole("heading", { level: 1, name: GUILD_A.name })).toBeVisible();
  await expect(page.getByRole("link", { name: RAID_A1.name })).toBeVisible();
  await expect(page.getByRole("link", { name: RAID_A2.name })).toBeVisible();
  // 所属していないギルドは BFF が絞り込む（残っていればギルド選択の select に変わる）。
  // セッションに名前が無いギルドは画面に ID で出るため、ID で確かめる
  await expect(page.getByRole("combobox")).toHaveCount(0);
  await expect(page.getByText(GUILD_B.id)).toHaveCount(0);
  await expect(page.getByText(GUILD_ERROR.id)).toHaveCount(0);
});

test("レイド一覧からレイドを選ぶと詳細にレイド名が表示される", async ({ page, loginAs }) => {
  await loginAs("member");
  await page.goto("/raids");

  await page.getByRole("link", { name: RAID_A1.name }).click();

  await expect(page).toHaveURL(new RegExp(`/raids/${RAID_A1.id}$`));
  await expect(page.getByRole("heading", { level: 1, name: RAID_A1.name })).toBeVisible();
});

test("bot API がエラーを返すとエラー表示と再読み込みボタンが出る", async ({ page, loginAs }) => {
  await loginAs("errorMember");
  await page.goto("/raids");

  await expect(page.getByText("レイド一覧の取得に失敗しました")).toBeVisible();
  await expect(page.getByRole("button", { name: "再読み込み" })).toBeVisible();
});
