import client from "./client";

export type GameWaitingStatus = "WAITING" | "IN_PROGRESS";

export interface GameWaitingResponse {
  status: GameWaitingStatus;
  nextSeasonNumber: number | null;
  currentDay: number | null;
  nextSeasonStartTime: number | null;
  seasonPhase: string | null;
  phaseRemainingSeconds: number | null;
  gameTime: string | null;
  tick: number | null;
  joinEnabled: boolean | null;
  joinPlayableFromDay: number | null;
  participantCount: number | null;
}

export interface SeasonJoinRequest {
  locationId: number;
  storeName: string;
}

export interface SeasonJoinResponse {
  storeId: number;
  storeName: string;
  balance: number;
  playableFromDay: number;
}

export interface CurrentSeasonTopRankingItem {
  rank: number;
  userId: number;
  nickname: string;
  storeName: string;
  roi: number;
  totalRevenue: number;
  rewardPoints: number;
}

export interface CurrentSeasonTopRankingsResponse {
  seasonId: number;
  rankings: CurrentSeasonTopRankingItem[];
  refreshedAt: string;
}

export interface CurrentSeasonRankingItem {
  rank: number | null;
  userId: number;
  nickname: string;
  storeName: string;
  locationName: string;
  menuName: string;
  roi: number;
  totalRevenue: number;
  rewardPoints: number;
  isBankrupt: boolean;
}

export interface CurrentSeasonFinalRankingsResponse {
  seasonId: number;
  rankings: CurrentSeasonRankingItem[];
  myRankings: CurrentSeasonRankingItem[];
}

export interface SeasonDemoSkipRequest {
  seasonId: number;
}

export interface SeasonDemoSkipResponse {
  seasonId: number;
  status: string;
  demoPlayableDays: number | null;
  message: string;
}

export async function getCurrentSeasonFinalRankings() {
  const { data } = await client.get<CurrentSeasonFinalRankingsResponse>(
    "/api/game/seasons/current/rankings/final",
  );
  return data;
}

export async function reserveSeasonDemoSkip(payload: SeasonDemoSkipRequest) {
  const { data } = await client.post<SeasonDemoSkipResponse>(
    "/api/game/seasons/admin/demo-skip",
    payload,
  );
  return data;
}

export async function getGameWaitingStatus() {
  const { data } = await client.get<GameWaitingResponse>("/api/game/waiting");
  return data;
}

export async function joinCurrentSeason(payload: SeasonJoinRequest) {
  const { data } = await client.post<SeasonJoinResponse>(
    "/api/game/seasons/current/join",
    payload,
  );
  return data;
}

export async function getCurrentSeasonTopRankings() {
  const { data } = await client.get<CurrentSeasonTopRankingsResponse>(
    "/api/game/seasons/current/rankings/top",
  );
  return data;
}

export interface GameDayReportResponse {
  seasonId: number;
  day: number;
  storeName: string;
  locationName: string;
  menuName: string;
  revenue: number;
  totalCost: number;
  visitors: number;
  salesCount: number;
  stockRemaining: number;
  stockDisposedCount: number;
  capture_rate: number;
  change_capture_rate: number;
  dailyRevenue: {
    first: number;
    second: number;
    third: number;
    fourth: number;
    fifth: number;
    sixth: number;
    seventh: number;
  } | null;
  tomorrowWeather: { condition: string } | null;
  isNextDayOrderDay: boolean | null;
  consecutiveDeficitDays: number;
  isBankrupt: boolean;
}

export async function getDayReport(day: number) {
  const { data } = await client.get<GameDayReportResponse>(
    `/api/game/day/reports/${day}`,
  );
  return data;
}

// History helper for report charts. Callers should not use this as a fallback
// for the current day's primary report.
export async function getAllDayReports(startDay: number, endDay: number) {
  const normalizedStartDay = Math.max(1, Math.floor(startDay));
  const normalizedEndDay = Math.max(0, Math.floor(endDay));

  if (normalizedStartDay > normalizedEndDay) {
    return [];
  }

  const promises = Array.from(
    { length: normalizedEndDay - normalizedStartDay + 1 },
    (_, i) => getDayReport(normalizedStartDay + i),
  );
  const results = await Promise.allSettled(promises);
  return results
    .filter(
      (r): r is PromiseFulfilledResult<GameDayReportResponse> =>
        r.status === "fulfilled",
    )
    .map((r) => r.value);
}

// --- 영업 중 페이지용 ---

export interface CustomerTick {
  tick: number;
  customerCount: number;
  unitPrice: number;
  soldUnits: number[];
  baseFloatingPopulation: number;
  populationGrowthRate: number;
  currentFloatingPopulation: number;
  regionStoreCount: number;
  rValue: number;
}

export interface CustomerPlanByHourItem {
  gameHour: number;
  customerCount: number;
}

export type GameTrafficStatus =
  | "VERY_SMOOTH"
  | "SMOOTH"
  | "NORMAL"
  | "CONGESTED"
  | "VERY_CONGESTED";

export interface GameTraffic {
  status: GameTrafficStatus | null;
  value: number | null;
  gameHour: number | null;
  delaySeconds: number | null;
}

export interface GameActionStatus {
  discountUsed: boolean;
  donationUsed: boolean;
  promotionUsed: boolean;
  emergencyUsed: boolean;
  emergencyOrderPending: boolean;
  emergencyOrderArriveAt: string | null;
}

export interface AppliedEvent {
  eventId?: number | null;
  eventCategory?: string | null;
  eventType?: string | null;
  eventName?: string | null;
  newsTitle?: string | null;
  appliedAt: string;
  scope?: { region: number | null; menu: number | null } | null;
  targetRegionId?: number | null;
  targetRegionName?: string | null;
  regionName?: string | null;
  locationName?: string | null;
}

export interface TodayEventScheduleItem {
  time: string;
  type: string;
  scope: { region: number | null; menu: number | null } | null;
  newsTitle: string;
  populationMultiplier: number;
  balanceChange: number | null;
  eventId?: number | null;
  eventName?: string | null;
  eventCategory?: string | null;
  targetRegionId?: number | null;
  targetRegionName?: string | null;
  regionName?: string | null;
  locationName?: string | null;
}

export interface GameStateResponse {
  serverTime: string;
  seasonId: number;
  day: number;
  population: string;
  traffic: GameTraffic | null;
  lastCalculatedAt: string;
  cash: number;
  customerCount: number;
  customerTick: CustomerTick;
  customerPlanByHour?: CustomerPlanByHourItem[] | null;
  todayEventSchedule: TodayEventScheduleItem[];
  inventory: { totalStock: number };
  actionStatus: GameActionStatus;
  appliedEvents: AppliedEvent[];
}

export interface CurrentSeasonTimeResponse {
  seasonPhase: string;
  currentDay: number;
  phaseRemainingSeconds: number;
  serverTime: string;
  seasonStartTime: string;
  gameTime: string | null;
  tick: number | null;
  joinEnabled: boolean;
  joinPlayableFromDay: number | null;
}

export interface ParticipationResponse {
  joinedCurrentSeason: boolean;
  storeAccessible: boolean;
  storeId: number | null;
  storeName: string | null;
  playableFromDay: number | null;
}

export interface GameDayStartResponse {
  startTime: string;
  endTime: string;
  weatherType: string;
  weatherMultiplier: number;
  initialBalance: number;
  initialStock: number;
  eventSchedule: Array<{
    time: string;
    type: string;
    scope: { region: number | null; menu: number | null } | null;
    newsTitle: string;
    populationMultiplier: number;
    balanceChange: number;
  }>;
  marketSnapshot: {
    avgMenuPrice: number;
    regionStoreCount: number;
    totalFloatingPopulation: number;
  };
}

/** 영업일 시작 */
export async function startGameDay() {
  const { data } = await client.get<GameDayStartResponse>("/api/game/day/start");
  return data;
}

/** 실시간 게임 상태 조회 */
export async function getGameDayState() {
  const { data } = await client.get<GameStateResponse>("/api/game/day/state");
  return data;
}

/** 현재 시즌 시간 정보 (타이머 보정용) */
export async function getSeasonTime() {
  const { data } = await client.get<CurrentSeasonTimeResponse>("/api/game/seasons/time");
  return data;
}

export async function getCurrentParticipation() {
  const { data } = await client.get<ParticipationResponse>("/api/game/seasons/current/participation");
  return data;
}
