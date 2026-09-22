"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { GuildDto, RaidSummaryDto } from "@/lib/api-types";

export default function RaidsPage() {
  const [guilds, setGuilds] = useState<GuildDto[]>([]);
  const [selectedGuildId, setSelectedGuildId] = useState<string>("");
  const [raids, setRaids] = useState<RaidSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [raidsLoading, setRaidsLoading] = useState(false);
  const [guildsError, setGuildsError] = useState<string | null>(null);
  const [raidsError, setRaidsError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const fetchGuildsAndSession = async () => {
      try {
        setLoading(true);
        setGuildsError(null);

        const [guildsResponse, sessionResponse] = await Promise.all([
          fetch("/api/guilds"),
          fetch("/api/session/selected-guild"),
        ]);

        if (!guildsResponse.ok) {
          throw new Error("ギルド一覧の取得に失敗しました");
        }

        const guildsData = await guildsResponse.json();
        const sessionData = sessionResponse.ok
          ? await sessionResponse.json()
          : { selectedGuildId: null };

        if (!cancelled) {
          const userGuilds = guildsData.guilds || [];
          setGuilds(userGuilds);

          if (userGuilds.length > 0) {
            const savedGuildId = sessionData.selectedGuildId;
            const isValidSelection =
              savedGuildId &&
              userGuilds.some((g: GuildDto) => g.guildId === savedGuildId);

            setSelectedGuildId(
              isValidSelection ? savedGuildId : userGuilds[0].guildId
            );
          }
        }
      } catch (err) {
        if (!cancelled) {
          setGuildsError(
            err instanceof Error ? err.message : "予期しないエラーが発生しました"
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchGuildsAndSession();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedGuildId) {
      return;
    }

    let cancelled = false;

    const fetchRaids = async () => {
      try {
        setRaidsLoading(true);
        setRaidsError(null);

        await fetch("/api/session/selected-guild", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ guildId: selectedGuildId }),
        });

        const response = await fetch(`/api/guilds/${selectedGuildId}/raids`);
        if (!response.ok) {
          throw new Error("レイド一覧の取得に失敗しました");
        }
        const data = await response.json();

        if (!cancelled) {
          setRaids(data.raids || []);
        }
      } catch (err) {
        if (!cancelled) {
          setRaidsError(
            err instanceof Error ? err.message : "予期しないエラーが発生しました"
          );
        }
      } finally {
        if (!cancelled) {
          setRaidsLoading(false);
        }
      }
    };

    fetchRaids();

    return () => {
      cancelled = true;
    };
  }, [selectedGuildId]);

  const getRaidStatus = (raid: RaidSummaryDto): string => {
    if (raid.finishedAt) {
      return "終了";
    }
    const now = new Date();
    const endTime = new Date(raid.endTime);
    if (now > endTime) {
      return "終了";
    }
    return "進行中";
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString("ja-JP", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
        <div className="text-slate-400 text-lg">読み込み中...</div>
      </div>
    );
  }

  if (guildsError) {
    return (
      <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
        <div className="bg-red-950/50 border border-red-900 rounded-lg p-6 max-w-md">
          <h2 className="text-red-400 text-xl font-bold mb-2">エラー</h2>
          <p className="text-red-300">{guildsError}</p>
          <button
            onClick={() => window.location.reload()}
            className="mt-4 px-4 py-2 bg-red-900 hover:bg-red-800 text-white rounded transition-colors"
          >
            再読み込み
          </button>
        </div>
      </div>
    );
  }

  if (guilds.length === 0) {
    return (
      <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
        <div className="bg-slate-900 border border-slate-800 rounded-lg p-8 max-w-md text-center">
          <h2 className="text-slate-300 text-xl font-bold mb-4">
            ギルドが登録されていません
          </h2>
          <p className="text-slate-500">
            Discord Bot でギルドを登録してください
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0a0a0a]">
      <div className="bg-[#1a1a1a] border-b border-slate-800">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <h1 className="text-2xl font-bold text-white mb-4">
            NIKKE ユニオンレイド 戦績
          </h1>

          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-3">
              <label htmlFor="guild-select" className="text-slate-400 font-medium">
                ギルド:
              </label>
              <select
                id="guild-select"
                value={selectedGuildId}
                onChange={(e) => setSelectedGuildId(e.target.value)}
                className="bg-[#2a2a2a] border border-slate-700 text-slate-200 px-4 py-2 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 hover:bg-[#333333] transition-colors"
              >
                {guilds.map((guild) => (
                  <option key={guild.guildId} value={guild.guildId}>
                    {guild.guildId}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 py-6">
        {raidsLoading ? (
          <div className="text-center py-12 text-slate-400">
            読み込み中...
          </div>
        ) : raidsError ? (
          <div className="bg-red-950/50 border border-red-900 rounded-lg p-6">
            <h3 className="text-red-400 text-lg font-bold mb-2">エラー</h3>
            <p className="text-red-300 mb-4">{raidsError}</p>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-red-900 hover:bg-red-800 text-white rounded transition-colors"
            >
              再読み込み
            </button>
          </div>
        ) : raids.length === 0 ? (
          <div className="bg-slate-900 border border-slate-800 rounded-lg p-8 text-center">
            <p className="text-slate-400">レイドが登録されていません</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-800">
            <table className="w-full">
              <thead className="bg-[#1a1a1a] border-b border-slate-800">
                <tr>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    レイド名
                  </th>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    開始日時
                  </th>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    終了日時
                  </th>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    順位
                  </th>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    パーセンテージ
                  </th>
                  <th className="px-6 py-4 text-left text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    ステータス
                  </th>
                </tr>
              </thead>
              <tbody className="bg-[#0f0f0f] divide-y divide-slate-800">
                {raids.map((raid) => {
                  const status = getRaidStatus(raid);
                  return (
                    <tr
                      key={raid.id}
                      className="hover:bg-[#1a1a1a] transition-colors"
                    >
                      <td className="px-6 py-4">
                        <Link
                          href={`/raids/${raid.id}`}
                          className="text-blue-400 hover:text-blue-300 font-medium transition-colors"
                        >
                          {raid.raidName}
                        </Link>
                      </td>
                      <td className="px-6 py-4 text-slate-300 text-sm">
                        {formatDate(raid.startTime)}
                      </td>
                      <td className="px-6 py-4 text-slate-300 text-sm">
                        {formatDate(raid.endTime)}
                      </td>
                      <td className="px-6 py-4 text-slate-300 font-semibold">
                        {raid.ranking !== null ? (
                          <span className="text-yellow-400">
                            {raid.ranking}位
                          </span>
                        ) : (
                          <span className="text-slate-600">-</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-slate-300">
                        {raid.percentage !== null ? (
                          <span className="font-mono">
                            {raid.percentage.toFixed(2)}%
                          </span>
                        ) : (
                          <span className="text-slate-600">-</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex px-3 py-1 text-xs font-semibold rounded-full ${
                            status === "進行中"
                              ? "bg-green-900/30 text-green-400 border border-green-800"
                              : "bg-slate-800/50 text-slate-400 border border-slate-700"
                          }`}
                        >
                          {status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
