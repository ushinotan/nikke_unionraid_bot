"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { GuildDto, RaidSummaryDto } from "@/lib/api-types";
import {
  Card,
  LogoutButton,
  RankBadge,
  SiteHeader,
  Spinner,
  StateScreen,
  StatusBadge,
  formatDateTime,
  primaryButtonClass,
  secondaryButtonClass,
} from "../_components/ui";

interface GuildMetadata {
  id: string;
  name: string;
  icon?: string | null;
}

function isRaidActive(raid: RaidSummaryDto): boolean {
  if (raid.finishedAt) {
    return false;
  }
  return new Date() <= new Date(raid.endTime);
}

export default function RaidsPage() {
  const [guilds, setGuilds] = useState<GuildDto[]>([]);
  const [guildMetadata, setGuildMetadata] = useState<GuildMetadata[]>([]);
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
          : { selectedGuildId: null, guilds: [] };

        if (!cancelled) {
          const userGuilds = guildsData.guilds || [];
          setGuilds(userGuilds);
          setGuildMetadata(sessionData.guilds || []);

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

        const sessionResponse = await fetch("/api/session/selected-guild", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ guildId: selectedGuildId }),
        });

        if (!sessionResponse.ok) {
          console.warn("Failed to save selected guild to session");
        }

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

  const getGuildDisplayName = (guildId: string): string => {
    const guild = guilds.find((g) => g.guildId === guildId);
    if (guild?.name) {
      return guild.name;
    }
    const metadata = guildMetadata.find((g) => g.id === guildId);
    return metadata?.name || guildId;
  };

  const header = <SiteHeader right={<LogoutButton />} />;

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col">
        {header}
        <Spinner />
      </div>
    );
  }

  if (guildsError) {
    return (
      <div className="min-h-screen flex flex-col">
        {header}
        <StateScreen
          tone="error"
          title="エラー"
          action={
            <button onClick={() => window.location.reload()} className={primaryButtonClass}>
              再読み込み
            </button>
          }
        >
          {guildsError}
        </StateScreen>
      </div>
    );
  }

  if (guilds.length === 0) {
    return (
      <div className="min-h-screen flex flex-col">
        {header}
        <StateScreen title="ギルドが登録されていません">
          Discord Bot でギルドを登録してください
        </StateScreen>
      </div>
    );
  }

  const activeCount = raids.filter((raid) => isRaidActive(raid)).length;

  return (
    <div className="min-h-screen flex flex-col">
      {header}

      <div className="bg-surface border-b border-line">
        <div className="max-w-6xl mx-auto px-4 py-5 flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-bold text-primary mb-1">GUILD</p>
            {guilds.length > 1 ? (
              <div className="relative inline-block max-w-full">
                <label htmlFor="guild-select" className="sr-only">
                  ギルド
                </label>
                <select
                  id="guild-select"
                  value={selectedGuildId}
                  onChange={(e) => setSelectedGuildId(e.target.value)}
                  className="appearance-none max-w-full bg-surface border border-line hover:border-primary-line text-ink text-xl font-bold pl-3 pr-9 h-11 rounded focus:outline-none focus:ring-2 focus:ring-primary-line transition-colors cursor-pointer"
                >
                  {guilds.map((guild) => (
                    <option key={guild.guildId} value={guild.guildId}>
                      {getGuildDisplayName(guild.guildId)}
                    </option>
                  ))}
                </select>
                <span
                  className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-ink-mute text-xs"
                  aria-hidden
                >
                  ▼
                </span>
              </div>
            ) : (
              <h1 className="text-xl font-bold text-ink truncate">
                {getGuildDisplayName(guilds[0].guildId)}
              </h1>
            )}
          </div>

          <dl className="flex gap-6 text-sm">
            <div>
              <dt className="text-xs text-ink-mute">レイド数</dt>
              <dd className="text-lg font-bold text-ink tabular">{raids.length}</dd>
            </div>
            <div>
              <dt className="text-xs text-ink-mute">進行中</dt>
              <dd className="text-lg font-bold text-primary tabular">{activeCount}</dd>
            </div>
          </dl>
        </div>
      </div>

      <main className="max-w-6xl w-full mx-auto px-4 py-6">
        <Card title="レイド一覧" aside={raids.length > 0 ? `${raids.length}件` : undefined}>
          {raidsLoading ? (
            <Spinner />
          ) : raidsError ? (
            <div className="p-6 text-center">
              <p className="text-hard font-bold mb-1">エラー</p>
              <p className="text-sm text-ink-sub mb-4">{raidsError}</p>
              <button onClick={() => window.location.reload()} className={secondaryButtonClass}>
                再読み込み
              </button>
            </div>
          ) : raids.length === 0 ? (
            <p className="py-16 text-center text-sm text-ink-mute">レイドが登録されていません</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface-sub text-ink-mute">
                  <tr className="text-left text-xs">
                    <th className="pl-5 pr-3 py-2.5 font-medium">レイド名</th>
                    <th className="px-3 py-2.5 font-medium">期間</th>
                    <th className="px-3 py-2.5 font-medium text-center">順位</th>
                    <th className="px-3 py-2.5 font-medium text-right">パーセンテージ</th>
                    <th className="px-3 py-2.5 font-medium text-center">ステータス</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line-soft">
                  {raids.map((raid) => {
                    const active = isRaidActive(raid);
                    return (
                      <tr
                        key={raid.id}
                        className={`group transition-colors ${
                          active ? "bg-primary-soft/50 hover:bg-primary-soft" : "hover:bg-surface-sub"
                        }`}
                      >
                        <td className="relative pl-5 pr-3 py-3">
                          <span
                            className={`absolute left-0 top-0 bottom-0 w-1 ${
                              active ? "bg-primary" : "bg-line"
                            }`}
                            aria-hidden
                          />
                          <Link
                            href={`/raids/${raid.id}`}
                            className="font-bold text-ink group-hover:text-primary transition-colors"
                          >
                            {raid.raidName}
                          </Link>
                        </td>
                        <td className="px-3 py-3 text-xs text-ink-sub tabular whitespace-nowrap">
                          <div>{formatDateTime(raid.startTime)}</div>
                          <div className="text-ink-mute">〜 {formatDateTime(raid.endTime)}</div>
                        </td>
                        <td className="px-3 py-3 text-center">
                          <RankBadge rank={raid.ranking} />
                        </td>
                        <td className="px-3 py-3 text-right">
                          {raid.percentage !== null ? (
                            <span className="font-bold text-ink tabular">
                              {raid.percentage.toFixed(2)}%
                            </span>
                          ) : (
                            <span className="text-ink-mute">-</span>
                          )}
                        </td>
                        <td className="px-3 py-3 text-center">
                          <StatusBadge active={active} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </main>
    </div>
  );
}
