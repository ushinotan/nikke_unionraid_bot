import Link from "next/link";
import type { ReactNode } from "react";

export function BrandMark({ size = 32 }: { size?: number }) {
  return (
    <span
      className="inline-flex items-center justify-center rounded-md bg-primary text-white font-bold tracking-tight shadow-sm"
      style={{ width: size, height: size, fontSize: size * 0.4 }}
      aria-hidden
    >
      UR
    </span>
  );
}

export function SiteHeader({ right }: { right?: ReactNode }) {
  return (
    <header className="bg-surface border-b border-line">
      <div className="brand-stripe h-1" />
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between gap-4">
        <Link href="/" className="flex items-center gap-2.5 min-w-0">
          <BrandMark />
          <span className="font-bold text-ink truncate">
            NIKKE <span className="text-primary">Union Raid</span>
          </span>
        </Link>
        <div className="flex items-center gap-2 shrink-0">{right}</div>
      </div>
    </header>
  );
}

export function LogoutButton() {
  return (
    <form action="/api/auth/logout" method="post">
      <button
        type="submit"
        className="h-8 px-3 rounded border border-line bg-surface text-ink-sub text-xs font-medium hover:bg-surface-sub hover:text-ink transition-colors"
      >
        ログアウト
      </button>
    </form>
  );
}

export function Card({
  title,
  aside,
  children,
  className = "",
}: {
  title?: ReactNode;
  aside?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`bg-surface rounded-md border border-line overflow-hidden ${className}`}>
      {title ? (
        <div className="flex items-center justify-between gap-3 px-4 h-11 border-b border-line-soft">
          <h2 className="text-sm font-bold text-ink flex items-center gap-2">
            <span className="w-1 h-3.5 rounded-full bg-primary" aria-hidden />
            {title}
          </h2>
          {aside ? <div className="text-xs text-ink-mute">{aside}</div> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}

export function StatusBadge({ active }: { active: boolean }) {
  return active ? (
    <span className="inline-flex items-center gap-1.5 px-2 h-6 rounded text-xs font-bold bg-primary-soft text-primary">
      <span className="relative flex w-1.5 h-1.5">
        <span className="absolute inline-flex w-full h-full rounded-full bg-primary opacity-60 animate-ping" />
        <span className="relative inline-flex w-1.5 h-1.5 rounded-full bg-primary" />
      </span>
      進行中
    </span>
  ) : (
    <span className="inline-flex items-center px-2 h-6 rounded text-xs font-bold bg-surface-sub text-ink-mute border border-line-soft">
      終了
    </span>
  );
}

export function DifficultyBadge({ difficulty }: { difficulty: "normal" | "hard" }) {
  return difficulty === "hard" ? (
    <span className="inline-flex items-center px-2 h-6 rounded text-xs font-bold bg-hard-soft text-hard">
      HARD
    </span>
  ) : (
    <span className="inline-flex items-center px-2 h-6 rounded text-xs font-bold bg-normal-soft text-normal">
      NORMAL
    </span>
  );
}

export function RankBadge({ rank }: { rank: number | null }) {
  if (rank === null) {
    return <span className="text-ink-mute">-</span>;
  }
  const tone =
    rank <= 3
      ? "bg-gold-soft text-gold"
      : rank <= 10
        ? "bg-primary-soft text-primary"
        : "bg-surface-sub text-ink-sub";
  return (
    <span className={`inline-flex items-center gap-0.5 px-2 h-6 rounded font-bold tabular ${tone}`}>
      {rank}
      <span className="text-[10px] font-semibold">位</span>
    </span>
  );
}

export function StateScreen({
  tone = "neutral",
  title,
  children,
  action,
}: {
  tone?: "neutral" | "error";
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex-1 flex items-center justify-center px-4 py-16">
      <div className="bg-surface border border-line rounded-md max-w-md w-full overflow-hidden">
        <div className={`h-1 ${tone === "error" ? "bg-hard" : "brand-stripe"}`} />
        <div className="p-6 text-center">
          {title ? (
            <h2 className={`text-base font-bold mb-2 ${tone === "error" ? "text-hard" : "text-ink"}`}>
              {title}
            </h2>
          ) : null}
          {children ? <div className="text-sm text-ink-sub">{children}</div> : null}
          {action ? <div className="mt-5">{action}</div> : null}
        </div>
      </div>
    </div>
  );
}

export function Spinner({ label = "読み込み中..." }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-sm text-ink-mute">
      <span className="w-4 h-4 rounded-full border-2 border-primary-line border-t-primary animate-spin" />
      {label}
    </div>
  );
}

export const primaryButtonClass =
  "inline-flex items-center justify-center h-9 px-4 rounded bg-primary hover:bg-primary-strong text-white text-sm font-bold transition-colors";

export const secondaryButtonClass =
  "inline-flex items-center justify-center h-9 px-4 rounded border border-line bg-surface hover:bg-surface-sub text-ink-sub text-sm font-medium transition-colors";

export function formatDateTime(dateString: string): string {
  return new Date(dateString).toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
