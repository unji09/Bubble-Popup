import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useOutletContext, useParams } from "react-router-dom";
import type { GameGuardContext } from "../router/GameGuard";
import AppHeader from "../components/common/AppHeader";
import CountdownTimer from "../components/common/CountdownTimer";
import MenuSelector from "../components/game/MenuSelector";
import PriceSlider from "../components/game/PriceSlider";
import QuantityCounter from "../components/game/QuantityCounter";
import CozyNewspaper from "../components/game/CozyNewspaper";
import { postRegularOrder } from "../api/order";
import { getGameWaitingStatus, type GameWaitingResponse } from "../api/game";
import {
  getNewsRanking,
  getTodayNews,
  type AreaRankingItemResponse,
  type NewsRankingResponse,
} from "../api/news";
import { getStore, getStoreMenus, type StoreMenuResponse, type StoreResponse } from "../api/store";
import bubbleNewsImage from "../assets/Bubblenewsimg.png";
import {
  applyDiscount,
  normalizeDiscountMultiplier,
} from "../utils/dashboardItems";

interface PrepMenu {
  id: number;
  emoji: string;
  name: string;
  costPrice: number;
  previousSalePrice: number;
  ingredientDiscountMultiplier: number;
}

const fallbackMenus: PrepMenu[] = [
  { id: 1, emoji: "🍞", name: "빵", costPrice: 1800, previousSalePrice: 3200, ingredientDiscountMultiplier: 1 },
  { id: 2, emoji: "🍢", name: "마라꼬치", costPrice: 2200, previousSalePrice: 3900, ingredientDiscountMultiplier: 1 },
  { id: 3, emoji: "🍬", name: "젤리", costPrice: 900, previousSalePrice: 1800, ingredientDiscountMultiplier: 1 },
  { id: 4, emoji: "🍽️", name: "떡볶이", costPrice: 2500, previousSalePrice: 4300, ingredientDiscountMultiplier: 1 },
  { id: 5, emoji: "🍔", name: "햄버거", costPrice: 3100, previousSalePrice: 5600, ingredientDiscountMultiplier: 1 },
  { id: 6, emoji: "🍨", name: "아이스크림", costPrice: 1400, previousSalePrice: 2600, ingredientDiscountMultiplier: 1 },
  { id: 7, emoji: "🍗", name: "닭강정", costPrice: 2800, previousSalePrice: 4900, ingredientDiscountMultiplier: 1 },
  { id: 8, emoji: "🌮", name: "타코", costPrice: 2600, previousSalePrice: 4500, ingredientDiscountMultiplier: 1 },
  { id: 9, emoji: "🌭", name: "핫도그", costPrice: 1700, previousSalePrice: 3000, ingredientDiscountMultiplier: 1 },
  { id: 10, emoji: "🧋", name: "버블티", costPrice: 2300, previousSalePrice: 4100, ingredientDiscountMultiplier: 1 },
];

const mockPopulationRanking = [
  { rank: 1, name: "홍대", change: "2.4%", positive: true, barWidth: "85%" },
  { rank: 2, name: "성수", change: "-", positive: true, barWidth: "65%" },
  { rank: 3, name: "부산", change: "0.8%", positive: false, barWidth: "45%" },
];

const mockRevenueRanking = [
  { rank: 1, name: "홍대", change: "5.2%", positive: true, barWidth: "92%" },
  { rank: 2, name: "성수", change: "-", positive: true, barWidth: "58%" },
  { rank: 3, name: "부산", change: "-", positive: true, barWidth: "35%" },
];

const mockNews = [
  { id: 1, title: "요즘 뜨는 디저트, '약과 쿠키' 인기 급상승 중", content: "전통 간식인 약과와 서양식 쿠키를 결합한 '약과 쿠키'가 MZ세대를 중심으로 큰 인기를 끌고 있습니다. 특히 편의점과 프랜차이즈 카페들이 앞다퉈 신제품을 출시하며 경쟁이 치열해지고 있습니다. 전문가들은 이러한 '할매니얼(할머니+밀레니얼)' 트렌드가 당분간 지속될 것으로 전망했습니다." },
  { id: 2, title: "원두 가격 3개월 만에 소폭 하락세... 카페 사장님들 '안도'", content: "국제 원두 가격이 3개월 만에 하락세로 돌아섰습니다. 브라질 수확량 증가와 물류비 안정이 주요 원인으로 분석됩니다." },
  { id: 3, title: "이번 주말 전국 비 예보, 배달 주문량 증가 예상", content: "기상청에 따르면 이번 주말 전국적으로 비가 내릴 예정입니다. 외출이 줄어드는 만큼 배달 수요가 늘어날 것으로 보입니다." },
  { id: 4, title: "성공적인 카페 운영을 위한 5가지 인테리어 팁", content: "작은 인테리어 변화가 매출에 큰 영향을 줄 수 있습니다. 조명, 좌석 배치, 음악, 향기, 그린 인테리어 5가지를 소개합니다." },
  { id: 5, title: "홍대 주변, 20대 유동인구 지난달 대비 15% 증가", content: "홍대 일대의 20대 유동인구가 지난달 대비 15% 증가한 것으로 나타났습니다. 봄 시즌 개강 효과와 맞물린 것으로 분석됩니다." },
];

void mockNews;

type Tab = "prep" | "news";
type RegularOrderStatus = "idle" | "submitting" | "submitted";

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

interface PrepNewsItem {
  id: number;
  title: string;
  content?: string;
}

interface PrepNewsRankingItem {
  rank: number;
  name: string;
  change: string;
  positive: boolean;
}

interface PrepNewsRankingSection {
  title: string;
  eyebrow: string;
  items?: PrepNewsRankingItem[];
  imageSrc?: string;
  imageAlt?: string;
  caption?: string;
  captionDetail?: string;
  meta?: string[];
}

function roundToHundreds(value: number) {
  return Math.round(value / 100) * 100;
}

function formatRankingChange(changeRate: number) {
  return `${Math.abs(changeRate).toFixed(1)}%`;
}

function mapAreaRankings(items: AreaRankingItemResponse[]) {
  return items.map((item) => ({
    rank: item.rank,
    name: item.areaName,
    change: formatRankingChange(item.changeRate),
    positive: item.changeRate > 0,
  }));
}

function buildNewsSidebarSections(day: number, rankingResponse: NewsRankingResponse | null) {
  const sections: PrepNewsRankingSection[] = [];

  if (rankingResponse) {
    sections.push({
      title: "유동인구 순위",
      eyebrow: "Foot Traffic Ranking",
      items: mapAreaRankings(rankingResponse.areaTrafficRanking.slice(0, 3)),
    });

    if (day >= 2) {
      sections.push({
        title: "지역 매출 순위",
        eyebrow: "Regional Revenue Ranking",
        items: mapAreaRankings(rankingResponse.areaRevenueRanking),
      });
    }
  }

  if (day === 1) {
    sections.push({
      title: "스팟 현장",
      eyebrow: "",
      imageSrc: bubbleNewsImage,
      caption: "개점 첫날, 입장 대기줄에 긴장감 팽팽",
      captionDetail:
        "초기 반응과 기대감이 높아지면서 첫날 흥행 가능성에 관심이 쏠리고 있습니다.",
      meta: ["DAY 1 현장 스케치"],
      imageAlt: "개점 첫날, 매장 앞에 모여든 방문객들",
    });
  }

  return sections;
}

function mapTodayNews(items: { newsId: number; newsTitle: string; newsContent: string }[]) {
  return items.map((item) => ({
    id: item.newsId,
    title: item.newsTitle,
    content: item.newsContent,
  }));
}

function getRecommendedPrice(costPrice: number) {
  return roundToHundreds(costPrice * 2.5);
}

function getSellingPriceDefault(
  costPrice: number,
  previousSalePrice: number,
  day: number,
  playableday: number | null,
) {
  if (playableday !== null && day > playableday) {
    return previousSalePrice;
  }

  return getRecommendedPrice(costPrice);
}

function isRegularOrderDay(day: number) {
  return day >= 1 && day <= 7 && day % 2 === 1;
}

function isOrderPreparingPhase(waitingStatus: GameWaitingResponse | null, day: number) {
  return (
    waitingStatus?.status === "IN_PROGRESS" &&
    waitingStatus.seasonPhase === "DAY_PREPARING" &&
    waitingStatus.currentDay === day
  );
}

function normalizeMenuName(value: string | null | undefined) {
  return value?.trim() ?? "";
}

function resolveSelectedMenuId(
  menus: PrepMenu[],
  currentMenuId: number | null,
  day: number,
  storeMenuName: string | null,
) {
  const fallbackMenuId =
    menus.find((menu) => menu.id === currentMenuId)?.id ?? menus[0]?.id ?? null;

  if (day < 2) {
    return fallbackMenuId;
  }

  const normalizedStoreMenuName = normalizeMenuName(storeMenuName);

  if (!normalizedStoreMenuName) {
    return fallbackMenuId;
  }

  return (
    menus.find((menu) => normalizeMenuName(menu.name) === normalizedStoreMenuName)?.id ??
    fallbackMenuId
  );
}

function mapStoreMenusToPrepMenus(menus: StoreMenuResponse[]) {
  return menus.map((menu) => {
    const fallbackMenu = fallbackMenus.find((entry) => entry.id === menu.menuId);

    return {
      id: menu.menuId,
      emoji: fallbackMenu?.emoji ?? "🍽️",
      name: menu.menuName,
      costPrice: menu.ingredientPrice,
      previousSalePrice:
        fallbackMenu?.previousSalePrice ?? getRecommendedPrice(menu.ingredientPrice),
      ingredientDiscountMultiplier: normalizeDiscountMultiplier(menu.discount),
    } satisfies PrepMenu;
  });
}

export default function PrepPage() {
  const { day: dayParam } = useParams<{ day: string }>();
  const guardContext = useOutletContext<GameGuardContext>();
  const parsedDay = Number(dayParam);
  const day = Number.isNaN(parsedDay) ? 0 : parsedDay;
  const isRegularOrderRouteDay = isRegularOrderDay(day);
  const [tab, setTab] = useState<Tab>("prep");
  const [menus, setMenus] = useState<PrepMenu[]>(fallbackMenus);
  const [isMenusLoading, setIsMenusLoading] = useState(true);
  const [menuError, setMenuError] = useState<string | null>(null);
  const [currentStoreMenuName, setCurrentStoreMenuName] = useState<string | null>(null);
  const [playableday, setPlayableday] = useState<number | null>(null);
  const [waitingStatus, setWaitingStatus] = useState<GameWaitingResponse | null>(null);
  const [isWaitingStatusLoading, setIsWaitingStatusLoading] = useState(true);
  const [newsItems, setNewsItems] = useState<PrepNewsItem[]>([]);
  const [newsRanking, setNewsRanking] = useState<NewsRankingResponse | null>(null);
  const [isNewsLoading, setIsNewsLoading] = useState(true);
  const [newsError, setNewsError] = useState<string | null>(null);
  const [regularOrderStatus, setRegularOrderStatus] = useState<RegularOrderStatus>("idle");
  const [regularOrderError, setRegularOrderError] = useState<string | null>(null);
  const [selectedMenu, setSelectedMenu] = useState<number | null>(1);
  const [quantity, setQuantity] = useState(120);
  const [expandedNewsId, setExpandedNewsId] = useState<number | null>(null);
  const selectedMenuData = menus.find((menu) => menu.id === selectedMenu) ?? menus[0] ?? fallbackMenus[0];
  const originalCostPrice = selectedMenuData.costPrice;
  const ingredientDiscountMultiplier = selectedMenuData.ingredientDiscountMultiplier;
  const hasItemDiscount = ingredientDiscountMultiplier < 1;
  const discountRate = Math.max(0, Math.round((1 - ingredientDiscountMultiplier) * 100));
  const discountedCostPrice = applyDiscount(
    originalCostPrice,
    ingredientDiscountMultiplier,
  );
  const recommendedPrice = getRecommendedPrice(originalCostPrice);
  const maxSellingPrice = roundToHundreds(recommendedPrice * 2);
  const defaultSellingPrice = getSellingPriceDefault(
    originalCostPrice,
    selectedMenuData.previousSalePrice,
    day,
    playableday,
  );
  const [price, setPrice] = useState(defaultSellingPrice);
  const totalCost = originalCostPrice * quantity;
  const discountedTotalCost = discountedCostPrice * quantity;
  const discountAmount = totalCost - discountedTotalCost;
  const isServerPreparing = isOrderPreparingPhase(waitingStatus, day);
  const canPrepareToday = isRegularOrderRouteDay && isServerPreparing;
  const normalizedCurrentStoreMenuName = normalizeMenuName(currentStoreMenuName);
  const shouldShowMenuStatus = playableday !== null && day > playableday && normalizedCurrentStoreMenuName.length > 0;
  const isSubmittingRegularOrder = regularOrderStatus === "submitting";
  const hasSubmittedRegularOrder = regularOrderStatus === "submitted";
  const canSubmitRegularOrder =
    canPrepareToday &&
    !isWaitingStatusLoading &&
    !isMenusLoading &&
    !menuError &&
    regularOrderStatus === "idle";
  const isPrepFormLocked =
    !canPrepareToday || isSubmittingRegularOrder || hasSubmittedRegularOrder;
  const prepEndTimestampMs = useMemo(() => {
    if (!isServerPreparing || typeof waitingStatus?.phaseRemainingSeconds !== "number") {
      return undefined;
    }

    return Date.now() + waitingStatus.phaseRemainingSeconds * 1000;
  }, [isServerPreparing, waitingStatus?.phaseRemainingSeconds]);
  const menuSelectorMenus = useMemo(
    () =>
      menus.map((menu) => {
        const isCurrentSellingMenu =
          shouldShowMenuStatus &&
          normalizeMenuName(menu.name) === normalizedCurrentStoreMenuName;

        return {
          id: menu.id,
          emoji: menu.emoji,
          name: menu.name,
          isCurrentSellingMenu,
          isSelectedNewMenu:
            shouldShowMenuStatus && selectedMenu === menu.id && !isCurrentSellingMenu,
        };
      }),
    [menus, normalizedCurrentStoreMenuName, selectedMenu, shouldShowMenuStatus],
  );
  const newsSidebarSections =
    day === 1
      ? [
          {
            title: "유동인구 순위",
            eyebrow: "Foot Traffic Ranking",
            items: mockPopulationRanking,
          },
          {
            title: "오픈 현장",
            eyebrow: "",
            imageSrc: bubbleNewsImage,
            caption: "개점 첫날, 도심 한복판에 긴 대기 행렬",
            captionDetail: "초기 반응이 기대치를 웃돌면서 첫날 흥행 가능성에 관심이 쏠리고 있다.",
            meta: ["DAY 1 현장 스케치"],
            imageAlt: "개점 첫날, 팝업 숍 앞에 모여든 방문객들",
          },
        ]
      : [
          {
            title: "유동인구 순위",
            eyebrow: "Foot Traffic Ranking",
            items: mockPopulationRanking,
          },
          {
            title: "지역 매출 순위",
            eyebrow: "Regional Revenue Ranking",
            items: mockRevenueRanking,
          },
        ];

  const apiNewsSidebarSections = useMemo(
    () => buildNewsSidebarSections(day, newsRanking),
    [day, newsRanking],
  );
  void newsSidebarSections;

  useEffect(() => {
    let isActive = true;

    const loadPrepMenus = async () => {
      try {
        const [menusResult, storeResult] = await Promise.allSettled([
          getStoreMenus(),
          day >= 2 ? getStore() : Promise.resolve<StoreResponse | null>(null),
        ]);

        if (!isActive) {
          return;
        }

        const nextStoreMenuName =
          storeResult.status === "fulfilled"
            ? normalizeMenuName(storeResult.value?.menu)
            : "";
        setCurrentStoreMenuName(nextStoreMenuName || null);
        setPlayableday(
          storeResult.status === "fulfilled" && storeResult.value?.playableday != null
            ? storeResult.value.playableday
            : null,
        );

        if (menusResult.status !== "fulfilled") {
          setMenuError("메뉴 정보를 불러오지 못했습니다. 정규 발주 요청은 잠시 후 다시 시도해주세요.");
          return;
        }

        const fetchedMenus = menusResult.value.menus;

        if (fetchedMenus.length === 0) {
          setMenuError("메뉴 정보를 불러오지 못했습니다. 정규 발주 요청은 잠시 후 다시 시도해주세요.");
          return;
        }

        const nextMenus = mapStoreMenusToPrepMenus(fetchedMenus);
        setMenus(nextMenus);
        setSelectedMenu((currentMenuId) =>
          resolveSelectedMenuId(nextMenus, currentMenuId, day, nextStoreMenuName),
        );
        setMenuError(null);
      } catch {
        if (!isActive) {
          return;
        }

        setCurrentStoreMenuName(null);
        setMenuError("메뉴 정보를 불러오지 못했습니다. 정규 발주 요청은 잠시 후 다시 시도해주세요.");
      } finally {
        if (isActive) {
          setIsMenusLoading(false);
        }
      }
    };

    setIsMenusLoading(true);
    void loadPrepMenus();

    return () => {
      isActive = false;
    };
  }, [day]);

  useEffect(() => {
    let isActive = true;

    const loadNews = async () => {
      try {
        const [todayNewsResult, rankingResult] = await Promise.allSettled([
          getTodayNews(day),
          getNewsRanking(day),
        ]);

        if (!isActive) {
          return;
        }

        let nextError: string | null = null;

        if (todayNewsResult.status === "fulfilled") {
          const nextNewsItems = mapTodayNews(todayNewsResult.value.news);
          setNewsItems(nextNewsItems);
          setExpandedNewsId((currentId) =>
            nextNewsItems.some((item) => item.id === currentId)
              ? currentId
              : nextNewsItems[0]?.id ?? null,
          );
        } else {
          setNewsItems([]);
          setExpandedNewsId(null);
          nextError = "뉴스 정보를 일부 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        if (rankingResult.status === "fulfilled") {
          setNewsRanking(rankingResult.value);
        } else {
          setNewsRanking(null);
          nextError = "뉴스 정보를 일부 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        setNewsError(nextError);
      } catch {
        if (!isActive) {
          return;
        }

        setNewsItems([]);
        setNewsRanking(null);
        setExpandedNewsId(null);
        setNewsError("뉴스 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
      } finally {
        if (isActive) {
          setIsNewsLoading(false);
        }
      }
    };

    setIsNewsLoading(true);
    void loadNews();

    return () => {
      isActive = false;
    };
  }, [day]);

  useEffect(() => {
    let isActive = true;

    const loadWaitingStatus = async () => {
      try {
        const nextWaitingStatus = await getGameWaitingStatus();

        if (!isActive) {
          return;
        }

        setWaitingStatus(nextWaitingStatus);
      } catch {
        if (!isActive) {
          return;
        }

        setRegularOrderError("시즌 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
      } finally {
        if (isActive) {
          setIsWaitingStatusLoading(false);
        }
      }
    };

    setIsWaitingStatusLoading(true);
    void loadWaitingStatus();

    return () => {
      isActive = false;
    };
  }, [day]);

  useEffect(() => {
    setPrice(defaultSellingPrice);
  }, [defaultSellingPrice]);

  useEffect(() => {
    setTab("prep");
    setRegularOrderStatus("idle");
    setRegularOrderError(null);
  }, [day]);

  const handleRegularOrderSubmit = async () => {
    if (!canSubmitRegularOrder) {
      return;
    }

    setRegularOrderStatus("submitting");
    setRegularOrderError(null);

    try {
      const latestWaitingStatus = await getGameWaitingStatus();
      setWaitingStatus(latestWaitingStatus);

      if (!isOrderPreparingPhase(latestWaitingStatus, day)) {
        setRegularOrderStatus("idle");
        setRegularOrderError("현재는 영업 준비 시간이 아니어서 정규 발주를 진행할 수 없습니다.");
        return;
      }

      await postRegularOrder({
        menuId: selectedMenuData.id,
        quantity,
        price,
      });
      setRegularOrderStatus("submitted");
    } catch (error) {
      setRegularOrderStatus("idle");

      if (axios.isAxiosError<ApiErrorResponse>(error)) {
        const serverMessage = error.response?.data?.message;
        if (serverMessage) {
          setRegularOrderError(serverMessage);
          return;
        }
      }

      setRegularOrderError("정규 발주에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
  };

  const regularOrderButtonLabel = isSubmittingRegularOrder
    ? "발주 처리중..."
    : hasSubmittedRegularOrder
      ? "발주 완료, 대기중"
      : "발주신청하기";

  return (
    <div className="min-h-screen bg-[#FDFDFB] text-slate-900 font-display flex flex-col">
      <AppHeader />

      {/* Main */}
      <main className="flex-1 flex flex-col items-center py-6 pt-24 px-4 sm:px-8">
        <div className="w-full max-w-[1000px] flex flex-col gap-6">
          {/* Page Header */}
          <div className="flex flex-col gap-5">
            <div className="flex flex-col gap-2.5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2 text-slate-400 text-sm font-medium">
                    <span className="material-symbols-outlined text-[1.25rem]">calendar_today</span>
                    <span>DAY {day}</span>
                  </div>
                  <CountdownTimer
                    key={prepEndTimestampMs ?? `prep-${day}`}
                    endTimestampMs={prepEndTimestampMs ?? guardContext.phaseEndTimestamp}
                    label="준비 시간"
                  />
                </div>
                <h1 className="text-slate-900 text-3xl md:text-[2rem] font-black leading-tight tracking-tight">
                  {tab === "prep" ? "영업 준비" : "버블 뉴스"}
                </h1>
            </div>

            {/* Tabs */}
            <div className="border-b border-slate-100">
              <div className="flex items-center gap-6">
                <button
                  onClick={() => setTab("prep")}
                  className={`pb-2.5 text-[15px] transition-colors ${
                    tab === "prep"
                      ? "border-b-2 border-slate-900 text-slate-900 font-bold"
                      : "text-slate-400 hover:text-slate-600 font-medium"
                  }`}
                >
                  영업 준비
                </button>
                <button
                  onClick={() => setTab("news")}
                  className={`pb-2.5 text-[15px] transition-colors ${
                    tab === "news"
                      ? "border-b-2 border-slate-900 text-slate-900 font-bold"
                      : "text-slate-400 hover:text-slate-600 font-medium"
                  }`}
                >
                  버블 뉴스
                </button>
              </div>
            </div>
          </div>

          {/* Tab: 영업 준비 */}
          {tab === "prep" ? (
            <div className="flex flex-col gap-6">
              {!isRegularOrderRouteDay && (
                <div className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm text-slate-500">
                  <span className="material-symbols-outlined mt-0.5 text-base text-slate-400">
                    event_busy
                  </span>
                  <p className="leading-6">
                    오늘은 정규발주일이 아니어서 영업 준비를 진행할 수 없습니다. 버블 뉴스를 확인해 다음 영업일을 준비하세요.
                  </p>
                </div>
              )}

              {isRegularOrderRouteDay && !isWaitingStatusLoading && !isServerPreparing && (
                <div className="flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-sm text-amber-700">
                  <span className="material-symbols-outlined mt-0.5 text-base text-amber-600">
                    schedule
                  </span>
                  <p className="leading-6">
                    현재 서버 기준으로 영업 준비 단계가 아닙니다. 자동 화면 전환을 기다리거나 시즌 상태를 다시 확인해주세요.
                  </p>
                </div>
              )}

              <div
                className={`flex flex-col gap-6 transition-opacity ${
                  canPrepareToday ? "" : "pointer-events-none select-none opacity-40"
                }`}
                aria-disabled={!canPrepareToday}
              >
                {isRegularOrderRouteDay && (
                  <div className="flex items-start gap-3 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-slate-700">
                    <span className="material-symbols-outlined mt-0.5 text-base text-primary-dark">
                      campaign
                    </span>
                    <p className="leading-6">
                      정규 발주는 반드시 <span className="font-bold">발주신청하기</span> 버튼을 눌러야
                      정상적으로 반영됩니다. 발주 완료 후에는 50초가 지나 자동으로 다음 화면으로 이동합니다.
                    </p>
                  </div>
                )}

                {(isMenusLoading || menuError) && (
                  <div
                    className={`flex items-start gap-3 rounded-2xl px-4 py-3 text-sm ${
                      menuError
                        ? "border border-amber-200 bg-amber-50/80 text-amber-700"
                        : "border border-slate-200 bg-slate-50/80 text-slate-500"
                    }`}
                  >
                    <span className="material-symbols-outlined mt-0.5 text-base">
                      {menuError ? "error" : "hourglass_top"}
                    </span>
                    <p className="leading-6">
                      {menuError ?? "메뉴 정보를 불러오는 중입니다."}
                    </p>
                  </div>
                )}

                {regularOrderError && (
                  <div className="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50/80 px-4 py-3 text-sm text-red-600">
                    <span className="material-symbols-outlined mt-0.5 text-base">error</span>
                    <p className="leading-6">{regularOrderError}</p>
                  </div>
                )}

                {hasSubmittedRegularOrder && (
                  <div className="flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50/80 px-4 py-3 text-sm text-emerald-700">
                    <span className="material-symbols-outlined mt-0.5 text-base">check_circle</span>
                    <p className="leading-6">
                      {selectedMenuData.name} 메뉴 정규 발주가 접수되었습니다. 남은 준비 시간 동안 대기한 뒤
                      자동으로 다음 화면으로 이동합니다.
                    </p>
                  </div>
                )}

                <div
                  className={`flex flex-col gap-6 transition-opacity ${
                    isPrepFormLocked ? "pointer-events-none select-none opacity-80" : ""
                  }`}
                  aria-disabled={isPrepFormLocked}
                >
                  <MenuSelector
                    menus={menuSelectorMenus}
                    selectedId={selectedMenu}
                    onSelect={setSelectedMenu}
                  />

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <PriceSlider
                      menuName={selectedMenuData.name}
                      price={price}
                      min={originalCostPrice}
                      max={maxSellingPrice}
                      step={100}
                      originalCostPrice={originalCostPrice}
                      discountedCostPrice={discountedCostPrice}
                      hasItemDiscount={hasItemDiscount}
                      defaultPrice={defaultSellingPrice}
                      defaultPriceLabel={playableday !== null && day > playableday ? "이전 판매가" : "권장가"}
                      onChange={setPrice}
                    />
                    <div className="flex flex-col gap-5">
                      <QuantityCounter quantity={quantity} min={50} max={500} step={10} onChange={setQuantity} />
                      <div className="flex flex-col gap-3.5">
                        {hasItemDiscount && (
                          <div className="rounded-2xl border border-red-100 bg-red-50/60 px-4 py-3 flex items-center justify-between">
                            <div>
                              <p className="text-sm font-bold text-red-500">선택 아이템 혜택 적용</p>
                              <p className="text-xs text-red-400 mt-1">아이템 사용으로 원재료 비용 {discountRate}% 할인</p>
                            </div>
                            <span className="text-lg font-bold text-red-500">-₩{discountAmount.toLocaleString()}</span>
                          </div>
                        )}
                        <div className="flex items-center justify-between px-4 py-2">
                          <span className="text-sm text-slate-500 font-medium">총 예상 비용</span>
                          <div className="text-right">
                            {hasItemDiscount && (
                              <p className="text-sm font-bold text-red-400 line-through decoration-2">
                                ₩{totalCost.toLocaleString()}
                              </p>
                            )}
                            <span className="text-[1.75rem] font-black text-slate-900 tracking-tight">
                              ₩{discountedTotalCost.toLocaleString()}
                            </span>
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => void handleRegularOrderSubmit()}
                          disabled={!canSubmitRegularOrder}
                          className={`w-full font-bold text-base py-4 px-6 rounded-2xl shadow-lg transition-all flex items-center justify-center gap-2 ${
                            canSubmitRegularOrder
                              ? "bg-primary hover:bg-primary-dark text-slate-900 hover:text-white shadow-primary/20 group"
                              : "bg-slate-200 text-slate-500 shadow-slate-200/70 cursor-not-allowed"
                          }`}
                        >
                          <span>{regularOrderButtonLabel}</span>
                          {!hasSubmittedRegularOrder && (
                            <span
                              className={`material-symbols-outlined transition-transform ${
                                canSubmitRegularOrder ? "group-hover:translate-x-1" : ""
                              }`}
                            >
                              {isSubmittingRegularOrder ? "hourglass_top" : "arrow_forward"}
                            </span>
                          )}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            /* Tab: 버블 뉴스 */
            <div className="flex flex-col gap-4">
              {(isNewsLoading || newsError) && (
                <div
                  className={`flex items-start gap-3 rounded-2xl px-4 py-3 text-sm ${
                    newsError
                      ? "border border-amber-200 bg-amber-50/80 text-amber-700"
                      : "border border-slate-200 bg-slate-50/80 text-slate-500"
                  }`}
                >
                  <span className="material-symbols-outlined mt-0.5 text-base">
                    {newsError ? "error" : "hourglass_top"}
                  </span>
                  <p className="leading-6">
                    {newsError ?? "뉴스 정보를 불러오는 중입니다."}
                  </p>
                </div>
              )}

              {newsItems.length > 0 ? (
                <CozyNewspaper
                  items={newsItems}
                  expandedId={expandedNewsId}
                  onToggle={(id) => setExpandedNewsId(expandedNewsId === id ? null : id)}
                  day={day}
                  rankings={apiNewsSidebarSections}
                />
              ) : (
                !isNewsLoading && (
                  <div className="rounded-2xl border border-slate-200 bg-white/80 px-6 py-10 text-center text-sm text-slate-500 shadow-soft">
                    오늘 표시할 뉴스가 아직 없습니다.
                  </div>
                )
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
