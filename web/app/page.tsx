import Link from "next/link";
import { SiteHeader, primaryButtonClass } from "./_components/ui";

const features = [
  {
    title: "レイド一覧",
    body: "ギルドごとのユニオンレイドを、順位・パーセンテージ付きで一覧表示します。",
  },
  {
    title: "ギルド選択",
    body: "所属している複数のギルドを切り替えて閲覧できます。選択はセッションに保存されます。",
  },
  {
    title: "参加者とレポート",
    body: "各レイドの参加者ごとの到達状況と、HARD / NORMAL の難易度レポートを確認できます。",
  },
];

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col">
      <SiteHeader />

      <section className="brand-hero border-b border-line">
        <div className="max-w-6xl mx-auto px-4 py-16 sm:py-24">
          <p className="text-xs font-bold tracking-[0.2em] text-primary mb-3">
            NIKKE UNION RAID TRACKER
          </p>
          <h1 className="text-3xl sm:text-5xl font-bold text-ink tracking-tight leading-tight">
            ユニオンレイドの戦績を、
            <br className="hidden sm:block" />
            ひと目で。
          </h1>
          <p className="mt-5 max-w-xl text-ink-sub leading-relaxed">
            Discord Bot で記録したユニオンレイドの順位・参加者・難易度レポートをまとめて確認できるダッシュボードです。
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/raids" className={`${primaryButtonClass} h-11 px-6 text-base`}>
              レイド一覧を見る
            </Link>
          </div>
        </div>
      </section>

      <main className="max-w-6xl w-full mx-auto px-4 py-10">
        <h2 className="text-sm font-bold text-ink-sub mb-3">できること</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          {features.map((feature) => (
            <div
              key={feature.title}
              className="bg-surface border border-line rounded-md p-5 hover:border-primary-line transition-colors"
            >
              <div className="flex items-center gap-2 mb-2">
                <span className="w-1 h-4 rounded-full bg-primary" aria-hidden />
                <h3 className="font-bold text-ink">{feature.title}</h3>
              </div>
              <p className="text-sm text-ink-sub leading-relaxed">{feature.body}</p>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
