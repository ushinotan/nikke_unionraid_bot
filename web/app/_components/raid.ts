import type { RaidReportDto, RaidSummaryDto } from "@/lib/api-types";

export function isRaidActive(raid: RaidSummaryDto, now: Date): boolean {
  if (raid.finishedAt) {
    return false;
  }
  return now <= new Date(raid.endTime);
}

/** 報告日時のミリ秒。日時が無い・不正なレポートは末尾に回すため +Infinity */
export function reportTime(report: RaidReportDto | null): number {
  const t = report?.reportedAt ? new Date(report.reportedAt).getTime() : Number.NaN;
  return Number.isNaN(t) ? Number.POSITIVE_INFINITY : t;
}

/** 報告日時の昇順。同時刻（日時なし同士を含む）は id でタイブレークする */
export function compareReportsByTime(a: RaidReportDto, b: RaidReportDto): number {
  const ta = reportTime(a);
  const tb = reportTime(b);
  if (ta !== tb) return ta < tb ? -1 : 1;
  return a.id - b.id;
}
