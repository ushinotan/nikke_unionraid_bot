import type {
  GuildsResponse,
  RaidDetailResponse,
  RaidSummaryDto,
  RaidsResponse,
} from "../../lib/api-types";
import {
  GUILD_A,
  GUILD_B,
  GUILD_ERROR,
  RAID_A1,
  RAID_A2,
  RAID_B1,
} from "./constants";

// 日付は固定の過去日（画面の日付や「進行中」表示はアサーションに使わない）
function raidSummary(
  raid: { id: number; name: string },
  guildId: string
): RaidSummaryDto {
  return {
    id: raid.id,
    guildId,
    raidName: raid.name,
    startTime: "2024-01-01T00:00:00Z",
    endTime: "2024-01-08T00:00:00Z",
    notifyTime: null,
    channelId: "900000000000000999",
    ranking: null,
    percentage: null,
    finishedAt: "2024-01-08T00:00:00Z",
    createdAt: "2024-01-01T00:00:00Z",
  };
}

export const guildsResponse = {
  guilds: [GUILD_A, GUILD_B, GUILD_ERROR].map((g) => ({
    guildId: g.id,
    createdAt: "2024-01-01T00:00:00Z",
  })),
} satisfies GuildsResponse;

export const raidsByGuild: Record<string, RaidsResponse> = {
  [GUILD_A.id]: {
    raids: [raidSummary(RAID_A1, GUILD_A.id), raidSummary(RAID_A2, GUILD_A.id)],
  },
  [GUILD_B.id]: { raids: [raidSummary(RAID_B1, GUILD_B.id)] },
};

export const raidDetails: Record<string, RaidDetailResponse> = {
  [String(RAID_A1.id)]: raidDetail(RAID_A1, GUILD_A.id),
  [String(RAID_A2.id)]: raidDetail(RAID_A2, GUILD_A.id),
  [String(RAID_B1.id)]: raidDetail(RAID_B1, GUILD_B.id),
};

function raidDetail(
  raid: { id: number; name: string },
  guildId: string
): RaidDetailResponse {
  return {
    raid: raidSummary(raid, guildId),
    participants: [],
    reports: [
      {
        id: raid.id * 10 + 1,
        userId: "900000000000000101",
        username: "e2e-user-1",
        difficulty: "normal",
        threeT: 3,
        reportedAt: "2024-01-02T00:00:00Z",
      },
      {
        id: raid.id * 10 + 2,
        userId: "900000000000000102",
        username: "e2e-user-2",
        difficulty: "hard",
        threeT: 1,
        reportedAt: "2024-01-03T00:00:00Z",
      },
    ],
  };
}
