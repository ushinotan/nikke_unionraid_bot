"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { RaidDetailResponse } from "@/lib/api-types";

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

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
        <div className="text-slate-400 text-lg">読み込み中...</div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
        <div className="bg-red-950/50 border border-red-900 rounded-lg p-6 max-w-md">
          <h2 className="text-red-400 text-xl font-bold mb-2">エラー</h2>
          <p className="text-red-300">
            {error || "レイドが見つかりませんでした"}
          </p>
          <Link
            href="/raids"
            className="mt-4 inline-block px-4 py-2 bg-red-900 hover:bg-red-800 text-white rounded transition-colors"
          >
            一覧に戻る
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0a0a0a]">
      <div className="bg-[#1a1a1a] border-b border-slate-800">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <Link
            href="/raids"
            className="text-blue-400 hover:text-blue-300 text-sm mb-2 inline-block transition-colors"
          >
            ← レイド一覧に戻る
          </Link>
          <h1 className="text-2xl font-bold text-white">
            {data.raid.raidName}
          </h1>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        <div className="bg-[#1a1a1a] border border-slate-800 rounded-lg p-6">
          <h2 className="text-slate-200 text-lg font-bold mb-4">レイド情報</h2>
          <dl className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">レイドID:</dt>
              <dd className="text-slate-200 font-mono">{data.raid.id}</dd>
            </div>
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">ギルドID:</dt>
              <dd className="text-slate-200 font-mono">{data.raid.guildId}</dd>
            </div>
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">順位:</dt>
              <dd className="text-slate-200">
                {data.raid.ranking !== null ? (
                  <span className="text-yellow-400 font-semibold">
                    {data.raid.ranking}位
                  </span>
                ) : (
                  <span className="text-slate-600">-</span>
                )}
              </dd>
            </div>
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">パーセンテージ:</dt>
              <dd className="text-slate-200">
                {data.raid.percentage !== null ? (
                  <span className="font-mono">{data.raid.percentage.toFixed(2)}%</span>
                ) : (
                  <span className="text-slate-600">-</span>
                )}
              </dd>
            </div>
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">開始日時:</dt>
              <dd className="text-slate-200">
                {new Date(data.raid.startTime).toLocaleString("ja-JP")}
              </dd>
            </div>
            <div className="flex justify-between border-b border-slate-800 pb-2">
              <dt className="text-slate-400">終了日時:</dt>
              <dd className="text-slate-200">
                {new Date(data.raid.endTime).toLocaleString("ja-JP")}
              </dd>
            </div>
          </dl>
        </div>

        <div className="bg-[#1a1a1a] border border-slate-800 rounded-lg p-6">
          <h2 className="text-slate-200 text-lg font-bold mb-4">
            参加者一覧 ({data.participants.length}人)
          </h2>
          {data.participants.length === 0 ? (
            <p className="text-slate-500 text-center py-8">
              参加者がいません
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-[#0f0f0f] border-b border-slate-800">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      ユーザー名
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      スコア
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      参加日時
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {[...data.participants]
                    .sort((a, b) => b.score - a.score)
                    .map((participant) => (
                      <tr key={participant.id} className="hover:bg-[#0f0f0f] transition-colors">
                        <td className="px-4 py-3 text-slate-200">
                          {participant.username}
                        </td>
                        <td className="px-4 py-3 text-slate-200 font-mono font-semibold">
                          {participant.score.toLocaleString()}
                        </td>
                        <td className="px-4 py-3 text-slate-400 text-sm">
                          {participant.joinedAt ? (
                            new Date(participant.joinedAt).toLocaleString("ja-JP")
                          ) : (
                            <span className="text-slate-600">-</span>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="bg-[#1a1a1a] border border-slate-800 rounded-lg p-6">
          <h2 className="text-slate-200 text-lg font-bold mb-4">
            難易度レポート ({data.reports.length}件)
          </h2>
          {data.reports.length === 0 ? (
            <p className="text-slate-500 text-center py-8">
              レポートがありません
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-[#0f0f0f] border-b border-slate-800">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      ユーザー名
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      難易度
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      3T
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      報告日時
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {[...data.reports]
                    .sort((a, b) => {
                      if (!a.reportedAt) return 1;
                      if (!b.reportedAt) return -1;
                      return new Date(b.reportedAt).getTime() - new Date(a.reportedAt).getTime();
                    })
                    .map((report) => (
                      <tr key={report.id} className="hover:bg-[#0f0f0f] transition-colors">
                        <td className="px-4 py-3 text-slate-200">
                          {report.username}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex px-3 py-1 text-xs font-semibold rounded-full ${
                              report.difficulty === "hard"
                                ? "bg-red-900/30 text-red-400 border border-red-800"
                                : "bg-blue-900/30 text-blue-400 border border-blue-800"
                            }`}
                          >
                            {report.difficulty === "hard" ? "HARD" : "NORMAL"}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          {report.is3t === 1 ? (
                            <span className="text-green-400 font-semibold">✓</span>
                          ) : (
                            <span className="text-slate-600">-</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-slate-400 text-sm">
                          {report.reportedAt ? (
                            new Date(report.reportedAt).toLocaleString("ja-JP")
                          ) : (
                            <span className="text-slate-600">-</span>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
