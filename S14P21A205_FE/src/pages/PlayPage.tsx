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
  type TodayEventScheduleItem,
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
  elapsedToGameTime,
  type SeasonPhase,
} from "../constants/gameTime";
import { sendToUnity, setWeather, setDay, startDay, spawnShopAtIndex, setCameraRegion } from "../utils/unity";
import { classifyEventEffect } from "../components/play/effects/effects";
import { useEventEffectStore } from "../components/play/effects/useEventEffect";
import EventEffect3DOverlay from "../components/play/effects/EventEffect3DOverlay";
import useBrandName from "../hooks/useBrandName";
import useStatQueue from "../hooks/useStatQueue";
import { useUserStore } from "../stores/useUserStore";
import { normalizeDiscountMultiplier } from "../utils/dashboardItems";

interface ApiErrorResponse {
  message?: string;
}

type PlayDebugPhaseLabel =
  | "BUSINESS"
  | "PREPARING"
  | "REPORT"
  | "LOCATION_SELECTION"
  | "SEASON_SUMMARY"
  | "NEXT_SEASON_WAITING"
  | "CLOSED";

interface PlayDebugCustomerFactors {
  serverCustomerCount: number;
  previousDisplayedGuests: number;
  pollingIncrease: number;
  animationAppliedCount: number;
}

interface PlayDebugStockFactors {
  previousDisplayedStock: number;
  guestArrivalDeduction: number;
  emergencyArrivalReflection: number;
  donationDeduction: number;
  serverResyncCorrection: number;
}

interface PlayDebugBalanceFactors {
  previousDisplayedBalance: number;
  salesAmount: number;
  promotionCost: number;
  emergencyOrderCost: number;
  donationCost: number;
  moveCost: number;
  serverResyncCorrection: number;
}

interface PlayDebugPendingLog {
  dedupeKey: string;
  day: number;
  phase: PlayDebugPhaseLabel;
  tick: string;
  gameTime: string;
  eventName: string;
  actionName: string;
  targetGuests: number;
  targetStock: number;
  targetBalance: number;
  waitForDisplayedSettlement: boolean;
  customerFactors: PlayDebugCustomerFactors;
  stockFactors: PlayDebugStockFactors;
  balanceFactors: PlayDebugBalanceFactors;
}

const PLAY_DEBUG_TOGGLE_KEY = "play-debug-tick-logs";

let lastDebugAppliedBusinessDay: number | null = null;

function isPlayDebugLoggingEnabled() {
  if (!import.meta.env.DEV) {
    return false;
  }

  try {
    return window.localStorage.getItem(PLAY_DEBUG_TOGGLE_KEY) !== "0";
  } catch {
    return true;
  }
}

function getPlayDebugPhaseLabel(phase: SeasonPhase): PlayDebugPhaseLabel {
  switch (phase) {
    case "DAY_BUSINESS":
      return "BUSINESS";
    case "DAY_PREPARING":
      return "PREPARING";
    case "DAY_REPORT":
      return "REPORT";
    default:
      return phase;
  }
}

function formatSignedInteger(value: number) {
  if (value > 0) {
    return `+${value.toLocaleString()}`;
  }

  return value.toLocaleString();
}

function formatUnsignedInteger(value: number) {
  return value.toLocaleString();
}

function formatPlayDebugBlock(input: {
  day: number;
  phase: PlayDebugPhaseLabel;
  tick: string;
  gameTime: string;
  eventName: string;
  actionName: string;
  guests: number;
  stock: number;
  balance: number;
  customerFactors: PlayDebugCustomerFactors;
  stockFactors: PlayDebugStockFactors;
  balanceFactors: PlayDebugBalanceFactors;
}) {
  return [
    "================ 틱 디버그 [프론트] ================",
    `현재 day     : ${input.day}`,
    `현재 phase   : ${input.phase}`,
    `현재 tick    : ${input.tick}`,
    `게임 시간    : ${input.gameTime}`,
    "",
    `이번 틱 이벤트 : ${input.eventName || "없음"}`,
    `이번 틱 액션   : ${input.actionName || "없음"}`,
    "",
    "손님",
    `- 최종값      : ${input.guests.toLocaleString()}명`,
    `- 영향요소    : 서버 누적 손님 수=${formatUnsignedInteger(input.customerFactors.serverCustomerCount)} / 직전 화면 손님 수=${formatUnsignedInteger(input.customerFactors.previousDisplayedGuests)} / 이번 폴링 증가분=${formatSignedInteger(input.customerFactors.pollingIncrease)} / 연출 반영 수=${formatUnsignedInteger(input.customerFactors.animationAppliedCount)}`,
    "",
    "재고",
    `- 최종값      : ${input.stock.toLocaleString()}개`,
    `- 영향요소    : 직전 화면 재고=${formatUnsignedInteger(input.stockFactors.previousDisplayedStock)} / 손님 연출 차감=${formatSignedInteger(input.stockFactors.guestArrivalDeduction)} / 긴급발주 도착 반영=${formatSignedInteger(input.stockFactors.emergencyArrivalReflection)} / 나눔 차감=${formatSignedInteger(input.stockFactors.donationDeduction)} / 서버 재동기화 보정=${formatSignedInteger(input.stockFactors.serverResyncCorrection)}`,
    "",
    "잔액",
    `- 최종값      : ${input.balance.toLocaleString()}원`,
    `- 영향요소    : 직전 화면 잔액=${formatUnsignedInteger(input.balanceFactors.previousDisplayedBalance)} / 이번 틱 판매금액=${formatSignedInteger(input.balanceFactors.salesAmount)} / 홍보 비용=${formatUnsignedInteger(input.balanceFactors.promotionCost)} / 긴급발주 비용=${formatUnsignedInteger(input.balanceFactors.emergencyOrderCost)} / 나눔 비용=${formatUnsignedInteger(input.balanceFactors.donationCost)} / 이동 비용=${formatUnsignedInteger(input.balanceFactors.moveCost)} / 서버 재동기화 보정=${formatSignedInteger(input.balanceFactors.serverResyncCorrection)}`,
    "==================================================",
  ].join("\n");
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

const MENU_NAME_BY_ID: Record<number, string> = {
  1: "빵",
  2: "마라꼬치",
  3: "젤리",
  4: "떡볶이",
  5: "햄버거",
  6: "아이스크림",
  7: "닭강정",
  8: "타코",
  9: "핫도그",
  10: "버블티",
};

interface EventTemplate {
  title: string;
  /** $LOC → 지역명, $MENU → 메뉴명 */
  description: string;
}

/** 악재 이벤트 (populationMultiplier < 1, 재난, 원가 상승 등) */
function isBadEvent(event: TodayEventScheduleItem): boolean {
  if (event.populationMultiplier < 1) return true;
  if ((event.balanceChange ?? 0) < 0) return true;
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

const LOCATION_NAME_BY_ID: Record<number, string> = {
  1: "잠실",
  2: "신도림",
  3: "여의도",
  4: "이태원",
  5: "서울숲/성수",
  6: "강남",
  7: "명동",
  8: "홍대",
};

function getEventDisplayName(event: TodayEventScheduleItem) {
  return event.eventName ?? event.newsTitle ?? event.type;
}

function resolveEventLocationName(event: TodayEventScheduleItem, fallbackLocationName: string) {
  const explicitLocation = [event.targetRegionName, event.regionName, event.locationName]
    .find((value) => typeof value === "string" && value.trim().length > 0);

  if (explicitLocation) {
    return explicitLocation.trim();
  }

  const locationId = event.targetRegionId ?? event.scope?.region ?? null;
  if (typeof locationId === "number") {
    return LOCATION_NAME_BY_ID[locationId] ?? fallbackLocationName;
  }

  return fallbackLocationName;
}

function isFestivalEvent(eventName: string) {
  return /festival|축제/iu.test(eventName);
}

function isSubstituteHolidayEvent(eventName: string) {
  return /substitute holiday|대체\s*공휴일/iu.test(eventName);
}

function isPolicyChangeEvent(eventName: string) {
  return /policy change|정책\s*변경|정부\s*방침/iu.test(eventName);
}

function getPriceDirection(eventName: string) {
  if (/price down|가격\s*하락/iu.test(eventName)) {
    return "down";
  }

  if (/price up|가격\s*상승/iu.test(eventName)) {
    return "up";
  }

  return null;
}

function buildTodayEventKey(event: TodayEventScheduleItem) {
  return [
    event.eventId ?? "",
    event.time,
    event.type,
    event.eventName ?? "",
    event.newsTitle,
    event.targetRegionId ?? event.scope?.region ?? "",
    event.targetRegionName ?? "",
    event.regionName ?? "",
    event.locationName ?? "",
    event.scope?.menu ?? "",
    event.populationMultiplier,
    event.balanceChange ?? "",
  ].join("|");
}

function buildTodayEventScheduleSignature(events: TodayEventScheduleItem[]) {
  return events.map(buildTodayEventKey).join("||");
}

function resolveEventMenuName(event: TodayEventScheduleItem, fallbackMenuName: string) {
  const menuId = event.scope?.menu ?? null;
  if (typeof menuId === "number") {
    return MENU_NAME_BY_ID[menuId] ?? fallbackMenuName;
  }

  const eventName = getEventDisplayName(event);
  const matchedMenuName = Object.values(MENU_NAME_BY_ID)
    .find((menuName) => eventName.includes(menuName));

  return matchedMenuName ?? fallbackMenuName;
}

function getEventInfo(
  event: TodayEventScheduleItem,
  fallbackLocationName: string,
  fallbackMenuName: string,
): { title: string; description: string } {
  const locationName = resolveEventLocationName(event, fallbackLocationName);
  const eventName = getEventDisplayName(event);
  const template = EVENT_INFO[event.type] ?? EVENT_INFO[event.newsTitle];

  if (isFestivalEvent(eventName)) {
    const title =
      event.newsTitle && !/^festival$/iu.test(event.newsTitle)
        ? event.newsTitle
        : template?.title ?? "축제 개최";
    const description = title.includes(locationName)
      ? `${title}가 열리고 있습니다.`
      : `${locationName}에서 ${title}가 열리고 있습니다.`;
    return { title, description };
  }

  if (isSubstituteHolidayEvent(eventName)) {
    return {
      title: template?.title ?? "대체 공휴일",
      description: "정부가 내일을 대체 공휴일로 지정했습니다.",
    };
  }

  if (isPolicyChangeEvent(eventName)) {
    return {
      title: template?.title ?? "정책 변경",
      description: "내일부터 일회용품 사용 규제 등 정부 방침이 변경될 예정입니다.",
    };
  }

  const priceDirection = getPriceDirection(eventName);
  if (priceDirection !== null) {
    const menuLabel =
      template?.title.replace(/\s*원가\s*(하락|상승)\s*$/u, "").trim()
      || resolveEventMenuName(event, fallbackMenuName);
    const priceChangeLabel = priceDirection === "down" ? "하락" : "상승";
    const title = menuLabel ? `${menuLabel} 원가 ${priceChangeLabel}` : `원가 ${priceChangeLabel}`;
    const menuText = menuLabel ? `${menuLabel} ` : "";
    return {
      title,
      description: `내일부터 ${menuText}원재료값이 ${priceChangeLabel}할 예정입니다.`,
    };
  }

  if (!template) {
    return { title: event.newsTitle, description: "새로운 이벤트가 발생했습니다." };
  }
  let description = template.description.replace("$LOC", locationName);

  // 지원금 이벤트의 경우 금액 표시
  if ((event.balanceChange ?? 0) > 0) {
    description += ` (${(event.balanceChange ?? 0).toLocaleString()}원)`;
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

function getEstimatedEmergencyArrivalGameTime(
  remainingMilliseconds: number,
  delaySeconds: number | null | undefined,
) {
  if (typeof delaySeconds !== "number" || delaySeconds < 0) {
    return null;
  }

  const currentElapsedBusinessSeconds = Math.floor(getElapsedBusinessSeconds(remainingMilliseconds));
  const estimatedElapsedBusinessSeconds = Math.min(
    BUSINESS_SECONDS,
    currentElapsedBusinessSeconds + delaySeconds,
  );
  return elapsedToGameTime(estimatedElapsedBusinessSeconds);
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
      phase={guardContext.phase}
      phaseEndTimestamp={guardContext.phaseEndTimestamp}
    />
  );
}

function PlayPageSession({
  dayNumber,
  phase,
  phaseEndTimestamp,
}: {
  dayNumber: number;
  phase: SeasonPhase;
  phaseEndTimestamp: number;
}) {
  const nickname = useUserStore((s) => s.nickname) ?? "버블티";
  const { brandName } = useBrandName();
  const triggerEffect = useEventEffectStore((s) => s.triggerEffect);
  const activeEventEffect = useEventEffectStore((s) => s.activeEffect);
  const [activeModal, setActiveModal] = useState<ActionType | null>(null);
  const [serverUsedActions, setServerUsedActions] = useState<Set<ActionType>>(new Set());
  const [optimisticUsedActions, setOptimisticUsedActions] = useState<Set<ActionType>>(new Set());
  const usedActions = useMemo(() => {
    const merged = new Set<ActionType>(serverUsedActions);
    for (const action of optimisticUsedActions) {
      merged.add(action);
    }
    return merged;
  }, [serverUsedActions, optimisticUsedActions]);
  const [activeEffects, setActiveEffects] = useState<Set<ActionType>>(new Set());
  const [alerts, setAlerts] = useState<GameAlert[]>([]);
  const [todayEventSchedule, setTodayEventSchedule] = useState<TodayEventScheduleItem[]>([]);
  const unityIframeRef = useRef<HTMLIFrameElement>(null);

  // 이벤트 이펙트 중 스페이스바(뷰 전환) 차단 + 탑뷰면 가게 뷰로 복귀
  useEffect(() => {
    if (!activeEventEffect) {
      // 이펙트 끝나면 iframe에 포커스 복원
      unityIframeRef.current?.focus();
      return;
    }
    // 탑뷰 상태일 수 있으니 가게 뷰로 복귀
    sendToUnity(unityIframeRef, "ReturnToMain");
    if (storeRegionIndex !== null) {
      setCameraRegion(unityIframeRef, storeRegionIndex);
    }
    // iframe blur로 키보드 입력 차단
    unityIframeRef.current?.blur();
  }, [activeEventEffect]);

  const [unityReady, setUnityReady] = useState(false);
  const [dayWeatherType, setDayWeatherType] = useState<string | null>(null);
  const [storeRegionIndex, setStoreRegionIndex] = useState<number | null>(null);
  const todayEventScheduleSignatureRef = useRef("");
  const [balance, setBalance] = useState(0);
  const [stock, setStock] = useState(0);
  const [guests, setGuests] = useState(0);
  const statQueue = useStatQueue(setStock, setBalance);
  const displayedGuestsRef = useRef(0);
  const displayedStockRef = useRef(0);
  const displayedBalanceRef = useRef(0);
  const [currentLocationName, setCurrentLocationName] = useState("");
  const currentLocationIdRef = useRef<number | null>(null);
  const locationIdByNameRef = useRef<ReadonlyMap<string, number>>(new Map());
  const scheduledVisitorTimersRef = useRef<number[]>([]);
  const dispatchedVisitorsByHourRef = useRef<Map<number, number>>(new Map());
  const latestCustomerPlanRef = useRef<CustomerPlanByHourItem[]>([]);
  const latestBackendCustomerCountRef = useRef(0);
  const [currentOrder, setCurrentOrder] = useState<CurrentOrderResponse | null>(null);
  const [liveSellingPrice, setLiveSellingPrice] = useState<number | null>(null);
  const [menuItems, setMenuItems] = useState<EmergencyMenuItem[]>([]);
  const [moveRegions, setMoveRegions] = useState<MoveRegion[]>([]);
  const [promotionOptions, setPromotionOptions] = useState<PromotionOption[]>(() =>
    buildPromotionOptions(),
  );
  const [trafficStatus, setTrafficStatus] = useState<GameTrafficStatus | null>(null);
  const [deliveryTrafficLabel, setDeliveryTrafficLabel] = useState<string | null>(null);
  const [emergencyArriveAt, setEmergencyArriveAt] = useState<string | null>(null);
  const [estimatedEmergencyDelaySeconds, setEstimatedEmergencyDelaySeconds] = useState<number | null>(null);
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
  const playStoreName = brandName || "";
  const currentMenuName = currentOrder?.menuName ?? "";
  const emergencyArrivalGameTime = emergencyArriveAt
    ? formatEmergencyArrivalGameTime(emergencyArriveAt, playEndTimestampMs) || null
    : getEstimatedEmergencyArrivalGameTime(remainingMilliseconds, estimatedEmergencyDelaySeconds);
  const currentLiveSellingPrice = liveSellingPrice ?? currentOrder?.sellingPrice ?? 0;
  const currentMenuPricing: CurrentMenuPricing | null = currentOrder
    ? {
        costPrice: currentOrder.costPrice,
        recommendedPrice: currentOrder.recommendedPrice,
        maxSellingPrice: currentOrder.maxSellingPrice,
        sellingPrice: currentLiveSellingPrice,
      }
    : null;
  const discountCurrentPrice = currentLiveSellingPrice;
  const discountMinimumPrice = currentOrder?.costPrice ?? discountCurrentPrice;
  const debugPhaseLabel = getPlayDebugPhaseLabel(phase);

  const syncActionUsageState = (action: ActionType, isUsed: boolean) => {
    setServerUsedActions((prev) => {
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

  const spawnTimingRef = useRef<{ totalSpawned: number; totalArrived: number; lastRequestAt: number }>({ totalSpawned: 0, totalArrived: 0, lastRequestAt: 0 });
  const unityBridgeRef = useRef<UnityBridgeHandle | null>(null);
  const latestTrafficStatusRef = useRef<GameTrafficStatus | null>(null);
  const lastUnityCongestionLevelRef = useRef<UnityCongestionLevel | null>(null);
  const pendingDebugLogsRef = useRef<PlayDebugPendingLog[]>([]);
  const emittedDebugKeysRef = useRef<Set<string>>(new Set());
  const [debugFlushVersion, setDebugFlushVersion] = useState(0);
  // ref로 최신 값 추적 (클로저 캡처 문제 방지)
  const prevGuestsRef = useRef<number | null>(null);
  const prevStockRef = useRef<number | null>(null);
  const prevBalanceRef = useRef<number | null>(null);
  // Unity arrival 시 고객별 재고/잔액 변동 큐
  const arrivalQueueRef = useRef<Array<{ stockDelta: number; balanceDelta: number }>>([]);

  useEffect(() => {
    remainingMillisecondsRef.current = remainingMilliseconds;
  }, [remainingMilliseconds]);

  useEffect(() => {
    displayedGuestsRef.current = guests;
    displayedStockRef.current = stock;
    displayedBalanceRef.current = balance;
  }, [balance, guests, stock]);

  const getCurrentDebugGameTime = () =>
    elapsedToGameTime(getElapsedBusinessSeconds(remainingMillisecondsRef.current));

  const getCurrentDebugTick = (
    options: { serverTick?: number | null; phase?: PlayDebugPhaseLabel } = {},
  ) => {
    if (typeof options.serverTick === "number" && Number.isFinite(options.serverTick)) {
      return String(options.serverTick);
    }

    const phase = options.phase ?? debugPhaseLabel;

    if (phase !== "BUSINESS") {
      return "-";
    }

    return String(Math.floor(getElapsedBusinessSeconds(remainingMillisecondsRef.current) / 10));
  };

  const queueDebugLog = (payload: PlayDebugPendingLog) => {
    if (!isPlayDebugLoggingEnabled()) {
      return;
    }

    if (emittedDebugKeysRef.current.has(payload.dedupeKey)) {
      return;
    }

    pendingDebugLogsRef.current.push(payload);
    setDebugFlushVersion((prev) => prev + 1);
  };

  const buildDebugLogFromState = (
    state: GameStateResponse,
    options: {
      day: number;
      phase: PlayDebugPhaseLabel;
      gameTime: string;
      eventName?: string;
      actionName?: string;
      previousDisplayedGuests: number;
      previousDisplayedStock: number;
      previousDisplayedBalance: number;
      promotionCost?: number;
      emergencyOrderCost?: number;
      donationCost?: number;
      moveCost?: number;
      donationDeduction?: number;
      emergencyArrivalReflection?: number;
      dedupeKey: string;
      waitForDisplayedSettlement: boolean;
    },
  ): PlayDebugPendingLog => {
    const soldUnits = state.customerTick.soldUnits ?? [];
    const soldUnitsTotal = soldUnits.reduce((sum, units) => sum + units, 0);
    const salesAmount = soldUnits.reduce(
      (sum, units) => sum + units * state.customerTick.unitPrice,
      0,
    );
    const pollingIncrease =
      prevGuestsRef.current !== null ? state.customerCount - prevGuestsRef.current : state.customerCount;
    const guestArrivalDeduction = soldUnitsTotal > 0 ? -soldUnitsTotal : 0;
    const emergencyArrivalReflection = options.emergencyArrivalReflection ?? 0;
    const donationDeduction = options.donationDeduction ?? 0;
    const promotionCost = options.promotionCost ?? 0;
    const emergencyOrderCost = options.emergencyOrderCost ?? 0;
    const donationCost = options.donationCost ?? 0;
    const moveCost = options.moveCost ?? 0;
    const expectedStock =
      options.previousDisplayedStock
      + guestArrivalDeduction
      + emergencyArrivalReflection
      + donationDeduction;
    const expectedBalance =
      options.previousDisplayedBalance
      + salesAmount
      - promotionCost
      - emergencyOrderCost
      - donationCost
      - moveCost;

    return {
      dedupeKey: options.dedupeKey,
      day: options.day,
      phase: options.phase,
      tick: getCurrentDebugTick({
        serverTick: state.customerTick?.tick,
        phase: options.phase,
      }),
      gameTime: options.gameTime,
      eventName: options.eventName ?? "없음",
      actionName: options.actionName ?? "없음",
      targetGuests: state.customerCount,
      targetStock: state.inventory.totalStock,
      targetBalance: state.cash,
      waitForDisplayedSettlement: options.waitForDisplayedSettlement,
      customerFactors: {
        serverCustomerCount: state.customerCount,
        previousDisplayedGuests: options.previousDisplayedGuests,
        pollingIncrease,
        animationAppliedCount: Math.max(0, pollingIncrease),
      },
      stockFactors: {
        previousDisplayedStock: options.previousDisplayedStock,
        guestArrivalDeduction,
        emergencyArrivalReflection,
        donationDeduction,
        serverResyncCorrection: state.inventory.totalStock - expectedStock,
      },
      balanceFactors: {
        previousDisplayedBalance: options.previousDisplayedBalance,
        salesAmount,
        promotionCost,
        emergencyOrderCost,
        donationCost,
        moveCost,
        serverResyncCorrection: state.cash - expectedBalance,
      },
    };
  };

  const buildPassiveDebugLog = (options: {
    dedupeKey: string;
    day: number;
    phase: PlayDebugPhaseLabel;
    gameTime: string;
    eventName?: string;
    actionName?: string;
  }): PlayDebugPendingLog => ({
    dedupeKey: options.dedupeKey,
    day: options.day,
    phase: options.phase,
    tick: getCurrentDebugTick({ phase: options.phase }),
    gameTime: options.gameTime,
    eventName: options.eventName ?? "없음",
    actionName: options.actionName ?? "없음",
    targetGuests: displayedGuestsRef.current,
    targetStock: displayedStockRef.current,
    targetBalance: displayedBalanceRef.current,
    waitForDisplayedSettlement: false,
    customerFactors: {
      serverCustomerCount: displayedGuestsRef.current,
      previousDisplayedGuests: displayedGuestsRef.current,
      pollingIncrease: 0,
      animationAppliedCount: 0,
    },
    stockFactors: {
      previousDisplayedStock: displayedStockRef.current,
      guestArrivalDeduction: 0,
      emergencyArrivalReflection: 0,
      donationDeduction: 0,
      serverResyncCorrection: 0,
    },
    balanceFactors: {
      previousDisplayedBalance: displayedBalanceRef.current,
      salesAmount: 0,
      promotionCost: 0,
      emergencyOrderCost: 0,
      donationCost: 0,
      moveCost: 0,
      serverResyncCorrection: 0,
    },
  });

  const queueActionDebugLog = (options: {
    actionName: string;
    dedupeKey: string;
    targetGuests?: number;
    targetStock?: number;
    targetBalance?: number;
    salesAmount?: number;
    promotionCost?: number;
    emergencyOrderCost?: number;
    donationCost?: number;
    moveCost?: number;
    donationDeduction?: number;
  }) => {
    const targetGuests = options.targetGuests ?? displayedGuestsRef.current;
    const targetStock = options.targetStock ?? displayedStockRef.current;
    const targetBalance = options.targetBalance ?? displayedBalanceRef.current;

    queueDebugLog({
      dedupeKey: options.dedupeKey,
      day: dayNumber,
      phase: debugPhaseLabel,
      tick: getCurrentDebugTick({ phase: debugPhaseLabel }),
      gameTime: getCurrentDebugGameTime(),
      eventName: "없음",
      actionName: options.actionName,
      targetGuests,
      targetStock,
      targetBalance,
      waitForDisplayedSettlement:
        targetGuests !== displayedGuestsRef.current
        || targetStock !== displayedStockRef.current
        || targetBalance !== displayedBalanceRef.current,
      customerFactors: {
        serverCustomerCount: targetGuests,
        previousDisplayedGuests: displayedGuestsRef.current,
        pollingIncrease: 0,
        animationAppliedCount: 0,
      },
      stockFactors: {
        previousDisplayedStock: displayedStockRef.current,
        guestArrivalDeduction: 0,
        emergencyArrivalReflection: 0,
        donationDeduction: options.donationDeduction ?? 0,
        serverResyncCorrection:
          targetStock - (displayedStockRef.current + (options.donationDeduction ?? 0)),
      },
      balanceFactors: {
        previousDisplayedBalance: displayedBalanceRef.current,
        salesAmount: options.salesAmount ?? 0,
        promotionCost: options.promotionCost ?? 0,
        emergencyOrderCost: options.emergencyOrderCost ?? 0,
        donationCost: options.donationCost ?? 0,
        moveCost: options.moveCost ?? 0,
        serverResyncCorrection:
          targetBalance - (
            displayedBalanceRef.current
            + (options.salesAmount ?? 0)
            - (options.promotionCost ?? 0)
            - (options.emergencyOrderCost ?? 0)
            - (options.donationCost ?? 0)
            - (options.moveCost ?? 0)
          ),
      },
    });
  };

  const clearScheduledVisitorTimers = () => {
    for (const timerId of scheduledVisitorTimersRef.current) {
      window.clearTimeout(timerId);
    }

    scheduledVisitorTimersRef.current = [];
  };

  const spawnPopupVisitorsImmediately = (popupStoreIndex: number, count: number) => {
    const totalCount = Math.max(0, Math.floor(count));
    if (totalCount <= 0) return false;

    // Unity의 SpawnPopupVisitorsRoutine을 사용해 NPC를 분산 스폰
    return unityBridgeRef.current?.spawnPopupVisitors(popupStoreIndex, totalCount) ?? false;
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
      setWeather(unityIframeRef, dayWeatherType, storeRegionIndex ?? 0);
    }
    const remaining = Math.max(0, Math.ceil((playEndTimestampMs - Date.now()) / 1000));
    const isFirstLoad = remaining >= BUSINESS_SECONDS - 3;
    if (isFirstLoad) {
      // 처음 진입: Unity autoStart가 이미 StartDay(120) 실행하므로 중복 호출 안 함
    } else if (remaining > 5) {
      // 새로고침: 남은 시간으로 동기화 (너무 짧으면 밤 덮어쓰기 방지)
      startDay(unityIframeRef, remaining);
    }
    lastUnityCongestionLevelRef.current = null;
    syncUnityCongestionLevel(latestTrafficStatusRef.current);
    schedulePlannedVisitors(latestCustomerPlanRef.current, latestBackendCustomerCountRef.current);
  };

  // Unity ready 이후 날씨 데이터가 도착하면 전송
  useEffect(() => {
    if (unityReady && dayWeatherType !== null) {
      setWeather(unityIframeRef, dayWeatherType, storeRegionIndex ?? 0);
    }
  }, [unityReady, dayWeatherType]);

  const handlePopupArrival = (popupStoreIndex: number | null) => {
    const currentPopupStoreIndex = resolvePopupStoreIndex(currentLocationIdRef.current);

    if (currentPopupStoreIndex === null) {
      return;
    }

    if (popupStoreIndex !== null && popupStoreIndex !== currentPopupStoreIndex) {
      return;
    }

    applyOneArrival();
  };

  const applyOneArrival = () => {
    if (arrivalQueueRef.current.length === 0) return;

    setGuests((prev) => prev + 1);

    const change = arrivalQueueRef.current.shift();
    if (change && (change.stockDelta !== 0 || change.balanceDelta !== 0)) {
      setStock((prev) => prev + change.stockDelta);
      setBalance((prev) => prev + change.balanceDelta);
    }

    spawnTimingRef.current.totalArrived += 1;
    const { totalSpawned, totalArrived } = spawnTimingRef.current;
    const remainingUntilEnd = Math.max(0, playEndTimestampMs - Date.now());
    console.log(`[SpawnTiming] 도착 (누적 도착: ${totalArrived}/${totalSpawned}, 미도착: ${totalSpawned - totalArrived}), 남은 시간: ${remainingUntilEnd}ms`);
  };

  // Unity UNITY_POPUP_ARRIVAL 이벤트 수신
  useEffect(() => {
    const handleUnityMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type !== "UNITY_POPUP_ARRIVAL") return;

      const signalValue = Number(event.data.signalValue ?? 1);
      for (let i = 0; i < signalValue; i += 1) {
        applyOneArrival();
      }
    };

    window.addEventListener("message", handleUnityMessage);
    return () => window.removeEventListener("message", handleUnityMessage);
  }, []);

  useEffect(() => {
    if (!isPlayDebugLoggingEnabled() || pendingDebugLogsRef.current.length === 0) {
      return;
    }

    const remainingLogs: PlayDebugPendingLog[] = [];

    for (const pendingLog of pendingDebugLogsRef.current) {
      const isSettled =
        !pendingLog.waitForDisplayedSettlement
        || (
          guests === pendingLog.targetGuests
          && stock === pendingLog.targetStock
          && balance === pendingLog.targetBalance
        );

      if (!isSettled) {
        remainingLogs.push(pendingLog);
        continue;
      }

      if (emittedDebugKeysRef.current.has(pendingLog.dedupeKey)) {
        continue;
      }

      emittedDebugKeysRef.current.add(pendingLog.dedupeKey);
      console.log(
        formatPlayDebugBlock({
          day: pendingLog.day,
          phase: pendingLog.phase,
          tick: pendingLog.tick,
          gameTime: pendingLog.gameTime,
          eventName: pendingLog.eventName,
          actionName: pendingLog.actionName,
          guests,
          stock,
          balance,
          customerFactors: pendingLog.customerFactors,
          stockFactors: pendingLog.stockFactors,
          balanceFactors: pendingLog.balanceFactors,
        }),
      );
    }

    pendingDebugLogsRef.current = remainingLogs;
  }, [balance, debugFlushVersion, guests, stock]);

  const applyGameState = (
    state: GameStateResponse,
    source: "initial" | "poll" | "action_sync" | "emergency_refresh" = "poll",
  ) => {
    setLiveSellingPrice(state.customerTick.unitPrice);
    const previousDisplayedGuests = displayedGuestsRef.current;
    const previousDisplayedStock = displayedStockRef.current;
    const previousDisplayedBalance = displayedBalanceRef.current;
    const debugGameTime = getCurrentDebugGameTime();
    const hasCustomerPlan =
      Array.isArray(state.customerPlanByHour) && state.customerPlanByHour.length > 0;

    if (prevGuestsRef.current !== null) {
      const gd = state.customerCount - prevGuestsRef.current;

      // 이전 큐 클리어
      arrivalQueueRef.current = [];

      // 새 손님이 있으면: 재고/잔액은 이전 값 유지, 큐로 점진 반영
      if (gd > 0) {
        const { soldUnits, unitPrice } = state.customerTick;
        for (const units of soldUnits) {
          arrivalQueueRef.current.push({
            stockDelta: -units,
            balanceDelta: units * unitPrice,
          });
        }

        if (!hasCustomerPlan) {
          const popupStoreIndex = resolvePopupStoreIndex(currentLocationIdRef.current);

          if (popupStoreIndex !== null) {
            spawnPopupVisitorsImmediately(popupStoreIndex, gd);
            spawnTimingRef.current.totalSpawned += gd;
            spawnTimingRef.current.lastRequestAt = Date.now();
            const { totalSpawned, totalArrived } = spawnTimingRef.current;
            const remainingUntilEnd = Math.max(0, playEndTimestampMs - Date.now());
            console.log(`[SpawnTiming] 스폰 요청: +${gd}명 (누적 스폰: ${totalSpawned}, 도착: ${totalArrived}, 미도착: ${totalSpawned - totalArrived}), 남은 시간: ${remainingUntilEnd}ms`);
          }
        }
      } else {
        // 새 손님 없으면 큐를 통해 점진적으로 동기화
        statQueue.enqueue({
          targetStock: state.inventory.totalStock,
          targetBalance: state.cash,
          currentStock: prevStockRef.current ?? 0,
          currentBalance: prevBalanceRef.current ?? 0,
        });
      }
    } else {
      // 최초 폴링: 백엔드 값으로 초기화
      setGuests(state.customerCount);
      setStock(state.inventory.totalStock);
      setBalance(state.cash);
    }

    // 현재 값을 ref에 저장 (다음 비교용)
    if (lastDebugAppliedBusinessDay !== null && lastDebugAppliedBusinessDay !== state.day) {
      queueDebugLog(
        buildDebugLogFromState(state, {
          day: state.day,
          phase: debugPhaseLabel,
          gameTime: "09:50",
          previousDisplayedGuests,
          previousDisplayedStock,
          previousDisplayedBalance,
          dedupeKey: `day-change:${state.day}`,
          waitForDisplayedSettlement: true,
        }),
      );
    }

    if (source === "poll") {
      queueDebugLog(
        buildDebugLogFromState(state, {
          day: state.day,
          phase: debugPhaseLabel,
          gameTime: debugGameTime,
          previousDisplayedGuests,
          previousDisplayedStock,
          previousDisplayedBalance,
          dedupeKey: `poll:${state.day}:${debugPhaseLabel}:${debugGameTime}`,
          waitForDisplayedSettlement: true,
        }),
      );
    }

    prevGuestsRef.current = state.customerCount;
    prevStockRef.current = state.inventory.totalStock;
    prevBalanceRef.current = state.cash;
    lastDebugAppliedBusinessDay = state.day;
    setTrafficStatus(state.traffic?.status ?? null);
    setDeliveryTrafficLabel(getTrafficStatusLabel(state.traffic?.status));
    syncUnityCongestionLevel(state.traffic?.status);
    schedulePlannedVisitors(state.customerPlanByHour, state.customerCount);
    const nextTodayEventSchedule = state.todayEventSchedule ?? [];
    const nextTodayEventScheduleSignature = buildTodayEventScheduleSignature(nextTodayEventSchedule);
    if (todayEventScheduleSignatureRef.current !== nextTodayEventScheduleSignature) {
      todayEventScheduleSignatureRef.current = nextTodayEventScheduleSignature;
      setTodayEventSchedule(nextTodayEventSchedule);
    }
    setEstimatedEmergencyDelaySeconds(state.traffic?.delaySeconds ?? null);
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

      setOptimisticUsedActions(new Set());

      if (stateResult.status === "fulfilled") {
        applyGameState(stateResult.value, "initial");
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
        setEstimatedEmergencyDelaySeconds(null);
        latestCustomerPlanRef.current = [];
        latestBackendCustomerCountRef.current = 0;
        dispatchedVisitorsByHourRef.current.clear();
        clearScheduledVisitorTimers();
        syncDiscountActionState(false);
        syncPromotionActionState(false);
        syncShareActionState(false);
        syncEmergencyActionState(false);
        setOptimisticUsedActions(new Set());
        setLiveSellingPrice(null);
      }

      if (orderResult.status === "fulfilled") {
        setCurrentOrder(orderResult.value);
        if (stateResult.status !== "fulfilled") {
          setLiveSellingPrice(orderResult.value.sellingPrice);
        }
      } else {
        setCurrentOrder(null);
        if (stateResult.status !== "fulfilled") {
          setLiveSellingPrice(null);
        }
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
        const { totalSpawned, totalArrived } = spawnTimingRef.current;
        if (totalSpawned > 0) {
          const missing = totalSpawned - totalArrived;
          if (missing > 0) {
            console.warn(`[SpawnTiming] ⚠️ 하루 종료! 누적 미도착: ${missing}/${totalSpawned}명 (도착률: ${Math.round(totalArrived / totalSpawned * 100)}%)`);
          } else {
            console.log(`[SpawnTiming] ✅ 하루 종료! 전원 도착: ${totalArrived}/${totalSpawned}명`);
          }
        }
      }
    }, 100);

    return () => window.clearInterval(timer);
  }, [playEndTimestampMs]);


  // 10초마다 게임 상태 폴링 (유동인구, 손님, 재고, 잔액)
  useEffect(() => {
    const poll = async () => {
      try {
        const state = await getGameDayState();
        applyGameState(state, "poll");
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
  const scheduledEventsRef = useRef<Set<string>>(new Set());
  const pendingEventTimersRef = useRef<number[]>([]);

  useEffect(() => {
    if (todayEventSchedule.length === 0) return;

    const totalBusinessMs = BUSINESS_SECONDS * 1000;
    const businessStartMs = playEndTimestampMs - totalBusinessMs;

    /** 같은 시간 이벤트를 시차(3초)를 두고 push */
    const scheduleAlert = (event: TodayEventScheduleItem, key: string, delayMs: number) => {
      if (triggeredEventsRef.current.has(key) || scheduledEventsRef.current.has(key)) {
        return;
      }

      scheduledEventsRef.current.add(key);
      const timerId = window.setTimeout(() => {
        scheduledEventsRef.current.delete(key);
        triggeredEventsRef.current.add(key);
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
        // 3D 이벤트 이펙트 트리거 (Unity + 프론트엔드 동시)
        // 지역 이벤트는 플레이어의 지역과 일치할 때만 애니메이션 표시
        const eventRegion = event.scope?.region ?? null;
        const playerRegion = storeRegionIndex !== null ? storeRegionIndex + 1 : null;
        const isGlobal = eventRegion === null;
        const isMyRegion = eventRegion !== null && eventRegion === playerRegion;

        const effectType = classifyEventEffect(event);
        if (effectType && (isGlobal || isMyRegion)) {
          const regionIdx = storeRegionIndex ?? 0;
          if (effectType === "TYPHOON") {
            sendToUnity(unityIframeRef, "SetWeather", `Wind,${regionIdx}`);
          } else if (effectType === "EARTHQUAKE") {
            sendToUnity(unityIframeRef, "SetWeather", `Earthquake,${regionIdx}`);
          } else if (effectType === "FIRE") {
            sendToUnity(unityIframeRef, "SetWeather", `Fire,${regionIdx}`);
          }
          triggerEffect(effectType);
        }

        queueDebugLog(
          buildPassiveDebugLog({
            dedupeKey: `event:${dayNumber}:${getCurrentDebugGameTime()}:${info.title}`,
            day: dayNumber,
            phase: debugPhaseLabel,
            gameTime: getCurrentDebugGameTime(),
            eventName: info.title,
          }),
        );
      }, delayMs);
      pendingEventTimersRef.current.push(timerId);
    };

    const check = () => {
      const elapsedMs = Date.now() - businessStartMs;
      const elapsedSec = Math.max(0, Math.min(elapsedMs / 1000, BUSINESS_SECONDS));
      const currentGameTime = elapsedToGameTime(elapsedSec);

      const sameTimeDelayCount = new Map<string, number>();

      for (const event of todayEventSchedule) {
        const key = `today:${buildTodayEventKey(event)}`;
        if (triggeredEventsRef.current.has(key) || scheduledEventsRef.current.has(key)) continue;
        if (currentGameTime >= event.time) {
          const delayCount = sameTimeDelayCount.get(event.time) ?? 0;
          sameTimeDelayCount.set(event.time, delayCount + 1);
          scheduleAlert(event, key, delayCount * 3000);
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
      scheduledEventsRef.current.clear();
      pendingEventTimersRef.current = [];
    };
  }, [todayEventSchedule, playEndTimestampMs, currentLocationName, currentMenuName]);

  /** 사용자가 직접 발주했을 때만 true → 도착 알림 활성화 */
  const didOrderEmergencyRef = useRef(false);
  const hasEmergencyArrivalAlertRef = useRef(false);

  function pushAlert(
    type: GameAlert["type"],
    title: string,
    description: string,
  ) {
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
  }

  const pushActionAlert = (title: string, description: string) => {
    pushAlert("action", title, description);
  };

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
          getGameDayState().then((state) => applyGameState(state, "emergency_refresh")).catch(() => {});
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
    setOptimisticUsedActions((prev) => new Set(prev).add(action));

    if (persistentActionTypes.has(action)) {
      setActiveEffects((prev) => new Set(prev).add(action));
    }

    const cost = options?.cost;
    const stockDelta = options?.stockDelta;
    const nextStock =
      typeof stockDelta === "number" && stockDelta !== 0 ? Math.max(0, stock + stockDelta) : stock;

    if (typeof cost === "number" && cost > 0) {
      statQueue.enqueueDelta({ balanceDelta: -cost });
    }

    if (typeof stockDelta === "number" && stockDelta !== 0) {
      statQueue.enqueueDelta({ stockDelta });

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

        <EventEffect3DOverlay />

        <RankingSidebar rankings={rankings} />
        <EventSidebar alerts={alerts} />
        <ActionBar onAction={handleAction} usedActions={usedActions} activeEffects={activeEffects} />
        <div className="absolute bottom-6 left-6 z-10 flex items-center gap-2 rounded-lg bg-black/40 px-3 py-1.5 text-[11px] font-medium text-white/80 backdrop-blur-sm">
          <span><kbd className="rounded bg-white/20 px-1.5 py-0.5">Space</kbd> 시점 변경</span>
          <span className="text-white/30">|</span>
          <span><kbd className="rounded bg-white/20 px-1.5 py-0.5">WASD</kbd> <kbd className="rounded bg-white/20 px-1.5 py-0.5">↑↓←→</kbd> 이동 (탑뷰)</span>
        </div>
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
            const actionGameTime = getCurrentDebugGameTime();

            if (discountValue <= 0) {
              return;
            }

            const response = await postDiscount(discountValue);

            setLiveSellingPrice(response.newPrice);
            syncDiscountActionState(true);

            const [stateResult] = await Promise.allSettled([getGameDayState()]);

            if (stateResult.status === "fulfilled") {
              applyGameState(stateResult.value, "action_sync");
            }

            if (stateResult.status === "fulfilled") {
              queueDebugLog(
                buildDebugLogFromState(stateResult.value, {
                  day: stateResult.value.day,
                  phase: debugPhaseLabel,
                  gameTime: actionGameTime,
                  actionName: "할인",
                  previousDisplayedGuests: displayedGuestsRef.current,
                  previousDisplayedStock: displayedStockRef.current,
                  previousDisplayedBalance: displayedBalanceRef.current,
                  dedupeKey: `action:discount:${stateResult.value.day}:${actionGameTime}`,
                  waitForDisplayedSettlement: true,
                }),
              );
            } else {
              queueActionDebugLog({
                actionName: "할인",
                dedupeKey: `action:discount:${dayNumber}:${actionGameTime}`,
              });
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
            const actionGameTime = getCurrentDebugGameTime();
            const previousBalance = displayedBalanceRef.current;
            const response = await postEmergencyOrder(menuId, quantity, salePrice);
            didOrderEmergencyRef.current = true;
            hasEmergencyArrivalAlertRef.current = false;
            const isNewMenuOrder = menuId !== currentOrder?.menuId;
            const arrivalLabel = formatEmergencyArrivalGameTime(response.arrivedTime, playEndTimestampMs);
            const arrivalText = arrivalLabel ? ` ${arrivalLabel} 도착 예정입니다.` : "";
            setEmergencyArriveAt(response.arrivedTime);

            queueActionDebugLog({
              actionName: "긴급발주",
              dedupeKey: `action:emergency:${dayNumber}:${actionGameTime}`,
              targetBalance: Math.max(0, previousBalance - response.totalCost),
              emergencyOrderCost: response.totalCost,
            });

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
            const actionGameTime = getCurrentDebugGameTime();
            const response = await postPromotion(promotionType);
            const promotionCost = response.cost || cost;

            syncPromotionActionState(true);

            const [stateSyncResult] = await Promise.allSettled([getGameDayState()]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value, "action_sync");
            }

            if (hasSyncedState) {
              queueDebugLog(
                buildDebugLogFromState(stateSyncResult.value, {
                  day: stateSyncResult.value.day,
                  phase: debugPhaseLabel,
                  gameTime: actionGameTime,
                  actionName: "홍보",
                  previousDisplayedGuests: displayedGuestsRef.current,
                  previousDisplayedStock: displayedStockRef.current,
                  previousDisplayedBalance: displayedBalanceRef.current,
                  promotionCost,
                  dedupeKey: `action:promotion:${stateSyncResult.value.day}:${actionGameTime}`,
                  waitForDisplayedSettlement: true,
                }),
              );
            } else {
              queueActionDebugLog({
                actionName: "홍보",
                dedupeKey: `action:promotion:${dayNumber}:${actionGameTime}`,
                targetBalance: Math.max(0, displayedBalanceRef.current - promotionCost),
                promotionCost,
              });
            }

            completeAction("promotion", {
              cost: hasSyncedState ? undefined : promotionCost,
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
            const actionGameTime = getCurrentDebugGameTime();
            const response = await postDonation(quantity);

            syncShareActionState(true);

            const [stateSyncResult] = await Promise.allSettled([getGameDayState()]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value, "action_sync");
            }

            if (hasSyncedState) {
              queueDebugLog(
                buildDebugLogFromState(stateSyncResult.value, {
                  day: stateSyncResult.value.day,
                  phase: debugPhaseLabel,
                  gameTime: actionGameTime,
                  actionName: "나눔",
                  previousDisplayedGuests: displayedGuestsRef.current,
                  previousDisplayedStock: displayedStockRef.current,
                  previousDisplayedBalance: displayedBalanceRef.current,
                  donationDeduction: -response.quantity,
                  dedupeKey: `action:share:${stateSyncResult.value.day}:${actionGameTime}`,
                  waitForDisplayedSettlement: true,
                }),
              );
            } else {
              queueActionDebugLog({
                actionName: "나눔",
                dedupeKey: `action:share:${dayNumber}:${actionGameTime}`,
                targetStock: Math.max(0, displayedStockRef.current - response.quantity),
                donationDeduction: -response.quantity,
              });
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
            const actionGameTime = getCurrentDebugGameTime();
            const previousBalance = displayedBalanceRef.current;
            const response = await updateStoreLocation(regionId);

            const [stateSyncResult, storeSyncResult] = await Promise.allSettled([
              getGameDayState(),
              getStore(),
            ]);
            const hasSyncedState = stateSyncResult.status === "fulfilled";

            if (hasSyncedState) {
              applyGameState(stateSyncResult.value, "action_sync");
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

            const moveCost = Math.max(0, previousBalance - response.balance);

            if (hasSyncedState) {
              queueDebugLog(
                buildDebugLogFromState(stateSyncResult.value, {
                  day: stateSyncResult.value.day,
                  phase: debugPhaseLabel,
                  gameTime: actionGameTime,
                  actionName: "이동",
                  previousDisplayedGuests: displayedGuestsRef.current,
                  previousDisplayedStock: displayedStockRef.current,
                  previousDisplayedBalance: displayedBalanceRef.current,
                  moveCost,
                  dedupeKey: `action:move:${stateSyncResult.value.day}:${actionGameTime}`,
                  waitForDisplayedSettlement: true,
                }),
              );
            } else {
              queueActionDebugLog({
                actionName: "이동",
                dedupeKey: `action:move:${dayNumber}:${actionGameTime}`,
                targetBalance: response.balance,
                moveCost,
              });
            }

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
