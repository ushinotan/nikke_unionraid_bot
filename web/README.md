This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## NIKKE ユニオンレイド フロントエンド

ユニオンレイドの戦績データを表示するフロントエンドアプリケーションです。

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

### レイド一覧ページ

レイド一覧ページを開くには、[http://localhost:3000/raids](http://localhost:3000/raids) にアクセスしてください。

- ギルドを選択してレイド一覧を表示できます
- レイド名をクリックすると詳細ページに遷移します（詳細な情報は Issue #40 で実装予定）

### 環境変数

Spring Boot API のエンドポイントを設定するには、`.env.local` に以下を追加してください:

```
SPRING_API_URL=http://localhost:8080
```

デフォルトは `http://localhost:8080` です。

## 実装済み機能

- レイド一覧表示 (Issue #39)
  - ギルド選択機能
  - レイド名、開始/終了日時、順位、パーセンテージ、ステータスの表示
  - 進行中/終了ステータスの判定
  - 空状態とエラーハンドリング
- レイド詳細ページ（スタブ、Issue #40 で本実装予定）

## 技術スタック

- Next.js 16 (App Router)
- TypeScript
- Tailwind CSS v4
- React 19

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
