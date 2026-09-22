"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { RaidDetailResponse } from "../../types/api";

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
    if (raidId) {
      fetchRaidDetail();
    }
  }, [raidId]);

  const fetchRaidDetail = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(`/api/raids/${raidId}`);
      if (!response.ok) {
        throw new Error("レイド詳細の取得に失敗しました");
      }
      const json = await response.json();
      setData(json);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "予期しないエラーが発生しました"
      );
    } finally {
      setLoading(false);
    }
  };

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

      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="bg-slate-900 border border-slate-800 rounded-lg p-8 text-center">
          <h2 className="text-slate-300 text-xl font-bold mb-4">
            レイド詳細ページ
          </h2>
          <p className="text-slate-500 mb-6">
            詳細な情報の表示は Issue #40 で実装予定です
          </p>
          <div className="text-left max-w-md mx-auto bg-[#0f0f0f] border border-slate-700 rounded p-4">
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-400">レイドID:</dt>
                <dd className="text-slate-200 font-mono">{data.raid.id}</dd>
              </div>
              <div className="flex justify-between">
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
              <div className="flex justify-between">
                <dt className="text-slate-400">参加者数:</dt>
                <dd className="text-slate-200">{data.participants.length}人</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-400">レポート数:</dt>
                <dd className="text-slate-200">{data.reports.length}件</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
}
