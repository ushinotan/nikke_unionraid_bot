export interface GuildDto {
  guildId: string;
  createdAt: string;
  name?: string;
  icon?: string | null;
}

export interface GuildsResponse {
  guilds: GuildDto[];
}

export interface RaidSummaryDto {
  id: number;
  guildId: string;
  raidName: string;
  startTime: string;
  endTime: string;
  notifyTime: string | null;
  channelId: string;
  ranking: number | null;
  percentage: number | null;
  finishedAt: string | null;
  createdAt: string;
  guildName?: string;
  guildIcon?: string | null;
}

export interface RaidsResponse {
  raids: RaidSummaryDto[];
}

export interface ParticipantDto {
  id: number;
  userId: string;
  username: string;
  score: number;
  joinedAt: string | null;
}

export interface RaidReportDto {
  id: number;
  userId: string;
  username: string;
  difficulty: "normal" | "hard";
  threeT: number;
  reportedAt: string | null;
}

export interface RaidDetailResponse {
  raid: RaidSummaryDto;
  participants: ParticipantDto[];
  reports: RaidReportDto[];
}
