export interface Guild {
  guildId: string;
  createdAt: string;
}

export interface GuildsResponse {
  guilds: Guild[];
}

export interface Raid {
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
}

export interface RaidsResponse {
  raids: Raid[];
}

export interface RaidDetailResponse {
  raid: Raid;
  participants: Participant[];
  reports: Report[];
}

export interface Participant {
  id: number;
  userId: string;
  username: string;
  score: number;
  joinedAt: string;
}

export interface Report {
  id: number;
  userId: string;
  username: string;
  difficulty: "normal" | "hard";
  is3t: 0 | 1;
  reportedAt: string;
}
