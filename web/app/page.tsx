import Link from "next/link";

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-950">
      <main className="flex flex-col items-center gap-8 px-8 py-16 text-center">
        <div className="space-y-4">
          <h1 className="text-5xl font-bold text-slate-900 dark:text-slate-50 tracking-tight">
            NIKKE ユニオンレイド
          </h1>
          <h2 className="text-3xl font-semibold text-slate-700 dark:text-slate-300">
            データフロントエンド
          </h2>
        </div>

        <div className="mt-8 max-w-2xl space-y-4">
          <p className="text-lg text-slate-600 dark:text-slate-400">
            このページは NIKKE ユニオンレイドの戦績データを表示するフロントエンドアプリケーションです。
          </p>
        </div>

        <div className="mt-12 flex flex-col gap-4">
          <Link
            href="/raids"
            className="px-8 py-4 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg shadow-lg transition-colors text-lg"
          >
            レイド一覧を見る
          </Link>
        </div>

        <div className="mt-12 px-8 py-6 bg-white dark:bg-slate-800 rounded-lg shadow-lg border border-slate-200 dark:border-slate-700">
          <h3 className="text-xl font-semibold text-slate-800 dark:text-slate-200 mb-4">
            実装済み機能
          </h3>
          <ul className="text-left space-y-2 text-slate-600 dark:text-slate-400">
            <li>• ユニオンレイド一覧の閲覧</li>
            <li>• ギルド選択</li>
            <li>• レイドステータスの表示</li>
          </ul>
        </div>
      </main>
    </div>
  );
}
