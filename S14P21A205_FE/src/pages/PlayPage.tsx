import axios, { type AxiosError } from "axios";
import { useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext, useParams } from "react-router-dom";
import { GAME_EXIT_CODES } from "../api/client";
import type { GameGuardContext } from "../router/GameGuard";
import PlayHeader from "../components/play/PlayHeader";
import EventSidebar, { type GameAlert } from "../components/play/EventSidebar";
import RankingSidebar, { type RankEntry } from "../components/play/RankingSidebar";
import ActionBar, { type ActionType } from "../components/play/ActionBar";
import DiscountModal from "../components/play/modals/DiscountModal";
import EmergencyOrderModal, {
  type CurrentMenuPricing,
  type EmergencyMenuItem,
} from "../components/play/modals/EmergencyOrderModal";
import PromotionModal, {
  type PromotionOption,
} from "../components/play/modals/PromotionModal";
import ShareModal from "../components/play/modals/ShareModal";
import MoveModal, { type MoveRegion } from "../components/play/modals/MoveModal";
import UnityCanvas, { type UnityBridgeHandle } from "../components/play/UnityCanvas";
import {
  getPromotionPrice,
  postDiscount,
  postDonation,
  postEmergencyOrder,
  postPromotion,
  type PromotionType,
} from "../api/action";
import {
  getGameDayState,
  getCurrentSeasonTopRankings,
  startGameDay,
  type CustomerPlanByHourItem,
  type GameStateResponse,
  type GameTrafficStatus,
} from "../api/game";
import { getCurrentOrder, type CurrentOrderResponse } from "../api/order";
import {
  getLocationList,
  getStore,
  getStoreMenus,
  updateStoreLocation,
  type LocationItem,
  type StoreMenuResponse,
} from "../api/store";
import { getNewsRanking, type AreaRankingItemResponse } from "../api/news";
import {
  BUSINESS_CLOSE_HOUR,
  BUSINESS_OPEN_HOUR,
  BUSINESS_SECONDS,
  DAY_SECONDS,
  elapsedToGameTime,
} from "../constants/gameTime";
import { setWeather, startDay, spawnShopAtIndex, setCameraRegion } from "../utils/unity";
import useBrandName from "../hooks/useBrandName";
import { useUserStore } from "../stores/useUserStore";
import { normalizeDiscountMultiplier } from "../utils/dashboardItems";

interface ApiErrorResponse {
  message?: string;
}


const MENU_EMOJI_MAP: Record<number, string> = {
  1: "🍞",
  2: "🍢",
  3: "🍬",
  4: "🍽️",
  5: "🍔",
  6: "🍨",
  7: "🍗",
  8: "🌮",
  9: "🌭",
  10: "🧋",
};

const MENU_EMOJI_BY_NAME: Record<string, string> = {
  빵: "🍞",
  마라꼬치: "🍢",
  젤리: "🍬",
  떡볶이: "🍽️",
  햄버거: "🍔",
  아이스크림: "🍨",
  닭강정: "🍗",
  타코: "🌮",
  핫도그: "🌭",
  버블티: "🧋",
};

interface EventScheduleItem {
  time: string;
  type: string;
  scope: { region: number | null; menu: number | null } | null;
  newsTitle: string;
  populationMultiplier: number;
  balanceChange: number;
}

interface EventTemplate {
  title: string;
  /** $LOC → 지역명, $MENU → 메뉴명 */
  description: string;
}

/** 영업 시작과 동시에 표시할 이벤트 */
const IMMEDIATE_EVENT_NAMES = new Set([
  "대체공휴일", "Substitute Holiday",
  "축제", "Festival",
]);

/** 일반 이벤트 고정 발생 게임 시간 (최대 2개) */
const REGULAR_EVENT_TIMES = ["14:00", "18:00"] as const;

const SEASON_LONG_ALERT_KEYWORDS = [
  "원재료 가격",
  "Price Down",
  "Price Up",
  "감염병",
  "Infectious",
  "지진",
  "Earthquake",
  "침수",
  "Flood",
  "태풍",
  "Typhoon",
  "화재",
  "Fire",
  "정책 변경",
  "Policy Change",
] as const;

function getElapsedAppliedEventSeconds(appliedAt: string) {
  const appliedMs = new Date(appliedAt).getTime();
  if (Number.isNaN(appliedMs)) return null;
  return Math.max(0, (Date.now() - appliedMs) / 1000);
}

function getDaysAgoLabel(appliedAt: string): string {
  const elapsedSec = getElapsedAppliedEventSeconds(appliedAt);
  if (elapsedSec === null) return "이전";
  if (elapsedSec < DAY_SECONDS) return "오늘";
  const daysAgo = Math.max(1, Math.floor(elapsedSec / DAY_SECONDS));
  if (daysAgo === 1) return "어제";
  return `${daysAgo}일 전`;
}

function isSeasonLongAlertEvent(eventName: string, newsTitle: string) {
  const candidates = [eventName, newsTitle].filter(Boolean);
  return candidates.some((candidate) =>
    SEASON_LONG_ALERT_KEYWORDS.some((keyword) => candidate.includes(keyword)),
  );
}

function shouldDisplayCarryOverAlert(appliedAt: string, eventName: string, newsTitle: string) {
  const elapsedSec = getElapsedAppliedEventSeconds(appliedAt);

  if (elapsedSec !== null && elapsedSec < DAY_SECONDS) {
    return true;
  }

  return isSeasonLongAlertEvent(eventName, newsTitle);
}

/** 악재 이벤트 (populationMultiplier < 1, 재난, 원가 상승 등) */
function isBadEvent(event: EventScheduleItem): boolean {
  if (event.populationMultiplier < 1) return true;
  if (event.balanceChange < 0) return true;
  const name = event.type || event.newsTitle;
  if (name.includes("상승") || name.includes("UP")) return true;
  const badKeywords = ["감염병", "지진", "침수", "태풍", "화재", "정책", "정부 방침",
    "Infectious", "Earthquake", "Flood", "Typhoon", "Fire", "Policy"];
  return badKeywords.some((kw) => name.includes(kw));
}

/** eventName(type/newsTitle)→{title, description} 매핑 (DB 한국어 + 영어 fallback) */
const EVENT_INFO: Record<string, EventTemplate> = {
  // 한국어 (data.sql 기준)
  "연예인 등장": { title: "연예인 등장", description: "$LOC에 유명인이 나타났습니다." },
  "빵 원재료 가격 하락": { title: "빵 원가 하락", description: "빵 원재료 시세가 하락했습니다." },
  "마라꼬치 원재료 가격 하락": { title: "마라꼬치 원가 하락", description: "마라꼬치 원재료 시세가 하락했습니다." },
  "젤리 원재료 가격 하락": { title: "젤리 원가 하락", description: "젤리 원재료 시세가 하락했습니다." },
  "떡볶이 원재료 가격 하락": { title: "떡볶이 원가 하락", description: "떡볶이 원재료 시세가 하락했습니다." },
  "햄버거 원재료 가격 하락": { title: "햄버거 원가 하락", description: "햄버거 원재료 시세가 하락했습니다." },
  "아이스크림 원재료 가격 하락": { title: "아이스크림 원가 하락", description: "아이스크림 원재료 시세가 하락했습니다." },
  "닭강정 원재료 가격 하락": { title: "닭강정 원가 하락", description: "닭강정 원재료 시세가 하락했습니다." },
  "타코 원재료 가격 하락": { title: "타코 원가 하락", description: "타코 원재료 시세가 하락했습니다." },
  "핫도그 원재료 가격 하락": { title: "핫도그 원가 하락", description: "핫도그 원재료 시세가 하락했습니다." },
  "버블티 원재료 가격 하락": { title: "버블티 원가 하락", description: "버블티 원재료 시세가 하락했습니다." },
  "빵 원재료 가격 상승": { title: "빵 원가 상승", description: "빵 원재료 시세가 상승했습니다." },
  "마라꼬치 원재료 가격 상승": { title: "마라꼬치 원가 상승", description: "마라꼬치 원재료 시세가 상승했습니다." },
  "젤리 원재료 가격 상승": { title: "젤리 원가 상승", description: "젤리 원재료 시세가 상승했습니다." },
  "떡볶이 원재료 가격 상승": { title: "떡볶이 원가 상승", description: "떡볶이 원재료 시세가 상승했습니다." },
  "햄버거 원재료 가격 상승": { title: "햄버거 원가 상승", description: "햄버거 원재료 시세가 상승했습니다." },
  "아이스크림 원재료 가격 상승": { title: "아이스크림 원가 상승", description: "아이스크림 원재료 시세가 상승했습니다." },
  "닭강정 원재료 가격 상승": { title: "닭강정 원가 상승", description: "닭강정 원재료 시세가 상승했습니다." },
  "타코 원재료 가격 상승": { title: "타코 원가 상승", description: "타코 원재료 시세가 상승했습니다." },
  "핫도그 원재료 가격 상승": { title: "핫도그 원가 상승", description: "핫도그 원재료 시세가 상승했습니다." },
  "버블티 원재료 가격 상승": { title: "버블티 원가 상승", description: "버블티 원재료 시세가 상승했습니다." },
  "대체공휴일": { title: "대체 공휴일", description: "정부가 오늘을 대체 공휴일로 지정했습니다." },
  "정부지원금": { title: "정부 지원금", description: "소상공인 긴급 지원금이 지급되었습니다." },
  "정부 방침 변경": { title: "정책 변경", description: "일회용품 사용 규제 등 정부 방침이 변경되었습니다." },
  "감염병": { title: "감염병 발생", description: "$LOC 일대에 감염병이 확산되고 있습니다." },
  "지진": { title: "지진 발생", description: "$LOC 인근에서 지진이 발생했습니다." },
  "침수": { title: "홍수 발생", description: "$LOC 일대가 침수되었습니다." },
  "태풍": { title: "태풍 접근", description: "$LOC 지역에 태풍이 접근하고 있습니다." },
  "화재": { title: "화재 발생", description: "$LOC 인근에서 화재가 발생했습니다." },
  // 영어 fallback (배포 DB에 영어로 들어간 경우)
  "Celebrity Appearance": { title: "연예인 등장", description: "$LOC에 유명인이 나타났습니다." },
  "Policy Change": { title: "정책 변경", description: "일회용품 사용 규제 등 정부 방침이 변경되었습니다." },
  "Substitute Holiday": { title: "대체 공휴일", description: "정부가 오늘을 대체 공휴일로 지정했습니다." },
  "Government Subsidy": { title: "정부 지원금", description: "소상공인 긴급 지원금이 지급되었습니다." },
  "Infectious Disease": { title: "감염병 발생", description: "$LOC 일대에 감염병이 확산되고 있습니다." },
  "Festival": { title: "축제 개최", description: "$LOC에서 축제가 열리고 있습니다." },
  "Earthquake": { title: "지진 발생", description: "$LOC 인근에서 지진이 발생했습니다." },
  "Flood": { title: "홍수 발생", description: "$LOC 일대가 침수되었습니다." },
  "Typhoon": { title: "태풍 접근", description: "$LOC 지역에 태풍이 접근하고 있습니다." },
  "Fire": { title: "화재 발생", description: "$LOC 인근에서 화재가 발생했습니다." },
  // 원가 변동 영어 fallback
  "Bread Price Down": { title: "빵 원가 하락", description: "빵 원재료 시세가 하락했습니다." },
  "Bread Price Up": { title: "빵 원가 상승", description: "빵 원재료 시세가 상승했습니다." },
  "Mala Skewer Price Down": { title: "마라꼬치 원가 하락", description: "마라꼬치 원재료 시세가 하락했습니다." },
  "Mala Skewer Price Up": { title: "마라꼬치 원가 상승", description: "마라꼬치 원재료 시세가 상승했습니다." },
  "Jelly Price Down": { title: "젤리 원가 하락", description: "젤리 원재료 시세가 하락했습니다." },
  "Jelly Price Up": { title: "젤리 원가 상승", description: "젤리 원재료 시세가 상승했습니다." },
  "Tteokbokki Price Down": { title: "떡볶이 원가 하락", description: "떡볶이 원재료 시세가 하락했습니다." },
  "Tteokbokki Price Up": { title: "떡볶이 원가 상승", description: "떡볶이 원재료 시세가 상승했습니다." },
  "Hamburger Price Down": { title: "햄버거 원가 하락", description: "햄버거 원재료 시세가 하락했습니다." },
  "Hamburger Price Up": { title: "햄버거 원가 상승", description: "햄버거 원재료 시세가 상승했습니다." },
  "Ice Cream Price Down": { title: "아이스크림 원가 하락", description: "아이스크림 원재료 시세가 하락했습니다." },
  "Ice Cream Price Up": { title: "아이스크림 원가 상승", description: "아이스크림 원재료 시세가 상승했습니다." },
  "Dakgangjeong Price Down": { title: "닭강정 원가 하락", description: "닭강정 원재료 시세가 하락했습니다." },
  "Dakgangjeong Price Up": { title: "닭강정 원가 상승", description: "닭강정 원재료 시세가 상승했습니다." },
  "Taco Price Down": { title: "타코 원가 하락", description: "타코 원재료 시세가 하락했습니다." },
  "Taco Price Up": { title: "타코 원가 상승", description: "타코 원재료 시세가 상승했습니다." },
  "Hotdog Price Down": { title: "핫도그 원가 하락", description: "핫도그 원재료 시세가 하락했습니다." },
  "Hotdog Price Up": { title: "핫도그 원가 상승", description: "핫도그 원재료 시세가 상승했습니다." },
  "Bubble Tea Price Down": { title: "버블티 원가 하락", description: "버블티 원재료 시세가 하락했습니다." },
  "Bubble Tea Price Up": { title: "버블티 원가 상승", description: "버블티 원재료 시세가 상승했습니다." },
};

function getEventInfo(
  event: EventScheduleItem,
  locationName: string,
  _menuName: string,
): { title: string; description: string } {
  const template = EVENT_INFO[event.type] ?? EVENT_INFO[event.newsTitle];
  if (!template) {
    const fallbackSource = [event.type, event.newsTitle].find(Boolean) ?? "";

    if (/price down|가격 하락/i.test(fallbackSource)) {
      return { title: "원가 하락", description: "원재료 시세가 하락했습니다." };
    }

    if (/price up|가격 상승/i.test(fallbackSource)) {
      return { title: "원가 상승", description: "원재료 시세가 상승했습니다." };
    }

    return { title: event.newsTitle, description: "새로운 이벤트가 발생했습니다." };
  }
  let description = template.description.replace("$LOC", locationName);

  // 지원금 이벤트의 경우 금액 표시
  if (event.balanceChange > 0) {
    description += ` (${event.balanceChange.toLocaleString()}원)`;
  }

  return { title: template.title, description };
}

const RANKING_POLL_INTERVAL_MS = 10_000;

const promotionLabels: Record<string, string> = {
  influencer: "인플루언서 홍보",
  sns: "SNS 홍보",
  flyer: "전단지 배포",
  referral: "지인 소개",
};

const persistentActionTypes = new Set<ActionType>(["discount", "promotion", "share"]);

const PROMOTION_OPTION_META: Record<
  PromotionType,
  Omit<PromotionOption, "id" | "price">
> = {
  INFLUENCER: { icon: "📣", name: "인플루언서 홍보", multiplier: 1.2 },
  SNS: { icon: "📱", name: "SNS 홍보", multiplier: 1.15 },
  LEAFLET: { icon: "📰", name: "전단지 배포", multiplier: 1.1 },
  FRIEND: { icon: "🫶", name: "지인 소개", multiplier: 1.05 },
};

const DEFAULT_PROMOTION_PRICES: Record<PromotionType, number> = {
  INFLUENCER: 50_000,
  SNS: 30_000,
  LEAFLET: 10_000,
  FRIEND: 0,
};

const PROMOTION_LABELS: Record<PromotionType, string> = {
  INFLUENCER: "인플루언서 홍보",
  SNS: "SNS 홍보",
  LEAFLET: "전단지 배포",
  FRIEND: "지인 소개",
};

promotionLabels.INFLUENCER = PROMOTION_LABELS.INFLUENCER;
promotionLabels.SNS = PROMOTION_LABELS.SNS;
promotionLabels.LEAFLET = PROMOTION_LABELS.LEAFLET;
promotionLabels.FRIEND = PROMOTION_LABELS.FRIEND;

function buildPromotionOptions(prices?: Partial<Record<PromotionType, number>>): PromotionOption[] {
  return (Object.keys(PROMOTION_OPTION_META) as PromotionType[]).map((type) => ({
    id: type,
    ...PROMOTION_OPTION_META[type],
    price: prices?.[type] ?? DEFAULT_PROMOTION_PRICES[type],
  }));
}

const LOCATION_ICON_MAP: Record<string, string> = {
  홍대: "🎸",
  신도림: "🚉",
  성수: "🏭",
  "서울숲/성수": "🌳",
  성수동: "🏭",
  명동: "🛍️",
  이태원: "🌍",
  건대: "🎓",
  강남: "💎",
  여의도: "💼",
  잠실: "🎡",
  사의동: "🍽️",
};

function getMoveCost(rent: number) {
  return Math.round(rent * 7 * 0.1);
}

function normalizeAreaName(value: string) {
  return value.trim();
}

function resolvePopupStoreIndex(locationId: number | null | undefined) {
  if (typeof locationId !== "number") {
    return null;
  }

  const popupStoreIndex = locationId - 1;
  return popupStoreIndex >= 0 && popupStoreIndex < 8 ? popupStoreIndex : null;
}

function buildAreaTrafficRankMap(items: AreaRankingItemResponse[]) {
  return new Map(
    items.map((item) => [normalizeAreaName(item.areaName), item.rank] as const),
  );
}

function mapLocationToMoveRegion(
  location: LocationItem,
  trafficRankByAreaName: ReadonlyMap<string, number>,
): MoveRegion {
  return {
    id: location.locationId,
    name: location.locationName,
    rent: location.rent,
    moveCost: getMoveCost(location.rent),
    trafficRank: trafficRankByAreaName.get(normalizeAreaName(location.locationName)) ?? null,
    icon: LOCATION_ICON_MAP[location.locationName] ?? "📍",
  };
}

function resolveMenuEmoji(menuId: number, menuName: string) {
  return MENU_EMOJI_MAP[menuId] ?? MENU_EMOJI_BY_NAME[menuName.trim()] ?? "🍽️";
}

function mapStoreMenusToEmergencyMenus(menus: StoreMenuResponse[]): EmergencyMenuItem[] {
  return menus.map((menu) => ({
    menuId: menu.menuId,
    name: menu.menuName,
    ingredientPrice: menu.ingredientPrice,
    ingredientDiscountMultiplier: normalizeDiscountMultiplier(menu.discount),
    emoji: resolveMenuEmoji(menu.menuId, menu.menuName),
  }));
}

function getErrorMessage(error: unknown, fallbackMessage: string) {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.message ?? fallbackMessage;
  }

  return fallbackMessage;
}

function formatEmergencyArrivalGameTime(arrivedTime: string, businessEndMs: number) {
  const parsed = new Date(arrivedTime);

  if (Number.isNaN(parsed.getTime())) {
    return "";
  }

  const businessStartMs = businessEndMs - BUSINESS_SECONDS * 1000;
  const elapsedSec = Math.max(0, Math.min((parsed.getTime() - businessStartMs) / 1000, BUSINESS_SECONDS));
  return elapsedToGameTime(elapsedSec);
}

function getEstimatedEmergencyArrivalTime(
  serverTime: string | null | undefined,
  delaySeconds: number | null | undefined,
) {
  if (!serverTime || typeof delaySeconds !== "number" || delaySeconds < 0) {
    return null;
  }

  const parsedServerTime = new Date(serverTime);

  if (Number.isNaN(parsedServerTime.getTime())) {
    return null;
  }

  return new Date(parsedServerTime.getTime() + delaySeconds * 1000).toISOString();
}

function getDiscountedPrice(
  currentPrice: number,
  _minimumPrice: number,
  discountRate: number,
) {
  return Math.max(0, Math.round(currentPrice * (1 - discountRate / 100)));
}

function getTrafficStatusLabel(status: GameTrafficStatus | null | undefined) {
  switch (status) {
    case "VERY_SMOOTH":
      return "매우 원활";
    case "SMOOTH":
      return "원활";
    case "NORMAL":
      return "보통";
    case "CONGESTED":
      return "혼잡";
    case "VERY_CONGESTED":
      return "매우 혼잡";
    default:
      return null;
  }
}

type UnityCongestionLevel = 1 | 2 | 3 | 4 | 5;

type HeaderCongestionLevel =
  | "very_crowded"
  | "crowded"
  | "normal"
  | "relaxed"
  | "very_relaxed";

const TRAFFIC_STATUS_TO_UNITY_LEVEL: Record<GameTrafficStatus, UnityCongestionLevel> = {
  VERY_SMOOTH: 1,
  SMOOTH: 2,
  NORMAL: 3,
  CONGESTED: 4,
  VERY_CONGESTED: 5,
};

const BUSINESS_HOUR_COUNT = BUSINESS_CLOSE_HOUR - BUSINESS_OPEN_HOUR;
const BUSINESS_SECONDS_PER_HOUR = BUSINESS_SECONDS / BUSINESS_HOUR_COUNT;

function clampNumber(value: number, min: number, max: number) {
  return Math.max(min, Math.min(value, max));
}

function getElapsedBusinessSeconds(remainingMilliseconds: number) {
  return clampNumber(BUSINESS_SECONDS - remainingMilliseconds / 1000, 0, BUSINESS_SECONDS);
}

function getBusinessHourWindowSeconds(gameHour: number) {
  if (!Number.isFinite(gameHour) || gameHour < BUSINESS_OPEN_HOUR || gameHour >= BUSINESS_CLOSE_HOUR) {
    return null;
  }

  const hourOffset = gameHour - BUSINESS_OPEN_HOUR;
  const start = hourOffset * BUSINESS_SECONDS_PER_HOUR;
  const end = start + BUSINESS_SECONDS_PER_HOUR;

  return { start, end };
}

function getUnityCongestionLevel(status: GameTrafficStatus | null | undefined) {
  if (!status) {
    return null;
  }

  return TRAFFIC_STATUS_TO_UNITY_LEVEL[status];
}

function getHeaderCongestionLevel(status: GameTrafficStatus | null | undefined): HeaderCongestionLevel {
  switch (status) {
    case "VERY_SMOOTH":
      return "very_relaxed";
    case "SMOOTH":
      return "relaxed";
    case "NORMAL":
      return "normal";
    case "CONGESTED":
      return "crowded";
    case "VERY_CONGESTED":
      return "very_crowded";
    default:
      return "normal";
  }
}

export default function PlayPage() {
  const { day } = useParams<{ day: string }>();
  const guardContext = useOutletContext<GameGuardContext>();
  const dayNumber = useMemo(() => Number(day) || 1, [day]);

  return (
    <PlayPageSession
      key={dayNumber}
      dayNumber={dayNumber}
      phaseEndTimestamp={guardContext.phaseEndTimestamp}
    />
  );
}

function PlayPageSession({
  dayNumber,
  phaseEndTimestamp,
}: {
  dayNumber: number;
  phaseEndTimestamp: number;
}) {
  const nickname = useUserStore((s) => s.nickname) ?? "버블티";
  const { brandName } = useBrandName();
  const [activeModal, setActiveModal] = useState<ActionType | null>(null);
  const [usedActions, setUsedActions] = useState<Set<ActionType>>(new Set());
  const [activeEffects, setActiveEffects] = useState<Set<ActionType>>(new Set());
  const [alerts, setAlerts] = useState<GameAlert[]>([]);
  const [eventSchedule, setEventSchedule] = useState<EventScheduleItem[]>([]);
  const unityIframeRef = useRef<HTMLIFrameElement>(null);
  const [unityReady, setUnityReady] = useState(false);
  const [dayWeatherType, setDayWeatherType] = useState<string | null>(null);
  const [storeRegionIndex, setStoreRegionIndex] = useState<number | null>(null);
  const hasLoadedCarryOverRef = useRef(false);
  const [balance, setBalance] = useState(0);
  const [stock, setStock] = useState(0);
  const [guests, setGuests] = useState(0);
  const [currentLocationName, setCurrentLocationName] = useState("");
  const currentLocationIdRef = useRef<number | null>(null);
  const locationIdByNameRef = useRef<ReadonlyMap<string, number>>(new Map());
  const scheduledVisitorTimersRef = useRef<number[]>([]);
  const dispatchedVisitorsByHourRef = useRef<Map<number, number>>(new Map());
  const latestCustomerPlanRef = useRef<CustomerPlanByHourItem[]>([]);
  const latestBackendCustomerCountRef = useRef(0);
  const [currentOrder, setCurrentOrder] = useState<CurrentOrderResponse | null>(null);
  const [menuItems, setMenuItems] = useState<EmergencyMenuItem[]>([]);
  const [moveRegions, setMoveRegions] = useState<MoveRegion[]>([]);
  const [promotionOptions, setPromotionOptions] = useState<PromotionOption[]>(() =>
    buildPromotionOptions(),
  );
  const [trafficStatus, setTrafficStatus] = useState<GameTrafficStatus | null>(null);
  const [deliveryTrafficLabel, setDeliveryTrafficLabel] = useState<string | null>(null);
  const [emergencyArriveAt, setEmergencyArriveAt] = useState<string | null>(null);
  const [estimatedEmergencyArriveAt, setEstimatedEmergencyArriveAt] = useState<string | null>(null);
  const [isEmergencyDataLoading, setIsEmergencyDataLoading] = useState(true);
  const [emergencyDataError, setEmergencyDataError] = useState<string | null>(null);
  const [isMoveDataLoading, setIsMoveDataLoading] = useState(true);
  const [moveDataError, setMoveDataError] = useState<string | null>(null);
  const playEndTimestampMs = phaseEndTimestamp;
  const [nowMs, setNowMs] = useState(() => Date.now());
  const hasDeadlineAlertRef = useRef(false);
  const hasLowStockAlertRef = useRef(false);
  const remainingMilliseconds = Math.max(0, playEndTimestampMs - nowMs);
  const remainingSeconds = Math.max(0, Math.ceil(remainingMilliseconds / 1000));
  const remainingMillisecondsRef = useRef(remainingMilliseconds);
  remainingMillisecondsRef.current = remainingMilliseconds;
  const playStoreName = brandName || "";
  const currentMenuName = currentOrder?.menuName ?? "";
  const displayedEmergencyArriveAt = emergencyArriveAt ?? estimatedEmergencyArriveAt;
  const emergencyArrivalGameTime = displayedEmergencyArriveAt
    ? formatEmergencyArrivalGameTime(displayedEmergencyArriveAt, playEndTimestampMs) || null
    : null;
  const currentMenuPricing: CurrentMenuPricing | null = currentOrder
    ? {
        costPrice: currentOrder.costPrice,
        recommendedPrice: currentOrder.recommendedPrice,
        maxSellingPrice: currentOrder.maxSellingPrice,
        sellingPrice: currentOrder.sellingPrice,
      }
    : null;
  const discountCurrentPrice = currentOrder?.sellingPrice ?? 0;
  const discountMinimumPrice = currentOrder?.costPrice ?? discountCurrentPrice;

  const syncActionUsageState = (action: ActionType, isUsed: boolean) => {
    setUsedActions((prev) => {
      const next = new Set(prev);

      if (isUsed) {
        next.add(action);
      } else {
        next.delete(action);
      }

      return next;
    });

    if (!persistentActionTypes.has(action)) {
      return;
    }

    setActiveEffects((prev) => {
      const next = new Set(prev);

      if (isUsed) {
        next.add(action);
      } else {
        next.delete(action);
      }

      return next;
    });
  };

  const syncDiscountActionState = (discountUsed: boolean) => {
    syncActionUsageState("discount", discountUsed);
  };

  const syncPromotionActionState = (promotionUsed: boolean) => {
    syncActionUsageState("promotion", promotionUsed);
  };

  const syncShareActionState = (donationUsed: boolean) => {
    syncActionUsageState("share", donationUsed);
  };

  const syncEmergencyActionState = (emergencyUsed: boolean) => {
    syncActionUsageState("emergency", emergencyUsed);
  };

  const [guestsDelta, setGuestsDelta] = useState<number | null>(null);
  const [stockDelta, setStockDelta] = useState<number | null>(null);
  const [balanceDelta, setBalanceDelta] = useState<number | null>(null);
  const unityBridgeRef = useRef<UnityBridgeHandle | null>(null);
  const latestTrafficStatusRef = useRef<GameTrafficStatus | null>(null);
  const lastUnityCongestionLevelRef = useRef<UnityCongestionLevel | null>(null);
  // ref로 최신 값 추적 (클로저 캡처 문제 방지)
  const prevGuestsRef = useRef<number | null>(null);
  const prevStockRef = useRef<number | null>(null);
  const prevBalanceRef = useRef<number | null>(null);

  const clearScheduledVisitorTimers = () => {
    for (const timerId of scheduledVisitorTimersRef.current) {
      window.clearTimeout(timerId);
    }

    scheduledVisitorTimersRef.current = [];
  };

  const spawnPopupVisitorsImmediately = (popupStoreIndex: number, count: number) => {
    const totalCount = Math.max(0, Math.floor(count));
    let didSendAny = false;

    for (let index = 0; index < totalCount; index += 1) {
      const didSend = unityBridgeRef.current?.spawnSinglePopupVisitor(popupStoreIndex) ?? false;
      didSendAny = didSend || didSendAny;
    }

    return didSendAny;
  };

  const schedulePlannedVisitors = (
    customerPlanByHour: CustomerPlanByHourItem[] | null | undefined,
    backendCustomerCount: number,
  ) => {
    const normalizedPlan = [...(customerPlanByHour ?? [])]
      .filter(
        (item) =>
          Number.isFinite(item.gameHour) &&
          Number.isFinite(item.customerCount) &&
          item.customerCount > 0,
      )
      .sort((a, b) => a.gameHour - b.gameHour);

    latestCustomerPlanRef.current = normalizedPlan;
    latestBackendCustomerCountRef.current = backendCustomerCount;

    clearScheduledVisitorTimers();

    if (normalizedPlan.length === 0) {
      return;
    }

    const popupStoreIndex = resolvePopupStoreIndex(currentLocationIdRef.current);

    if (popupStoreIndex === null) {
      return;
    }

    const elapsedBusinessSeconds = getElapsedBusinessSeconds(remainingMillisecondsRef.current);
    let cumulativePlannedCustomers = 0;

    for (const planItem of normalizedPlan) {
      const hourWindow = getBusinessHourWindowSeconds(planItem.gameHour);
      const plannedCustomers = Math.max(0, Math.floor(planItem.customerCount));

      if (!hourWindow || plannedCustomers <= 0) {
        cumulativePlannedCustomers += plannedCustomers;
        continue;
      }

      if (elapsedBusinessSeconds >= hourWindow.end) {
        cumulativePlannedCustomers += plannedCustomers;
        continue;
      }

      const dispatchedCustomers = dispatchedVisitorsByHourRef.current.get(planItem.gameHour) ?? 0;
      let remainingCustomers = Math.max(0, plannedCustomers - dispatchedCustomers);

      if (elapsedBusinessSeconds >= hourWindow.start) {
        const realizedCurrentHour = clampNumber(
          backendCustomerCount - cumulativePlannedCustomers,
          0,
          plannedCustomers,
        );

        remainingCustomers = Math.max(
          0,
          plannedCustomers - Math.max(dispatchedCustomers, realizedCurrentHour),
        );
      }

      cumulativePlannedCustomers += plannedCustomers;

      if (remainingCustomers <= 0) {
        continue;
      }

      const scheduleWindowStart = Math.max(elapsedBusinessSeconds, hourWindow.start);
      const delayMs = Math.max(0, Math.round((scheduleWindowStart - elapsedBusinessSeconds) * 1000));

      const timerId = window.setTimeout(() => {
        const didSend = spawnPopupVisitorsImmediately(popupStoreIndex, remainingCustomers);

        if (!didSend) {
          return;
        }

        dispatchedVisitorsByHourRef.current.set(
          planItem.gameHour,
          (dispatchedVisitorsByHourRef.current.get(planItem.gameHour) ?? 0) + remainingCustomers,
        );
      }, delayMs);

      scheduledVisitorTimersRef.current.push(timerId);
    }
  };

  const syncUnityCongestionLevel = (status: GameTrafficStatus | null | undefined) => {
    latestTrafficStatusRef.current = status ?? null;

    const nextLevel = getUnityCongestionLevel(status);

    if (nextLevel === null || lastUnityCongestionLevelRef.current === nextLevel) {
      return;
    }

    const didSend = unityBridgeRef.current?.setCongestionLevel(nextLevel) ?? false;

    if (didSend) {
      lastUnityCongestionLevelRef.current = nextLevel;
    }
  };

  // storeRegionIndex가 확정되면 Unity ready 전에 미리 전송 → 큐에 쌓여서 ready 시 가장 먼저 실행
  useEffect(() => {
    if (storeRegionIndex !== null) {
      spawnShopAtIndex(unityIframeRef, storeRegionIndex);
      setCameraRegion(unityIframeRef, storeRegionIndex);
    }
  }, [storeRegionIndex]);

  const handleUnityReady = () => {
    // spawnShop + setCameraRegion은 이미 큐에 들어있으므로 여기서는 생략
    if (dayWeatherType !== null) {
      setWeather(unityIframeRef, dayWeatherType);
    }
    const remaining = Math.max(0, Math.ceil((playEndTimestampMs - Date.now()) / 1000));
    startDay(unityIframeRef, remaining);
    lastUnityCongestionLevelRef.current = null;
    syncUnityCongestionLevel(latestTrafficStatusRef.current);
    schedulePlannedVisitors(latestCustomerPlanRef.current, latestBackendCustomerCountRef.current);
  };

  const handlePopupArrival = (popupStoreIndex: number | null) => {
    const currentPopupStoreIndex = resolvePopupStoreIndex(currentLocationIdRef.current);

    if (currentPopupStoreIndex === null) {
      return;
    }

    if (popupStoreIndex !== null && popupStoreIndex !== currentPopupStoreIndex) {
      return;
    }

    setGuests((prev) => prev + 1);
  };

  const applyGameState = (state: GameStateResponse) => {
    const hasCustomerPlan =
      Array.isArray(state.customerPlanByHour) && state.customerPlanByHour.length > 0;
    // 이전 값이 있으면 delta 계산
    if (prevGuestsRef.current !== null) {
      const gd = state.customerCount - prevGuestsRef.current;
      const sd = state.inventory.totalStock - prevStockRef.current!;
      const bd = state.cash - prevBalanceRef.current!;
      if (gd !== 0) setGuestsDelta(gd);
      if (sd !== 0) setStockDelta(sd);
      if (bd !== 0) setBalanceDelta(bd);
      if (!hasCustomerPlan && gd > 0) {
        const popupStoreIndex = resolvePopupStoreIndex(currentLocationIdRef.current);

        if (popupStoreIndex !== null) {
          spawnPopupVisitorsImmediately(popupStoreIndex, gd);
        }
      }
    }

    // 현재 값을 ref에 저장 (다음 비교용)
    prevGuestsRef.current = state.customerCount;
    prevStockRef.current = state.inventory.totalStock;
    prevBalanceRef.current = state.cash;

    setBalance(state.cash);
    setStock(state.inventory.totalStock);
    setGuests(state.customerCount);
    setTrafficStatus(state.traffic?.status ?? null);
    setDeliveryTrafficLabel(getTrafficStatusLabel(state.traffic?.status));
    syncUnityCongestionLevel(state.traffic?.status);
    schedulePlannedVisitors(state.customerPlanByHour, state.customerCount);
    const estimatedEmergencyArriveAt = getEstimatedEmergencyArrivalTime(
      state.serverTime,
      state.traffic?.delaySeconds,
    );
    setEstimatedEmergencyArriveAt(estimatedEmergencyArriveAt);
    setEmergencyArriveAt((current) => {
      if (state.actionStatus.emergencyOrderArriveAt) {
        return state.actionStatus.emergencyOrderArriveAt;
      }

      if (!current) {
        return null;
      }

      const currentArriveMs = new Date(current).getTime();

      if (Number.isNaN(currentArriveMs)) {
        return null;
      }

      return Date.now() < currentArriveMs ? current : null;
    });
    syncDiscountActionState(state.actionStatus.discountUsed);
    syncPromotionActionState(state.actionStatus.promotionUsed);
    syncShareActionState(state.actionStatus.donationUsed);
    syncEmergencyActionState(state.actionStatus.emergencyUsed);

    // 이전 일차에서 이어지는 이벤트를 carry-over 알림으로 표시 (최초 1회)
    if (!hasLoadedCarryOverRef.current && state.appliedEvents.length > 0) {
      hasLoadedCarryOverRef.current = true;
      const carryOverAlerts: GameAlert[] = state.appliedEvents
        .filter((ae) => shouldDisplayCarryOverAlert(ae.appliedAt, ae.eventName, ae.newsTitle))
        .map((ae) => {
          const fakeSchedule: EventScheduleItem = {
            time: "10:00",
            type: ae.eventName,
            scope: null,
            newsTitle: ae.newsTitle,
            populationMultiplier: 1,
            balanceChange: 0,
          };
          const info = getEventInfo(fakeSchedule, currentLocationName, currentMenuName);
          return {
            id: Date.now() + Math.floor(Math.random() * 10000),
            type: isBadEvent(fakeSchedule) ? "bad_event" as const : "event" as const,
            title: info.title,
            description: info.description,
            createdAt: Date.now(),
            timeLabel: getDaysAgoLabel(ae.appliedAt),
          };
        });
      if (carryOverAlerts.length > 0) {
        setAlerts((prev) => [...prev, ...carryOverAlerts]);
      }
    }
  };

  const [rankings, setRankings] = useState<RankEntry[]>([]);

  useEffect(() => {
    let isActive = true;

    const fetchRankings = async () => {
      try {
        const res = await getCurrentSeasonTopRankings();
        if (!isActive) return;
        setRankings(
          res.rankings.map((r) => ({
            id: String(r.userId),
            name: r.nickname,
            storeName: r.storeName,
            revenue: r.totalRevenue,
            roi: typeof r.roi === "number" ? r.roi : Number(r.roi),
            isMe: r.nickname === nickname,
          })),
        );
      } catch {
        // 랭킹 조회 실패 시 기존 데이터 유지
      }
    };

    void fetchRankings();
    const timer = window.setInterval(fetchRankings, RANKING_POLL_INTERVAL_MS);

    return () => {
      isActive = false;
      window.clearInterval(timer);
    };
  }, [nickname]);

  // Unity ready 시그널 수신 (postMessage "unityReady" — 3초 대기 후)
  useEffect(() => {
    const handler = (e: MessageEvent) => {
      if (e.data?.type === "unityReady") {
        setUnityReady(true);
      }
    };
    window.addEventListener("message", handler);
    return () => window.removeEventListener("message", handler);
  }, []);

  useEffect(() => {
    let isActive = true;

    const loadEmergencyOrderData = async () => {
      setIsEmergencyDataLoading(true);
      setEmergencyDataError(null);
      setIsMoveDataLoading(true);
      setMoveDataError(null);

      let startErrorMessage: string | null = null;
      let dayStartFallbackBalance: number | null = null;
      let dayStartFallbackStock: number | null = null;

      try {
        const dayStartRes = await startGameDay();
        if (isActive && dayStartRes.eventSchedule) {
          setEventSchedule(dayStartRes.eventSchedule);
        }
        if (isActive) {
          setDayWeatherType(dayStartRes.weatherType ?? null);
          // fallback용으로만 저장 (getGameDayState 실패 시 사용)
          if (typeof dayStartRes.initialBalance === "number") {
            dayStartFallbackBalance = dayStartRes.initialBalance;
          }
          if (typeof dayStartRes.initialStock === "number") {
            dayStartFallbackStock = dayStartRes.initialStock;
          }
        }
      } catch (error) {
        startErrorMessage = getErrorMessage(error, "영업 상태를 준비하지 못했습니다.");
      }

      const [
        stateResult,
        orderResult,
        menuResult,
        promotionPriceResult,
        storeResult,
        locationResult,
        rankingResult,
      ] = await Promise.allSettled([
        getGameDayState(),
        getCurrentOrder(),
        getStoreMenus(),
        getPromotionPrice(),
        getStore(),
        getLocationList(),
        getNewsRanking(dayNumber),
      ]);

      if (!isActive) {
        return;
      }

      if (stateResult.status === "fulfilled") {
        applyGameState(stateResult.value);
      } else {
        // getGameDayState 실패 시 startGameDay의 initialBalance/Stock을 fallback으로 사용
        if (dayStartFallbackBalance !== null) {
          setBalance(dayStartFallbackBalance);
          prevBalanceRef.current = dayStartFallbackBalance;
        }
        if (dayStartFallbackStock !== null) {
          setStock(dayStartFallbackStock);
          prevStockRef.current = dayStartFallbackStock;
        }
        setTrafficStatus(null);
        setDeliveryTrafficLabel(null);
        setEmergencyArriveAt(null);
        setEstimatedEmergencyArriveAt(null);
        latestCustomerPlanRef.current = [];
        latestBackendCustomerCountRef.current = 0;
        dispatchedVisitorsByHourRef.current.clear();
        clearScheduledVisitorTimers();
        syncDiscountActionState(false);
        syncPromotionActionState(false);
        syncShareActionState(false);
        syncEmergencyActionState(false);
      }

      if (orderResult.status === "fulfilled") {
        setCurrentOrder(orderResult.value);
      } else {
        setCurrentOrder(null);
      }

      if (menuResult.status === "fulfilled") {
        setMenuItems(mapStoreMenusToEmergencyMenus(menuResult.value.menus));
      } else {
        setMenuItems([]);
      }

      if (promotionPriceResult.status === "fulfilled") {
        const nextPrices = Object.fromEntries(
          promotionPriceResult.value.promotion.map((item) => [
            item.promotionType,
            item.promotionPrice,
          ]),
        ) as Partial<Record<PromotionType, number>>;

        setPromotionOptions(buildPromotionOptions(nextPrices));
      } else {
        setPromotionOptions(buildPromotionOptions());
      }

      const nextCurrentLocationName =
        storeResult.status === "fulfilled" ? storeResult.value.location : currentLocationName;

      if (storeResult.status === "fulfilled") {
        setCurrentLocationName(storeResult.value.location);
        // 매장 지역의 Unity 인덱스 계산 (locationId - 1 = 0-based index)
        if (locationResult.status === "fulfilled") {
          const matched = locationResult.value.locations.find(
            (loc) => loc.locationName === storeResult.value.location,
          );
          if (matched) {
            setStoreRegionIndex(matched.locationId - 1);
          }
        }
      }

      const trafficRankByAreaName =
        rankingResult.status === "fulfilled"
          ? buildAreaTrafficRankMap(rankingResult.value.areaTrafficRanking)
          : new Map<string, number>();

      if (locationResult.status === "fulfilled") {
        const nextLocationIdByName = new Map(
          locationResult.value.locations.map((location) => [
            normalizeAreaName(location.locationName),
            location.locationId,
          ] as const),
        );

        locationIdByNameRef.current = nextLocationIdByName;
        currentLocationIdRef.current =
          nextLocationIdByName.get(normalizeAreaName(nextCurrentLocationName)) ?? currentLocationIdRef.current;
        if (stateResult.status === "fulfilled") {
          schedulePlannedVisitors(
            stateResult.value.customerPlanByHour,
            stateResult.value.customerCount,
          );
        }

        setMoveRegions(
          locationResult.value.locations.map((location) =>
            mapLocationToMoveRegion(location, trafficRankByAreaName),
          ),
        );
      } else {
        locationIdByNameRef.current = new Map();
        setMoveRegions([]);
      }

      const nextError =
        stateResult.status === "rejected"
          ? getErrorMessage(
              stateResult.reason,
              startErrorMessage ?? "현재 게임 상태를 불러오지 못했습니다.",
            )
          : orderResult.status === "rejected"
            ? getErrorMessage(orderResult.reason, "현재 판매 메뉴 정보를 불러오지 못했습니다.")
            : menuResult.status === "rejected"
              ? getErrorMessage(menuResult.reason, "메뉴 목록을 불러오지 못했습니다.")
              : null;

      setEmergencyDataError(nextError);
      setIsEmergencyDataLoading(false);

      const nextMoveError =
        storeResult.status === "rejected"
          ? getErrorMessage(storeResult.reason, "현재 매장 위치를 불러오지 못했습니다.")
          : locationResult.status === "rejected"
            ? getErrorMessage(locationResult.reason, "지역 목록을 불러오지 못했습니다.")
            : null;

      if (storeResult.status !== "fulfilled") {
        setCurrentLocationName(nextCurrentLocationName);
      }

      setMoveDataError(nextMoveError);
      setIsMoveDataLoading(false);
    };

    void loadEmergencyOrderData();

    return () => {
      isActive = false;
    };
  }, [dayNumber]);

  useEffect(() => {
    if (Date.now() >= playEndTimestampMs) {
      return;
    }

    const timer = window.setInterval(() => {
      const nextNowMs = Date.now();
      const nextRemainingMilliseconds = Math.max(0, playEndTimestampMs - nextNowMs);
      const nextRemainingSeconds = Math.max(0, Math.ceil(nextRemainingMilliseconds / 1000));

      if (
        nextRemainingSeconds <= 60 &&
        nextRemainingSeconds > 0 &&
        !hasDeadlineAlertRef.current
      ) {
        hasDeadlineAlertRef.current = true;
        setAlerts((prev) => [
          {
            id: Date.now() + Math.floor(Math.random() * 1000),
            type: "deadline",
            title: "마감 1분 전",
            description: "영업 종료가 곧 다가옵니다.",
            createdAt: Date.now(),
          },
          ...prev,
        ]);
      }

      setNowMs(nextNowMs);

      if (nextRemainingMilliseconds <= 0) {
        window.clearInterval(timer);
      }
    }, 100);

    return () => window.clearInterval(timer);
  }, [playEndTimestampMs]);


  // 10초마다 게임 상태 폴링 (유동인구, 손님, 재고, 잔액)
  useEffect(() => {
    const poll = async () => {
      try {
        const state = await getGameDayState();
        applyGameState(state);
      } catch (err) {
        // 파산/시즌종료 에러 코드 → 메인으로 이동
        const code = (err as AxiosError<{ code?: string }>)?.response?.data?.code;
        if (code && GAME_EXIT_CODES.has(code)) {
          window.location.href = "/";
        }
      }
    };

    const timer = window.setInterval(poll, 10_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    return () => {
      clearScheduledVisitorTimers();
    };
  }, []);

  const triggeredEventsRef = useRef<Set<string>>(new Set());
  const pendingEventTimersRef = useRef<number[]>([]);

  useEffect(() => {
    if (eventSchedule.length === 0) return;

    const totalBusinessMs = BUSINESS_SECONDS * 1000;
    const businessStartMs = playEndTimestampMs - totalBusinessMs;

    /** 같은 시간 이벤트를 시차(3초)를 두고 push */
    const scheduleAlert = (event: EventScheduleItem, delayMs: number) => {
      const timerId = window.setTimeout(() => {
        const info = getEventInfo(event, currentLocationName, currentMenuName);
        setAlerts((prev) => [
          {
            id: Date.now() + Math.floor(Math.random() * 1000),
            type: isBadEvent(event) ? "bad_event" : "event",
            title: info.title,
            description: info.description,
            createdAt: Date.now(),
          },
          ...prev,
        ]);
      }, delayMs);
      pendingEventTimersRef.current.push(timerId);
    };

    // 즉시 이벤트와 일반(14:00/18:00) 이벤트 분리
    const immediateEvents: EventScheduleItem[] = [];
    const regularEvents: EventScheduleItem[] = [];

    for (const event of eventSchedule) {
      const isImmediate = IMMEDIATE_EVENT_NAMES.has(event.type) || IMMEDIATE_EVENT_NAMES.has(event.newsTitle);
      if (isImmediate) {
        immediateEvents.push(event);
      } else {
        regularEvents.push(event);
      }
    }

    // 오늘 새 이벤트 최대 2개, 14:00 / 18:00 고정
    const todayRegulars = regularEvents.slice(-2).map((event, i) => ({
      ...event,
      time: REGULAR_EVENT_TIMES[i] ?? REGULAR_EVENT_TIMES[REGULAR_EVENT_TIMES.length - 1],
    }));

    const check = () => {
      const elapsedMs = Date.now() - businessStartMs;
      const elapsedSec = Math.max(0, Math.min(elapsedMs / 1000, BUSINESS_SECONDS));
      const currentGameTime = elapsedToGameTime(elapsedSec);

      let sameTimeCount = 0;

      // 즉시 이벤트: 영업 시작 시 바로
      for (const event of immediateEvents) {
        const key = `immediate-${event.newsTitle}`;
        if (triggeredEventsRef.current.has(key)) continue;
        if (elapsedSec >= 0) {
          triggeredEventsRef.current.add(key);
          scheduleAlert(event, sameTimeCount * 3000);
          sameTimeCount++;
        }
      }

      // 일반 이벤트: 14:00 / 18:00 고정
      for (const event of todayRegulars) {
        const key = `regular-${event.time}-${event.newsTitle}`;
        if (triggeredEventsRef.current.has(key)) continue;
        if (currentGameTime >= event.time) {
          triggeredEventsRef.current.add(key);
          scheduleAlert(event, 0);
        }
      }
    };

    check();
    const timer = window.setInterval(check, 1000);
    return () => {
      window.clearInterval(timer);
      for (const id of pendingEventTimersRef.current) {
        window.clearTimeout(id);
      }
      pendingEventTimersRef.current = [];
    };
  }, [eventSchedule, playEndTimestampMs, currentLocationName, currentMenuName]);

  /** 사용자가 직접 발주했을 때만 true → 도착 알림 활성화 */
  const didOrderEmergencyRef = useRef(false);
  const hasEmergencyArrivalAlertRef = useRef(false);

  useEffect(() => {
    if (!emergencyArriveAt || !didOrderEmergencyRef.current) {
      return;
    }

    const arriveMs = new Date(emergencyArriveAt).getTime();
    if (Number.isNaN(arriveMs)) return;

    if (hasEmergencyArrivalAlertRef.current) return;

    const check = () => {
      if (hasEmergencyArrivalAlertRef.current) return;
      if (Date.now() >= arriveMs) {
        hasEmergencyArrivalAlertRef.current = true;
        pushAlert("action", "긴급 발주 도착", "긴급 발주한 물품이 도착했습니다.");
        // 메뉴/재고/잔액 서버에서 재조회 (BE 반영 타이밍 보정 위해 다중 재시도)
        const refreshData = () => {
          getCurrentOrder().then((order) => setCurrentOrder(order)).catch(() => {});
          getGameDayState().then((state) => applyGameState(state)).catch(() => {});
        };
        refreshData();
        setTimeout(refreshData, 2000);
        setTimeout(refreshData, 5000);
      }
    };

    check();
    const timer = window.setInterval(check, 1000);
    return () => window.clearInterval(timer);
  }, [emergencyArriveAt]);

  const handleAction = (action: ActionType) => {
    setActiveModal(action);
  };

  const closeModal = () => setActiveModal(null);

  const pushAlert = (
    type: GameAlert["type"],
    title: string,
    description: string,
  ) => {
    setAlerts((prev) => [
      {
        id: Date.now() + Math.floor(Math.random() * 1000),
        type,
        title,
        description,
        createdAt: Date.now(),
      },
      ...prev,
    ]);
  };

  const pushActionAlert = (title: string, description: string) => {
    pushAlert("action", title, description);
  };

  const completeAction = (
    action: ActionType,
    options?: {
      cost?: number;
      stockDelta?: number;
      alert?: {
        title: string;
        description: string;
      };
    },
  ) => {
    setUsedActions((prev) => new Set(prev).add(action));

    if (persistentActionTypes.has(action)) {
      setActiveEffects((prev) => new Set(prev).add(action));
    }

    const cost = options?.cost;
    const stockDelta = options?.stockDelta;
    const nextStock =
      typeof stockDelta === "number" && stockDelta !== 0 ? Math.max(0, stock + stockDelta) : stock;

    if (typeof cost === "number" && cost > 0) {
      setBalance((prev) => prev - cost);
    }

    if (typeof stockDelta === "number" && stockDelta !== 0) {
      setStock(nextStock);

      if (nextStock > 30) {
        hasLowStockAlertRef.current = false;
      } else if (stock > 30 && !hasLowStockAlertRef.current) {
        hasLowStockAlertRef.current = true;
        pushAlert("stock", "재고 30개 이하", "긴급 발주를 고려해보세요.");
      }
    }

    if (options?.alert) {
      pushActionAlert(options.alert.title, options.alert.description);
    }

    closeModal();
  };

  return (
    <div className="selection:bg-primary selection:text-white flex h-screen w-full flex-col overflow-hidden font-display text-slate-900">
      <PlayHeader
        location={currentLocationName}
        storeName={playStoreName}
        menuName={currentMenuName}
        day={dayNumber}
        remainingSeconds={remainingSeconds}
        remainingMilliseconds={remainingMilliseconds}
        congestion={getHeaderCongestionLevel(trafficStatus)}
        guests={guests}
        stock={stock}
        balance={balance}
        guestsDelta={guestsDelta}
        stockDelta={stockDelta}
        balanceDelta={balanceDelta}
      />

      <main className="relative flex flex-1 overflow-hidden">
        <div className="absolute inset-0 z-0 bg-transparent" />
        <UnityCanvas
          ref={unityBridgeRef}
          iframeRef={unityIframeRef}
          className="relative z-0 flex-1 bg-slate-950"
          onReady={handleUnityReady}
          onPopupArrival={handlePopupArrival}
        />

        <RankingSidebar rankings={rankings} />
        <EventSidebar alerts={alerts} />
        <ActionBar onAction={handleAction} usedActions={usedActions} activeEffects={activeEffects} />
      </main>

      {activeModal === "discount" && (
        <DiscountModal
          currentPrice={discountCurrentPrice}
          minimumPrice={discountMinimumPrice}
          onClose={closeModal}
          onSubmit={async (rate) => {
            const discountedPrice = getDiscountedPrice(
              discountCurrentPrice,
              discountMinimumPrice,
              rate,
            );
            const discountValue = discountCurrentPrice - discountedPrice;

            if (discountValue <= 0) {
              return;
            }

            const response = await postDiscount(discountValue);

            setCurrentOrder((prev) =>
              prev
                ? {
                    ...prev,
                    sellingPrice: response.newPrice,
                  }
                : prev,
            );
            syncDiscountActionState(true);

            const [stateResult, orderResult] = await Promise.allSettled([
              getGameDayState(),
              getCurrentOrder(),
            ]);

            if (stateResult.status === "fulfilled") {
              applyGameState(stateResult.value);
            }

            if (orderResult.status === "fulfilled") {
              setCurrentOrder(orderResult.value);
            }

            completeAction("discount", {
              alert: {
                title: "할인 이벤트 적용",
                description: `${rate}% 할인이 적용되었습니다.`,
              },
            });
          }}
        />
      )}

      {activeModal === "emergency" && (
        <EmergencyOrderModal
          currentBalance={balance}
          menuItems={menuItems}
          currentMenuId={currentOrder?.menuId ?? null}
          currentMenuPricing={currentMenuPricing}
          deliveryTrafficLabel={deliveryTrafficLabel}
          estimatedArrivalLabel={emergencyArrivalGameTime}
          isInitializing={isEmergencyDataLoading}
          initializationError={emergencyDataError}
          onClose={closeModal}
          onSubmit={async ({ menuId, menuName, quantity, salePrice }) => {
            const response = await postEmergencyOrder(menuId, quantity, salePrice);
            didOrderEmergencyRef.current = true;
            hasEmergencyArrivalAlertRef.current = false;
            const isNewMenuOrder = menuId !== currentOrder?.menuId;
            const arrivalLabel = formatEmergencyArrivalGameTime(response.arrivedTime, playEndTimestampMs);
            const arrivalText = arrivalLabel ? ` ${arrivalLabel} 도착 예정입니다.` : "";

            setEmergencyArriveAt(response.arrivedTime);

            completeAction("emergency", {
              cost: response.totalCost,
              alert: {
                title: "긴급 발주 완료",
                description: isNewMenuOrder
                  ? `${menuName} ${quantity}개를 긴급 발주했습니다.${arrivalText} 새 메뉴 주문입니다.`
                  : `${menuName} ${quantity}개를 긴급 발주했습니다.${arrivalText}`,
              },
            });
          }}
        />
      )}

      {activeModal === "promotion" && (
        <PromotionModal
          currentBalance={balance}
          options={promotionOptions}
          onClose={closeModal}
          onSubmit={async ({ promotionId, cost }) => {
            const promotionType = promotionId as PromotionType;
            const response = await postPromotion(promotionType);

            syncPromotionActionState(true);

            const [stateSyncResult] = await Promise.allSettled([getGameDayState()]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value);
            }

            completeAction("promotion", {
              cost: hasSyncedState ? undefined : response.cost || cost,
              alert: {
                title: "홍보 시작",
                description: `${promotionLabels[promotionId] ?? "홍보"}를 시작했습니다.`,
              },
            });
          }}
        />
      )}

      {activeModal === "share" && (
        <ShareModal
          currentStock={stock}
          onClose={closeModal}
          onSubmit={async (quantity) => {
            const response = await postDonation(quantity);

            syncShareActionState(true);

            const [stateSyncResult] = await Promise.allSettled([getGameDayState()]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value);
            }

            completeAction("share", {
              stockDelta: hasSyncedState ? undefined : -response.quantity,
              alert: {
                title: "나눔 이벤트 진행",
                description: `재고 ${quantity}개 나눔을 시작했습니다.`,
              },
            });
          }}
        />
      )}

      {activeModal === "move" && (
        <MoveModal
          currentBalance={balance}
          currentRegionName={currentLocationName}
          regions={moveRegions}
          isInitializing={isMoveDataLoading}
          initializationError={moveDataError}
          onClose={closeModal}
          onSubmit={async ({ regionId, regionName }) => {
            const response = await updateStoreLocation(regionId);

            const [stateSyncResult, storeSyncResult] = await Promise.allSettled([
              getGameDayState(),
              getStore(),
            ]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value);
            } else {
              setBalance(response.balance);
            }

            if (storeSyncResult.status === "fulfilled") {
              currentLocationIdRef.current =
                locationIdByNameRef.current.get(normalizeAreaName(storeSyncResult.value.location)) ?? regionId;
              setCurrentLocationName(storeSyncResult.value.location);
            } else {
              currentLocationIdRef.current = regionId;
              setCurrentLocationName(regionName);
            }

            schedulePlannedVisitors(
              latestCustomerPlanRef.current,
              latestBackendCustomerCountRef.current,
            );

            completeAction("move", {
              alert: {
                title: "영업 지역 이전 예약",
                description: `${regionName}으로 다음 영업부터 이동합니다.`,
              },
            });
          }}
        />
      )}
    </div>
  );
}
