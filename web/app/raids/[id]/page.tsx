"use client";

import { useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import type { RaidDetailResponse } from "@/lib/api-types";
import {
  Card,
  DifficultyBadge,
  LogoutButton,
  Meter,
  SiteHeader,
  Spinner,
  StateScreen,
  StatusBadge,
  formatDateTime,
  primaryButtonClass,
} from "../../_components/ui";

type Params = Promise<{ id: string }>;

export default function RaidDetailPage({ params }: { params: Params }) {
  const [raidId, setRaidId] = useState<string>("");
  const [data, setData] = useState<RaidDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
  const active = !raid.finishedAt && new Date() <= new Date(raid.endTime);
  const participants = [...data.participants].sort((a, b) => b.score - a.score);
  const maxScore = participants[0]?.score ?? 0;
  const totalScore = participants.reduce((sum, p) => sum + p.score, 0);
  const reports = [...data.reports].sort((a, b) => {
    if (!a.reportedAt) return 1;
    if (!b.reportedAt) return -1;
    return new Date(b.reportedAt).getTime() - new Date(a.reportedAt).getTime();
  });
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

          <dl className="mt-5 grid grid-cols-2 sm:grid-cols-4 gap-px bg-line rounded-md overflow-hidden border border-line">
            <Stat label="順位">
              {raid.ranking !== null ? (
                <span className={raid.ranking <= 3 ? "text-gold" : "text-primary"}>
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
              {participants.length}
              <span className="text-sm ml-0.5">人</span>
            </Stat>
            <Stat label="合計スコア">{totalScore.toLocaleString()}</Stat>
          </dl>
        </div>
      </div>

      <main className="max-w-6xl w-full mx-auto px-4 py-6 grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] items-start">
        <Card title="参加者" aside={`${participants.length}人`}>
          {participants.length === 0 ? (
            <p className="py-12 text-center text-sm text-ink-mute">参加者がいません</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface-sub text-ink-mute">
                  <tr className="text-left text-xs">
                    <th className="pl-4 pr-2 py-2.5 font-medium w-10 text-center">#</th>
                    <th className="px-3 py-2.5 font-medium">ユーザー</th>
                    <th className="px-3 py-2.5 font-medium w-48">スコア</th>
                    <th className="px-3 py-2.5 font-medium">参加日時</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line-soft">
                  {participants.map((participant, index) => (
                    <tr key={participant.id} className="hover:bg-surface-sub transition-colors">
                      <td className="pl-4 pr-2 py-2.5 text-center">
                        <span
                          className={`inline-flex items-center justify-center w-6 h-6 rounded text-xs font-bold tabular ${
                            index < 3 ? "bg-primary text-white" : "text-ink-mute"
                          }`}
                        >
                          {index + 1}
                        </span>
                      </td>
                      <td className="px-3 py-2.5 font-medium text-ink">{participant.username}</td>
                      <td className="px-3 py-2.5">
                        <div className="font-bold text-ink tabular">
                          {participant.score.toLocaleString()}
                        </div>
                        <Meter value={maxScore > 0 ? (participant.score / maxScore) * 100 : 0} />
                      </td>
                      <td className="px-3 py-2.5 text-xs text-ink-sub tabular whitespace-nowrap">
                        {participant.joinedAt ? (
                          formatDateTime(participant.joinedAt)
                        ) : (
                          <span className="text-ink-mute">-</span>
                        )}
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
                <span className="text-hard font-bold">HARD {hardCount}</span>
                <span className="mx-1.5">/</span>
                <span className="text-normal font-bold">NORMAL {reports.length - hardCount}</span>
                <span className="mx-1.5">/</span>
                <span className="text-success font-bold">3T {threeTCount}</span>
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
                    <span className="inline-flex items-center px-2 h-6 rounded text-xs font-bold bg-success-soft text-success">
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
