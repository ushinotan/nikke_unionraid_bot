import { useEffect, useState } from "react";
import type { RaidReportDto, RaidSummaryDto } from "@/lib/api-types";

export function isRaidActive(raid: RaidSummaryDto, now: Date): boolean {
  if (raid.finishedAt) {
    return false;
  }
  return now <= new Date(raid.endTime);
}

/** 終了時刻を過ぎたら「進行中」表示が切り替わるよう、現在時刻を定期的に更新する */
export function useNow(intervalMs = 30_000): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}

/** 報告日時のミリ秒。日時が無いレポートは末尾に回すため +Infinity */
export function reportTime(report: RaidReportDto | null): number {
  return report?.reportedAt ? new Date(report.reportedAt).getTime() : Number.POSITIVE_INFINITY;
}

/** 報告日時の昇順。同時刻（日時なし同士を含む）は id でタイブレークする */
export function compareReportsByTime(a: RaidReportDto, b: RaidReportDto): number {
  const ta = reportTime(a);
  const tb = reportTime(b);
  if (ta !== tb) return ta < tb ? -1 : 1;
  return a.id - b.id;
}
