"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import type { RaidDetailResponse, RaidReportDto } from "@/lib/api-types";
import {
  Card,
  DifficultyBadge,
  LogoutButton,
  SiteHeader,
  Spinner,
  StateScreen,
  StatusBadge,
  formatDateTime,
  primaryButtonClass,
} from "../../_components/ui";
import { compareReportsByTime, isRaidActive } from "../../_components/raid";
import { useNow } from "../../_components/use-now";

type Params = Promise<{ id: string }>;

interface MemberProgress {
  userId: string;
  username: string;
  normal: RaidReportDto | null;
  hard: RaidReportDto | null;
}

// 参加者データは Bot が記録していないため、難易度レポートをユーザーごとにまとめて参加者とみなす。
// reports は報告日時の昇順で渡すこと。同じ難易度の報告が複数ある場合は、
// 日時は最初の報告を採用し、3T はいずれかの報告で達成していれば達成とみなす
function aggregateMembers(reports: RaidReportDto[]): MemberProgress[] {
  const byUser = new Map<string, MemberProgress>();
  for (const report of reports) {
    const member = byUser.get(report.userId) ?? {
      userId: report.userId,
      username: report.username,
      normal: null,
      hard: null,
    };
    // 想定外の値は DifficultyBadge と同じく NORMAL として扱う
    const difficulty = report.difficulty === "hard" ? "hard" : "normal";
    const first = member[difficulty];
    if (!first) {
      member[difficulty] = report;
    } else if (report.threeT === 1 && first.threeT !== 1) {
      member[difficulty] = { ...first, threeT: 1 };
    }
    byUser.set(report.userId, member);
  }
  // HARD 到達者を先に、その中では報告が早い順
  const leading = (m: MemberProgress) => (m.hard ?? m.normal) as RaidReportDto;
  return [...byUser.values()].sort((a, b) => {
    if (!!a.hard !== !!b.hard) return a.hard ? -1 : 1;
    return compareReportsByTime(leading(a), leading(b));
  });
}

export default function RaidDetailPage({ params }: { params: Params }) {
  const [raidId, setRaidId] = useState<string>("");
  const [data, setData] = useState<RaidDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const now = useNow();

  useEffect(() => {
    params.then((p) => {
      setRaidId(p.id);
    });
  }, [params]);

  useEffect(() => {
    if (!raidId) {
      return;
    }

    let cancelled = false;

    const fetchRaidDetail = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await fetch(`/api/raids/${raidId}`);
        if (!response.ok) {
          if (response.status === 404) {
            throw new Error("レイドが見つかりませんでした");
          } else if (response.status === 403) {
            throw new Error("このレイドにアクセスする権限がありません");
          } else if (response.status === 400) {
            throw new Error("無効なレイドIDです");
          }
          throw new Error("レイド詳細の取得に失敗しました");
        }
        const json = await response.json();

        if (!cancelled) {
          setData(json);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "予期しないエラーが発生しました"
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchRaidDetail();

    return () => {
      cancelled = true;
    };
  }, [raidId]);

  const header = <SiteHeader right={<LogoutButton />} />;

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col">
        {header}
        <Spinner />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen flex flex-col">
        {header}
        <StateScreen
          tone="error"
          title="エラー"
          action={
            <Link href="/raids" className={primaryButtonClass}>
              一覧に戻る
            </Link>
          }
        >
          {error || "レイドが見つかりませんでした"}
        </StateScreen>
      </div>
    );
  }

  const { raid } = data;
  const active = isRaidActive(raid, now);
  const reports = [...(data.reports ?? [])].sort(compareReportsByTime);
  const members = aggregateMembers(reports);
  const hardCount = reports.filter((r) => r.difficulty === "hard").length;
  const threeTCount = reports.filter((r) => r.threeT === 1).length;

  return (
    <div className="min-h-screen flex flex-col">
      {header}

      <div className="brand-hero border-b border-line">
        <div className="max-w-6xl mx-auto px-4 py-6">
          <Link
            href="/raids"
            className="inline-flex items-center gap-1 text-xs font-medium text-ink-sub hover:text-primary transition-colors mb-3"
          >
            ← レイド一覧
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold text-ink">{raid.raidName}</h1>
            <StatusBadge active={active} />
          </div>
          <p className="mt-1 text-sm text-ink-sub">
            {raid.guildName || raid.guildId}
            <span className="mx-2 text-ink-mute">|</span>
            <span className="tabular">
              {formatDateTime(raid.startTime)} 〜 {formatDateTime(raid.endTime)}
            </span>
          </p>

          <dl className="mt-5 grid grid-cols-3 gap-px bg-line rounded-md overflow-hidden border border-line">
            <Stat label="順位">
              {raid.ranking !== null ? (
                <span className={raid.ranking <= 3 ? "text-gold-ink" : "text-primary"}>
                  {raid.ranking}
                  <span className="text-sm ml-0.5">位</span>
                </span>
              ) : (
                <span className="text-ink-mute">-</span>
              )}
            </Stat>
            <Stat label="パーセンテージ">
              {raid.percentage !== null ? (
                <>
                  {raid.percentage.toFixed(2)}
                  <span className="text-sm ml-0.5">%</span>
                </>
              ) : (
                <span className="text-ink-mute">-</span>
              )}
            </Stat>
            <Stat label="参加者">
              {members.length}
              <span className="text-sm ml-0.5">人</span>
            </Stat>
          </dl>
        </div>
      </div>

      <main className="max-w-6xl w-full mx-auto px-4 py-6 grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] items-start">
        <Card title="参加者" aside={`${members.length}人`}>
          {members.length === 0 ? (
            <p className="py-12 text-center text-sm text-ink-mute">参加者がいません</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface-sub text-ink-mute">
                  <tr className="text-left text-xs">
                    <th className="pl-4 pr-2 py-2.5 font-medium w-10 text-center">#</th>
                    <th className="px-3 py-2.5 font-medium">ユーザー</th>
                    <th className="px-3 py-2.5 font-medium text-center">NORMAL</th>
                    <th className="px-3 py-2.5 font-medium text-center">HARD</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line-soft">
                  {members.map((member, index) => (
                    <tr key={member.userId} className="hover:bg-surface-sub transition-colors">
                      <td className="pl-4 pr-2 py-2.5 text-center text-xs font-bold text-ink-mute tabular">
                        {index + 1}
                      </td>
                      <td className="px-3 py-2.5 font-medium text-ink">{member.username}</td>
                      <td className="px-3 py-2.5 text-center">
                        <ProgressCell report={member.normal} difficulty="normal" />
                      </td>
                      <td className="px-3 py-2.5 text-center">
                        <ProgressCell report={member.hard} difficulty="hard" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card
          title="難易度レポート"
          aside={
            reports.length > 0 ? (
              <span className="tabular">
                <span className="text-hard-ink font-bold">HARD {hardCount}</span>
                <span className="mx-1.5">/</span>
                <span className="text-normal-ink font-bold">NORMAL {reports.length - hardCount}</span>
                <span className="mx-1.5">/</span>
                <span className="text-success-ink font-bold">3T {threeTCount}</span>
              </span>
            ) : undefined
          }
        >
          {reports.length === 0 ? (
            <p className="py-12 text-center text-sm text-ink-mute">レポートがありません</p>
          ) : (
            <ul className="divide-y divide-line-soft">
              {reports.map((report) => (
                <li
                  key={report.id}
                  className={`relative flex items-center gap-3 pl-5 pr-4 py-2.5 ${
                    report.difficulty === "hard" ? "bg-hard-soft/60" : "bg-normal-soft/50"
                  }`}
                >
                  <span
                    className={`absolute left-0 top-0 bottom-0 w-1 ${
                      report.difficulty === "hard" ? "bg-hard" : "bg-normal"
                    }`}
                    aria-hidden
                  />
                  <DifficultyBadge difficulty={report.difficulty} />
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium text-ink truncate">{report.username}</div>
                    <div className="text-xs text-ink-mute tabular">
                      {report.reportedAt ? formatDateTime(report.reportedAt) : "-"}
                    </div>
                  </div>
                  {report.threeT === 1 ? (
                    <span className="inline-flex items-center px-2 h-6 rounded text-xs font-bold bg-success-soft text-success-ink">
                      3T
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </main>
    </div>
  );
}

function Stat({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="bg-surface px-4 py-3">
      <dt className="text-xs text-ink-mute">{label}</dt>
      <dd className="mt-0.5 text-2xl font-bold text-ink tabular">{children}</dd>
    </div>
  );
}

function ProgressCell({
  report,
  difficulty,
}: {
  report: RaidReportDto | null;
  difficulty: "normal" | "hard";
}) {
  if (!report) {
    return <span className="text-ink-mute">-</span>;
  }
  const tone =
    difficulty === "hard" ? "bg-hard-soft text-hard-ink" : "bg-normal-soft text-normal-ink";
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 h-6 rounded text-xs font-bold ${tone}`}
      title={report.reportedAt ? formatDateTime(report.reportedAt) : undefined}
    >
      <span aria-hidden>✓</span>
      <span className="sr-only">報告済み、</span>
      {report.threeT === 1 ? <span>3T</span> : null}
    </span>
  );
}
