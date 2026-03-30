import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  getCurrentParticipation,
  getCurrentSeasonTopRankings,
  getGameWaitingStatus,
  type GameWaitingResponse,
  type ParticipationResponse,
} from "../api/game";
import { getShopItems, type ShopItemResponse } from "../api/shop";
import { getUserPoints } from "../api/user";
import {
  BUSINESS_SECONDS,
  DAY_SECONDS,
  NEXT_SEASON_WAIT_SECONDS,
  REPORT_SECONDS,
  SEASON_SUMMARY_SECONDS,
  TOTAL_DAYS,
  phaseToRoute,
  type SeasonPhase,
} from "../constants/gameTime";
import AnimatedNumber from "../components/common/AnimatedNumber";
import AppHeader from "../components/common/AppHeader";
import BankruptWarning from "../components/common/BankruptWarning";
import FloatingBubbles from "../components/common/FloatingBubbles";
import HeroCarousel, { AnimatedParticipants } from "../components/common/HeroCarousel";
import ItemSelector from "../components/common/ItemSelector";
import Modal from "../components/common/Modal";
import SeasonCTA from "../components/common/SeasonCTA";
import { isAuthenticated } from "../hooks/useAuth";
import { useGameStore } from "../stores/useGameStore";
import { setSeasonJoinIntent } from "../utils/seasonJoinIntent";
import {
  clearStoredSelectedDashboardItems,
  getDiscountLabel,
  getStoredSelectedDashboardItemIds,
  hydrateSelectedDashboardItems,
  setStoredSelectedDashboardItems,
  toggleSelectedDashboardItem,
  type DashboardSelectedItem,
} from "../utils/dashboardItems";

const dashBubbles = [
  {
    size: "w-64 h-64",
    position: "top-[5%] left-[-5%]",
    opacity: "opacity-40",
    delay: "0s",
    variant: "glass" as const,
  },
  {
    size: "w-48 h-48",
    position: "bottom-10 right-[-2%]",
    opacity: "opacity-30",
    delay: "2s",
    variant: "glass" as const,
  },
  {
    size: "w-24 h-24",
    position: "top-1/3 left-[40%]",
    opacity: "opacity-20",
    delay: "4s",
    variant: "glass" as const,
  },
];

const SUMMARY_SECONDS = SEASON_SUMMARY_SECONDS;
const SEASON_POLLING_INTERVAL_MS = 1000;
const ITEM_SELECTION_LOCK_MESSAGE =
  "이미 이번 시즌에 참가 중이라 아이템을 변경할 수 없습니다. 다음 시즌에 다시 선택하세요.";

const SHOP_ITEM_UI_BY_ID: Partial<
  Record<
    number,
    {
      name: string;
      category: DashboardSelectedItem["category"];
    }
  >
> = {
  1: { name: "원재료 할인권", category: "INGREDIENT" },
  2: { name: "임대료 할인권", category: "RENT" },
  3: { name: "원재료 할인권", category: "INGREDIENT" },
  4: { name: "임대료 할인권", category: "RENT" },
};

interface DashboardRouteState {
  showMidSeasonSetupExpiredModal?: boolean;
  hideGameReturnButton?: boolean;
}

const RETURNABLE_GAME_PHASES = new Set<SeasonPhase>([
  "LOCATION_SELECTION",
  "DAY_PREPARING",
  "DAY_BUSINESS",
  "DAY_REPORT",
]);

function toDiscountMultiplier(discountRate: number) {
  if (!Number.isFinite(discountRate)) {
    return 1;
  }

  if (discountRate > 1) {
    return Math.max(0, 1 - discountRate / 100);
  }

  return Math.max(0, discountRate);
}

function toDashboardItem(item: ShopItemResponse): DashboardSelectedItem {
  const uiMeta = SHOP_ITEM_UI_BY_ID[item.itemId];

  return {
    id: item.itemId,
    name: uiMeta?.name ?? item.itemName,
    category: uiMeta?.category ?? item.category,
    point: item.point,
    discountMultiplier: toDiscountMultiplier(item.discountRate),
  };
}

function getGroupMeta(category: DashboardSelectedItem["category"]) {
  if (category === "RENT") {
    return {
      label: "임대료 할인권",
      icon: "🏠",
      desc: (discountLabel: string) =>
        `일일 임대료 ${discountLabel.replace(" 할인", "")} 감소`,
    };
  }

  return {
    label: "원재료 할인권",
    icon: "🌿",
    desc: (discountLabel: string) =>
      `원재료 구매 비용 ${discountLabel.replace(" 할인", "")} 감소`,
  };
}

function isJoinableDay(currentDay: number | null): currentDay is number {
  return typeof currentDay === "number" && currentDay >= 1 && currentDay <= 5;
}

function getSecondsUntilNextBusinessDay(waitingStatus: GameWaitingResponse, currentDay: number) {
  const remaining = Math.max(0, waitingStatus.phaseRemainingSeconds ?? 0);

  switch (waitingStatus.seasonPhase) {
    case "LOCATION_SELECTION":
      return remaining;
    case "DAY_PREPARING":
      return remaining + BUSINESS_SECONDS + REPORT_SECONDS;
    case "DAY_BUSINESS":
      return remaining + REPORT_SECONDS;
    case "DAY_REPORT":
      return remaining;
    default:
      return currentDay >= 1 ? remaining : 0;
  }
}

function getSecondsUntilNextSeason(waitingStatus: GameWaitingResponse, currentDay: number | null) {
  const remaining = Math.max(0, waitingStatus.phaseRemainingSeconds ?? 0);

  if (waitingStatus.status === "WAITING") {
    return remaining;
  }

  const remainingDayCount =
    typeof currentDay === "number" ? Math.max(0, TOTAL_DAYS - currentDay) : 0;

  switch (waitingStatus.seasonPhase) {
    case "DAY_PREPARING":
      return (
        remaining +
        BUSINESS_SECONDS +
        REPORT_SECONDS +
        remainingDayCount * DAY_SECONDS +
        SUMMARY_SECONDS +
        NEXT_SEASON_WAIT_SECONDS
      );
    case "DAY_BUSINESS":
      return (
        remaining +
        REPORT_SECONDS +
        remainingDayCount * DAY_SECONDS +
        SUMMARY_SECONDS +
        NEXT_SEASON_WAIT_SECONDS
      );
    case "DAY_REPORT":
      return (
        remaining +
        remainingDayCount * DAY_SECONDS +
        SUMMARY_SECONDS +
        NEXT_SEASON_WAIT_SECONDS
      );
    case "SEASON_SUMMARY":
      return remaining + NEXT_SEASON_WAIT_SECONDS;
    case "NEXT_SEASON_WAITING":
      return remaining;
    default:
      return remaining;
  }
}

function getCurrentSeasonTitle(currentSeasonNumber: number | null) {
  if (typeof currentSeasonNumber === "number" && currentSeasonNumber > 0) {
    return `${currentSeasonNumber}번째 시즌`;
  }

  return "진행 중인 시즌";
}

function parseDashboardRouteState(value: unknown): DashboardRouteState {
  if (!value || typeof value !== "object") {
    return {};
  }

  const state = value as DashboardRouteState;

  return {
    showMidSeasonSetupExpiredModal: Boolean(state.showMidSeasonSetupExpiredModal),
    hideGameReturnButton: Boolean(state.hideGameReturnButton),
  };
}

async function fetchParticipationStatus() {
  return getCurrentParticipation();
}

function resolveSeasonCardData(
  waitingStatus: GameWaitingResponse | null,
  currentSeasonNumber: number | null,
) {

  if (!waitingStatus) {
    return {
      title: "시즌 정보를 불러오는 중",
      badgeText: "LOADING",
      timerLabel: "시즌 상태 확인 중",
      ctaLabel: "불러오는 중",
      tone: "waiting" as const,
      disabled: true,
    };
  }

  if (waitingStatus.status === "WAITING") {
    if ((waitingStatus.phaseRemainingSeconds ?? 0) <= 0) {
      return {
        title: `${waitingStatus.nextSeasonNumber ?? 1}번째 시즌`,
        badgeText: "STARTING",
        timerLabel: "시즌 시작 준비 중",
        ctaLabel: "잠시만 기다려주세요",
        tone: "waiting" as const,
        disabled: true,
      };
    }

    return {
      title: `${waitingStatus.nextSeasonNumber ?? 1}번째 시즌`,
      badgeText: "WAITING",
      timerLabel: "다음 시즌 시작까지",
      endTimestampMs:
        typeof waitingStatus.phaseRemainingSeconds === "number"
          ? Date.now() + waitingStatus.phaseRemainingSeconds * 1000
          : undefined,
      ctaLabel: "시즌 대기 중",
      tone: "waiting" as const,
      disabled: true,
    };
  }

  const currentDay = waitingStatus.currentDay ?? null;
  const title = getCurrentSeasonTitle(currentSeasonNumber);
  const badgeText =
    typeof currentDay === "number" ? `DAY ${currentDay} / ${TOTAL_DAYS}` : "IN PROGRESS";

  if (isJoinableDay(currentDay)) {
    const nextBusinessDay =
      waitingStatus.seasonPhase === "LOCATION_SELECTION"
        ? currentDay
        : Math.min(TOTAL_DAYS, waitingStatus.joinPlayableFromDay ?? currentDay + 1);

    return {
      title,
      badgeText,
      timerLabel: `DAY ${nextBusinessDay} 영업 시작까지`,
      endTimestampMs:
        Date.now() + getSecondsUntilNextBusinessDay(waitingStatus, currentDay) * 1000,
      ctaLabel: "게임 참여하기",
      ctaTo: "/game/setup/location",
      tone: "active" as const,
      disabled: false,
    };
  }

  return {
    title,
    badgeText,
    timerLabel: "다음 시즌 시작까지",
    endTimestampMs:
      Date.now() + getSecondsUntilNextSeason(waitingStatus, currentDay) * 1000,
    ctaLabel: "시즌 대기 중",
    tone: "waiting" as const,
    disabled: true,
  };
}

export default function DashboardPage() {
  const loggedIn = isAuthenticated();
  const location = useLocation();
  const navigate = useNavigate();
  const [currentPoints, setCurrentPoints] = useState<number | null>(null);
  const [shopItems, setShopItems] = useState<DashboardSelectedItem[]>([]);
  const [selectedItemIds, setSelectedItemIds] = useState<number[]>(
    getStoredSelectedDashboardItemIds,
  );
  const [waitingStatus, setWaitingStatus] = useState<GameWaitingResponse | null>(null);
  const [participation, setParticipation] = useState<ParticipationResponse | null>(null);
  const [currentSeasonNumber, setCurrentSeasonNumber] = useState<number | null>(null);
  const [isResolvingCurrentSeasonNumber, setIsResolvingCurrentSeasonNumber] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [gameReturnPath, setGameReturnPath] = useState<string | null>(null);
  const [isBankruptWarningVisible, setIsBankruptWarningVisible] = useState(false);
  const bankruptNoticeSeasonNumber = useGameStore((state) => state.bankruptNoticeSeasonNumber);
  const bankruptReportDay = useGameStore((state) => state.bankruptReportDay);
  const clearBankruptNotice = useGameStore((state) => state.clearBankruptNotice);

  const routeState = useMemo(
    () => parseDashboardRouteState(location.state),
    [location.state],
  );
  const showMidSeasonSetupExpiredModal = Boolean(routeState.showMidSeasonSetupExpiredModal);
  const isActiveSeasonParticipant =
    participation?.joinedCurrentSeason === true && participation?.storeAccessible === true;
  const isItemSelectionLocked = isActiveSeasonParticipant;
  const hideGameReturnButton = Boolean(routeState.hideGameReturnButton);
  const [hasForcedExitSuppression, setHasForcedExitSuppression] = useState(hideGameReturnButton);
  const selectedItems = useMemo(
    () => hydrateSelectedDashboardItems(selectedItemIds, shopItems),
    [selectedItemIds, shopItems],
  );
  const selectableItems = useMemo(
    () => (isItemSelectionLocked ? [] : selectedItems),
    [isItemSelectionLocked, selectedItems],
  );
  const effectiveSelectedIds = useMemo(
    () => selectableItems.map((item) => item.id),
    [selectableItems],
  );
  const itemGroups = useMemo(() => {
    const groupedItems = new Map<
      string,
      ReturnType<typeof getGroupMeta> & {
        group: string;
        items: Array<{
          id: number;
          name: string;
          group: string;
          desc: string;
          discount: string;
          price: number;
          owned: boolean;
        }>;
      }
    >();

    shopItems.forEach((item) => {
      const meta = getGroupMeta(item.category);
      const existingGroup = groupedItems.get(item.category);
      const nextItem = {
        id: item.id,
        name: item.name,
        group: item.category,
        desc: meta.desc(getDiscountLabel(item.discountMultiplier)),
        discount: getDiscountLabel(item.discountMultiplier),
        price: item.point,
        owned: false,
      };

      if (existingGroup) {
        existingGroup.items.push(nextItem);
        return;
      }

      groupedItems.set(item.category, {
        group: item.category,
        label: meta.label,
        icon: meta.icon,
        desc: meta.desc,
        items: [nextItem],
      });
    });

    return Array.from(groupedItems.values()).map(({ group, label, icon, items }) => ({
      group,
      label,
      icon,
      items,
    }));
  }, [shopItems]);

  const pendingUsedPoints = useMemo(
    () => selectableItems.reduce((sum, item) => sum + item.point, 0),
    [selectableItems],
  );
  const displayedPoints =
    currentPoints === null ? 0 : Math.max(0, currentPoints - pendingUsedPoints);
  const participationSyncKey = waitingStatus
    ? `${waitingStatus.status ?? "null"}:${waitingStatus.nextSeasonNumber ?? "null"}:${waitingStatus.seasonPhase ?? "null"}`
    : null;
  const seasonCard = useMemo(() => {
    const card = resolveSeasonCardData(waitingStatus, currentSeasonNumber);
    if (!loggedIn) {
      return {
        ...card,
        ctaLabel: "로그인하고 대기하기",
        ctaTo: "/login",
        disabled: false,
      };
    }
    return card;
  }, [currentSeasonNumber, loggedIn, waitingStatus]);
  const noticeDay = waitingStatus?.currentDay ?? null;
  const showDeadlineNotice = Boolean(
    waitingStatus?.status === "IN_PROGRESS" &&
      participation !== null &&
      typeof noticeDay === "number" &&
      !isActiveSeasonParticipant,
  );
  const showBankruptRetryNotice = Boolean(
    isBankruptWarningVisible &&
      waitingStatus?.status === "IN_PROGRESS" &&
      participation !== null &&
      isJoinableDay(noticeDay) &&
      !isActiveSeasonParticipant,
  );
  const recentSeasonNumber = useMemo(() => {
    if (!waitingStatus) {
      return null;
    }

    if (waitingStatus.seasonPhase === "SEASON_SUMMARY") {
      if (currentSeasonNumber != null) {
        return currentSeasonNumber;
      }

      return waitingStatus.nextSeasonNumber != null
        ? waitingStatus.nextSeasonNumber - 1
        : null;
    }

    if (waitingStatus.seasonPhase === "NEXT_SEASON_WAITING") {
      return (waitingStatus.nextSeasonNumber ?? 1) - 1;
    }

    return null;
  }, [currentSeasonNumber, waitingStatus]);
  const showRecentSeasonRankingButton =
    (waitingStatus?.seasonPhase === "SEASON_SUMMARY" ||
      waitingStatus?.seasonPhase === "NEXT_SEASON_WAITING") &&
    (recentSeasonNumber ?? 0) >= 1;

  const syncParticipation = useCallback(async () => fetchParticipationStatus(), []);

  useEffect(() => {
    if (shopItems.length === 0) {
      return;
    }

    if (isItemSelectionLocked) {
      return;
    }

    setStoredSelectedDashboardItems(selectedItems);
  }, [isItemSelectionLocked, selectedItems, shopItems.length]);

  useEffect(() => {
    if (!isItemSelectionLocked) {
      return;
    }

    clearStoredSelectedDashboardItems();
    setSelectedItemIds((prev) => (prev.length === 0 ? prev : []));
  }, [isItemSelectionLocked]);

  useEffect(() => {
    let isCancelled = false;

    async function loadDashboard() {
      if (!loggedIn) {
        try {
          const waitingResult = await getGameWaitingStatus();
          if (!isCancelled) {
            setWaitingStatus(waitingResult);
          }
        } catch {
          // ignore
        }
        if (!isCancelled) {
          setIsLoading(false);
        }
        return;
      }

      const [pointsResult, itemsResult, waitingResult, participationResult] = await Promise.allSettled([
        getUserPoints(),
        getShopItems(),
        getGameWaitingStatus(),
        syncParticipation(),
      ]);

      if (isCancelled) {
        return;
      }

      const errors: string[] = [];

      if (pointsResult.status === "fulfilled") {
        setCurrentPoints(pointsResult.value.currentPoints);
      } else {
        errors.push("포인트 정보를 불러오지 못했습니다.");
      }

      if (itemsResult.status === "fulfilled") {
        setShopItems(
          itemsResult.value.items
            .map(toDashboardItem)
            .sort((left, right) => left.id - right.id),
        );
      } else {
        errors.push("상점 아이템을 불러오지 못했습니다.");
      }

      if (waitingResult.status === "fulfilled") {
        setWaitingStatus(waitingResult.value);
      } else {
        errors.push("시즌 정보를 불러오지 못했습니다.");
      }

      if (participationResult.status === "fulfilled") {
        setParticipation(participationResult.value);
      } else {
        errors.push("참여 상태를 불러오지 못했습니다.");
      }

      setLoadError(errors[0] ?? null);
      setIsLoading(false);
    }

    void loadDashboard();

    return () => {
      isCancelled = true;
    };
  }, [loggedIn, syncParticipation]);

  useEffect(() => {
    if (!loggedIn || participationSyncKey === null) {
      return;
    }

    let isCancelled = false;

    async function resyncParticipation() {
      try {
        const nextParticipation = await fetchParticipationStatus();

        if (!isCancelled) {
          setParticipation(nextParticipation);
        }
      } catch {
        // Keep the last known participation state when refresh fails.
      }
    }

    void resyncParticipation();

    return () => {
      isCancelled = true;
    };
  }, [loggedIn, participationSyncKey]);

  useEffect(() => {
    if (hideGameReturnButton) {
      setHasForcedExitSuppression(true);
    }
  }, [hideGameReturnButton]);

  useEffect(() => {
    if (
      !hasForcedExitSuppression ||
      !waitingStatus ||
      !participation
    ) {
      return;
    }

    if (
      waitingStatus.status !== "IN_PROGRESS" ||
      !participation.joinedCurrentSeason ||
      !participation.storeAccessible
    ) {
      setHasForcedExitSuppression(false);
    }
  }, [hasForcedExitSuppression, participation, waitingStatus]);

  useEffect(() => {
    if (
      hasForcedExitSuppression ||
      !waitingStatus ||
      waitingStatus.status !== "IN_PROGRESS" ||
      !isActiveSeasonParticipant
    ) {
      setGameReturnPath(null);
      return;
    }

    const phase = waitingStatus.seasonPhase as SeasonPhase | null;
    const currentDay = waitingStatus.currentDay;
    const playableFromDay = participation?.playableFromDay;

    if (typeof currentDay !== "number" || !phase) {
      setGameReturnPath(null);
      return;
    }

    // 파산 유저 또는 시즌 종료 시 버튼 숨김
    if (
      bankruptNoticeSeasonNumber != null ||
      (bankruptReportDay != null && !(phase === "DAY_REPORT" && currentDay === bankruptReportDay)) ||
      phase === "SEASON_SUMMARY" ||
      phase === "NEXT_SEASON_WAITING" ||
      !RETURNABLE_GAME_PHASES.has(phase)
    ) {
      setGameReturnPath(null);
      return;
    }

    if (typeof playableFromDay === "number" && currentDay < playableFromDay) {
      setGameReturnPath("/game/waiting");
      return;
    }

    const path = phaseToRoute(phase, currentDay);
    setGameReturnPath(path && path !== "/" ? path : null);
  }, [
    bankruptNoticeSeasonNumber,
    bankruptReportDay,
    hasForcedExitSuppression,
    isActiveSeasonParticipant,
    participation,
    waitingStatus,
  ]);

  useEffect(() => {
    if (!loggedIn) {
      return;
    }

    let isCancelled = false;

    async function loadCurrentSeasonNumber() {
      if (waitingStatus?.status !== "IN_PROGRESS") {
        setCurrentSeasonNumber(null);
        setIsResolvingCurrentSeasonNumber(false);
        return;
      }

      setIsResolvingCurrentSeasonNumber(true);

      try {
        const topRankings = await getCurrentSeasonTopRankings();

        if (!isCancelled) {
          setCurrentSeasonNumber(topRankings.seasonId);
        }
      } catch {
        if (!isCancelled) {
          setCurrentSeasonNumber(null);
        }
      } finally {
        if (!isCancelled) {
          setIsResolvingCurrentSeasonNumber(false);
        }
      }
    }

    void loadCurrentSeasonNumber();

    return () => {
      isCancelled = true;
    };
  }, [loggedIn, waitingStatus?.status]);

  useEffect(() => {
    if (bankruptNoticeSeasonNumber === null) {
      setIsBankruptWarningVisible(false);
      return;
    }

    if (!waitingStatus) {
      return;
    }

    if (waitingStatus.status !== "IN_PROGRESS") {
      clearBankruptNotice();
      setIsBankruptWarningVisible(false);
      return;
    }

    if (isResolvingCurrentSeasonNumber) {
      return;
    }

    if (currentSeasonNumber === null) {
      return;
    }

    const isBankruptSeason = currentSeasonNumber === bankruptNoticeSeasonNumber;

    if (isBankruptSeason) {
      setIsBankruptWarningVisible(true);
      return;
    }

    clearBankruptNotice();
    setIsBankruptWarningVisible(false);
  }, [
    bankruptNoticeSeasonNumber,
    clearBankruptNotice,
    currentSeasonNumber,
    isResolvingCurrentSeasonNumber,
    waitingStatus,
  ]);

  useEffect(() => {
    let isCancelled = false;

    const timer = window.setTimeout(async () => {
      try {
        if (!loggedIn) {
          const nextWaitingStatus = await getGameWaitingStatus();
          if (!isCancelled) {
            setWaitingStatus(nextWaitingStatus);
          }
          return;
        }

        const [nextWaitingStatusResult, nextParticipationResult] = await Promise.allSettled([
          getGameWaitingStatus(),
          getCurrentParticipation(),
        ]);

        if (!isCancelled) {
          if (nextWaitingStatusResult.status === "fulfilled") {
            setWaitingStatus(nextWaitingStatusResult.value);
          }

          if (nextParticipationResult.status === "fulfilled") {
            setParticipation(nextParticipationResult.value);
          }
        }
      } catch {
        // Ignore transient polling errors and keep the last known state.
      }
    }, SEASON_POLLING_INTERVAL_MS);

    return () => {
      isCancelled = true;
      window.clearTimeout(timer);
    };
  }, [loggedIn, waitingStatus]);

  const handleSeasonCountdownComplete = async () => {
    try {
      const latestStatus = await getGameWaitingStatus();
      setWaitingStatus(latestStatus);
    } catch {
      // Keep the last known season state when refresh fails.
    }
  };

  const handleSeasonCtaClick = () => {
    if (seasonCard.ctaTo === "/game/setup/location") {
      setSeasonJoinIntent();
    }
  };

  const handleToggle = (id: number) => {
    if (isItemSelectionLocked) {
      return;
    }

    const item = shopItems.find((entry) => entry.id === id);

    if (!item) {
      return;
    }

    setSelectedItemIds((prev) => toggleSelectedDashboardItem(prev, item, shopItems));
  };

  return (
    <div className="relative flex min-h-screen w-full flex-col overflow-x-hidden bg-[#FDFDFB] font-display text-slate-900">
      <FloatingBubbles bubbles={dashBubbles} />
      <AppHeader />

      <main className={`z-10 mx-auto flex w-full max-w-[1100px] flex-1 flex-col px-6 pb-12 pt-24 md:px-12 ${loggedIn ? "items-center" : "items-center justify-center"}`}>
        {loggedIn && loadError && (
          <div className="mb-6 w-full rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
            {loadError}
          </div>
        )}

        <div className={`grid w-full grid-cols-1 gap-8 ${loggedIn ? "md:grid-cols-2 items-start" : "md:grid-cols-[1.2fr_1fr] items-start"}`}>
          <div className={`flex flex-col gap-6 ${loggedIn ? "lg:w-[95%]" : ""}`}>
            {loggedIn ? (
              <>
                <div className="flex w-full flex-col gap-2 rounded-[20px] bg-white p-6 shadow-soft">
                  <span className="text-sm font-medium text-gray-500">보유 포인트</span>
                  <div className="flex items-baseline gap-2">
                    <AnimatedNumber
                      value={displayedPoints}
                      suffix="P"
                      className="font-countdown text-[40px] font-bold leading-none tracking-tight text-primary"
                    />
                    {currentPoints !== null && pendingUsedPoints > 0 && (
                      <span className="text-sm font-medium text-slate-400">/ {currentPoints}P</span>
                    )}
                  </div>
                  {currentPoints !== null && pendingUsedPoints > 0 && (
                    <p className="text-xs text-slate-400">
                      현재 선택 기준으로 {pendingUsedPoints}P 사용 예정
                    </p>
                  )}
                </div>

                <ItemSelector
                  groups={itemGroups}
                  selectedIds={effectiveSelectedIds}
                  onToggle={handleToggle}
                  availablePoints={displayedPoints}
                  disabled={isItemSelectionLocked}
                  disabledMessage={ITEM_SELECTION_LOCK_MESSAGE}
                  isLoading={isLoading && shopItems.length === 0}
                />

                {/* 튜토리얼 버튼 */}
                <button
                  onClick={() => navigate("/tutorial")}
                  className="w-full rounded-[20px] bg-white p-5 shadow-soft border-2 border-slate-100 hover:border-primary hover:bg-primary/5 hover:shadow-md transition-all text-left group"
                >
                  <div className="flex items-center gap-3">
                    <div className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary group-hover:bg-primary/20 transition-colors">
                      <span className="material-symbols-outlined text-[22px]">menu_book</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <span className="text-sm font-bold text-slate-800">게임 가이드</span>
                      <p className="text-xs text-slate-500 mt-0.5">게임 플레이 방법을 알아보세요</p>
                    </div>
                    <span className="material-symbols-outlined text-slate-300 group-hover:text-primary transition-colors">chevron_right</span>
                  </div>
                </button>
              </>
            ) : (
              <div className="w-full [&>div]:aspect-auto [&>div]:min-h-[400px]">
                <HeroCarousel />
              </div>
            )}
          </div>

          <div className="flex flex-col gap-6">
            <SeasonCTA
              {...seasonCard}
              onCtaClick={handleSeasonCtaClick}
              onCountdownComplete={
                waitingStatus?.status === "WAITING"
                  ? handleSeasonCountdownComplete
                  : undefined
              }
            />

            {!loggedIn && (
              <AnimatedParticipants count={waitingStatus?.participantCount ?? 0} />
            )}

            {loggedIn && gameReturnPath && (
              <button
                onClick={() => navigate(gameReturnPath)}
                className="w-full flex items-center justify-center gap-2 rounded-2xl bg-primary px-6 py-4 text-white font-bold shadow-lg hover:bg-primary-dark transition-all hover:-translate-y-0.5"
              >
                <span className="material-symbols-outlined text-xl">sports_esports</span>
                게임으로 돌아가기
              </button>
            )}

            {loggedIn && showRecentSeasonRankingButton && (
              <button
                onClick={() => navigate("/ranking")}
                className="w-full flex items-center justify-center gap-2 rounded-2xl bg-slate-800 px-6 py-4 text-white font-bold shadow-lg hover:bg-slate-900 transition-all hover:-translate-y-0.5"
              >
                <span className="material-symbols-outlined text-xl">leaderboard</span>
                최근 시즌 랭킹 조회하기
              </button>
            )}

            {loggedIn && showDeadlineNotice && (
              <div className="rounded-[20px] border border-amber-200 bg-amber-50/90 p-5 shadow-soft">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5 flex size-9 items-center justify-center rounded-full bg-amber-100 text-amber-700">
                    <span className="material-symbols-outlined text-[20px]">info</span>
                  </div>
                  <div>
                    <p className="text-sm font-bold text-amber-800">참여 유의사항</p>
                    <p className="mt-1 text-sm leading-6 text-amber-700">
                      중간 참여자는 DAY 6 시작 전까지 지역과 팝업명 설정을 모두 완료해야 합니다.
                      DAY 6 이후에는 다음 시즌에 참여할 수 있습니다.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {loggedIn && showBankruptRetryNotice && <BankruptWarning />}
          </div>
        </div>
      </main>

      <Modal
        isOpen={loggedIn && showMidSeasonSetupExpiredModal}
        onClose={() => navigate(location.pathname, { replace: true, state: null })}
        title="설정 시간이 종료되었습니다"
      >
        <p className="text-sm leading-6 text-slate-600">
          DAY 6 시작 전까지 지역과 팝업명 설정을 완료하지 못해 이번 시즌에는 참여할 수
          없습니다. 다음 시즌이 시작되면 다시 참여해주세요.
        </p>
        <button
          type="button"
          onClick={() => navigate(location.pathname, { replace: true, state: null })}
          className="mt-5 flex h-11 w-full items-center justify-center rounded-xl bg-primary font-semibold text-slate-900 transition-colors hover:bg-primary-dark hover:text-white"
        >
          확인
        </button>
      </Modal>
    </div>
  );
}
