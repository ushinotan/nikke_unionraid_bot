# コンポーネント（frontend）

共通部品は `web/app/_components/`。

| ファイル | export | 内容 |
|---|---|---|
| `ui.tsx` | `BrandMark`、`SiteHeader`、`LogoutButton`、`Card`、`StateScreen`、`Spinner` | レイアウトと状態表示 |
| `ui.tsx` | `StatusBadge`、`DifficultyBadge`、`RankBadge` | 進行中 / 終了、難易度、順位のバッジ |
| `ui.tsx` | `primaryButtonClass`、`secondaryButtonClass`、`formatDateTime` | ボタンの Tailwind クラス、日時の表示形式 |
| `raid.ts` | `isRaidActive`、`reportTime`、`compareReportsByTime` | レイドが進行中かの判定、報告の並べ替え |
| `use-now.ts` | `useNow` | 現在時刻を一定間隔（既定 30 秒）で更新するフック |

- コンポーネントライブラリは使わず、Tailwind のクラスで組む。
- 新しい共通部品は `_components/` に置く（`_` 付きのフォルダはルートにならない）。
