import axios from "axios";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { GameGuardContext } from "../router/GameGuard";
import { getGameWaitingStatus, getSeasonTime, joinCurrentSeason, type GameWaitingResponse } from "../api/game";
import { purchaseItems } from "../api/shop";
import { getLocationList } from "../api/store";
import { getNewsRanking, type AreaRankingItemResponse } from "../api/news";
import CountdownTimer from "../components/common/CountdownTimer";
import DistrictDetailPanel from "../components/game/DistrictDetailPanel";
import SeoulMap3D from "../components/game/SeoulMap3D";
import { seoulDistricts } from "../components/game/seoulDistricts";
import { LOCATION_SELECTION_DEADLINE_STORAGE_KEY } from "../constants";
import { clearStoredBrandName, setStoredBrandName } from "../hooks/useBrandName";
import { useAppNoticeStore } from "../stores/useAppNoticeStore";
import { useGameStore } from "../stores/useGameStore";
import type { WaitingRouteState } from "../types/waiting";
import { clearSeasonJoinIntent } from "../utils/seasonJoinIntent";
import {
  applyDiscount,
  clearStoredSelectedDashboardItems,
  getSelectedDiscountMultiplier,
  getSelectedDiscountPercent,
  getStoredSelectedDashboardItemIds,
  getStoredSelectedDashboardItems,
} from "../utils/dashboardItems";

import {
  BUSINESS_SECONDS,
  DAY_SECONDS,
  REPORT_SECONDS,
} from "../constants/gameTime";

const DEFAULT_PREP_DAY = 1;
const INITIAL_CAPITAL = 5_000_000;
const MIDSEASON_CUTOFF_DAY = 6;
const BRAND_NAME_MAX_LENGTH = 10;
const PURCHASE_FAILURE_NOTICE =
  "매장은 생성되었지만 아이템 구매에 실패했습니다. 포인트는 차감되지 않았습니다.";

type SelectionMode = "opening_window" | "midseason";

interface SelectionWindowState {
  mode: SelectionMode;
  endTimestampMs: number;
  cutoffTimestampMs?: number;
  timerLabel: string;
  helperText: string;
}

function parseCurrency(value: string) {
  return Number(value.replace(/[^\d]/g, ""));
}

function formatCurrency(value: number) {
  return `₩${value.toLocaleString("ko-KR")}`;
}



function clearLocationSelectionDeadline() {
  try {
    sessionStorage.removeItem(LOCATION_SELECTION_DEADLINE_STORAGE_KEY);
  } catch {
    // Ignore storage access failures and continue navigation.
  }

  clearSeasonJoinIntent();
}

function isLocationSelectionAvailable(waitingStatus: GameWaitingResponse) {
  return (
    waitingStatus.status === "IN_PROGRESS" &&
    typeof waitingStatus.currentDay === "number" &&
    waitingStatus.currentDay >= 1 &&
    waitingStatus.currentDay <= 5
  );
}

function getSecondsUntilDayStart(waitingStatus: GameWaitingResponse, targetDay: number) {
  const currentDay = waitingStatus.currentDay;
  const remaining = Math.max(0, waitingStatus.phaseRemainingSeconds ?? 0);

  if (typeof currentDay !== "number") {
    return remaining;
  }

  if (targetDay <= currentDay) {
    return 0;
  }

  const remainingFullDays = Math.max(0, targetDay - currentDay - 1);

  switch (waitingStatus.seasonPhase) {
    case "LOCATION_SELECTION":
      return remaining + Math.max(0, targetDay - 1) * DAY_SECONDS;
    case "DAY_PREPARING":
      return remaining + BUSINESS_SECONDS + REPORT_SECONDS + remainingFullDays * DAY_SECONDS;
    case "DAY_BUSINESS":
      return remaining + REPORT_SECONDS + remainingFullDays * DAY_SECONDS;
    case "DAY_REPORT":
      return remaining + remainingFullDays * DAY_SECONDS;
    default:
      return remaining;
  }
}

function buildSelectionWindow(
  waitingStatus: GameWaitingResponse,
  locationSelectionDeadlineMs: number,
): SelectionWindowState | null {
  if (!isLocationSelectionAvailable(waitingStatus)) {
    return null;
  }

  const currentDay = waitingStatus.currentDay ?? 1;
  const nextPrepDay = waitingStatus.seasonPhase === "LOCATION_SELECTION" ? 1 : currentDay + 1;

  const baseWindow = {
    endTimestampMs: locationSelectionDeadlineMs,
    timerLabel: `${nextPrepDay}일차 영업 준비까지`,
    helperText: "영업 준비가 시작되기 전에 지역을 고르고 팝업 브랜드명을 입력해주세요.",
  };

  if (waitingStatus.seasonPhase === "LOCATION_SELECTION") {
    return {
      mode: "opening_window",
      ...baseWindow,
    };
  }

  // midseason: 다음 영업 시작(prep)까지의 시간으로 타이머 설정
  const secondsUntilNextPrep = getSecondsUntilDayStart(waitingStatus, nextPrepDay);
  return {
    mode: "midseason",
    ...baseWindow,
    endTimestampMs: Date.now() + secondsUntilNextPrep * 1000,
    cutoffTimestampMs:
      Date.now() + getSecondsUntilDayStart(waitingStatus, MIDSEASON_CUTOFF_DAY) * 1000,
  };
}

function resolveJoinErrorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) {
    return "시즌 참여 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
  }

  const responseMessage = error.response?.data?.message;

  if (typeof responseMessage === "string" && responseMessage.trim()) {
    return responseMessage;
  }

  if (error.response?.status === 409) {
    return "이미 이번 시즌에 참여했습니다.";
  }

  return "시즌 참여 요청을 완료하지 못했습니다. 입력한 정보와 시즌 상태를 다시 확인해주세요.";
}

/** 유동인구 순위 → 등급 변환 (1~2위: S, 3~5위: A, 6~8위: B) */
function rankToGrade(rank: number): string {
  if (rank <= 2) return "S등급";
  if (rank <= 5) return "A등급";
  return "B등급";
}

/** seoulDistricts의 name과 서버 locationName을 매칭하는 맵 */
const LOCATION_NAME_MAP: Record<string, string> = {
  "홍대": "홍대",
  "여의도": "여의도",
  "명동": "명동",
  "이태원": "이태원",
  "서울숲/성수": "서울숲/성수",
  "신도림": "신도림",
  "강남": "강남",
  "잠실": "잠실",
};

export default function LocationSelectPage() {
  const guardContext = useOutletContext<GameGuardContext>();
  const [locationSelectionDeadlineMs, setLocationSelectionDeadlineMs] = useState(() => guardContext.phaseEndTimestamp);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectionWindow, setSelectionWindow] = useState<SelectionWindowState | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [isAccessChecking, setIsAccessChecking] = useState(true);
  const [joinError, setJoinError] = useState<string | null>(null);
  const [selectedDashboardItems] = useState(getStoredSelectedDashboardItems);
  const [serverLocations, setServerLocations] = useState<Array<{locationId: number; locationName: string; rent: number; interiorCost: number; discount: number}>>([]);
  const [trafficRanking, setTrafficRanking] = useState<AreaRankingItemResponse[]>([]);
  const navigate = useNavigate();
  const showFlashNotice = useAppNoticeStore((state) => state.showFlashNotice);
  const clearFlashNotice = useAppNoticeStore((state) => state.clearFlashNotice);

  // --- 서버 시간 동기화 ---
  const resyncDeadline = useCallback(async () => {
    try {
      const timeData = await getSeasonTime();
      if (timeData.seasonPhase !== "LOCATION_SELECTION") return;
      const correctedEnd = Date.now() + timeData.phaseRemainingSeconds * 1000;
      const drift = Math.abs(correctedEnd - locationSelectionDeadlineMs);
      if (drift > 1000) {
        setLocationSelectionDeadlineMs(correctedEnd);
      }
    } catch { /* 무시 */ }
  }, [locationSelectionDeadlineMs]);

  // 3.1 화면 진입 시 sync
  useEffect(() => { resyncDeadline(); }, []);

  // 3.5 탭 복귀 시 sync
  useEffect(() => {
    const handler = () => {
      if (document.visibilityState === "visible") resyncDeadline();
    };
    document.addEventListener("visibilitychange", handler);
    return () => document.removeEventListener("visibilitychange", handler);
  }, [resyncDeadline]);
  // --- 서버 시간 동기화 끝 ---

  // 서버 지역 데이터 + 유동인구 순위 로드
  useEffect(() => {
    getLocationList()
      .then((data) => setServerLocations(data.locations))
      .catch(() => {});
    getNewsRanking(guardContext.day)
      .then((data) => setTrafficRanking(data.areaTrafficRanking))
      .catch(() => {});
  }, [guardContext.day]);

  // seoulDistricts에 서버 rent + 유동인구 순위 기반 등급/혼잡도 반영
  const mergedDistricts = useMemo(() => {
    if (serverLocations.length === 0) return seoulDistricts;
    return seoulDistricts.map((district) => {
      const serverName = LOCATION_NAME_MAP[district.name] ?? district.name;
      const serverLoc = serverLocations.find((s) => s.locationName === serverName);
      const rankItem = trafficRanking.find((r) => r.areaName === serverName);
      const merged = { ...district };
      if (serverLoc) {
        merged.rent = `₩${serverLoc.rent.toLocaleString()}`;
      }
      if (rankItem) {
        merged.grade = rankToGrade(rankItem.rank);
        merged.congestion = `${rankItem.rank}위`;
      }
      return merged;
    });
  }, [serverLocations, trafficRanking]);

  // FE district name → 서버 locationId 변환 (서버 데이터 없으면 FE id fallback)
  const getServerLocationId = (feDistrict: { id: number; name: string }): number => {
    if (serverLocations.length === 0) return feDistrict.id;
    const serverName = LOCATION_NAME_MAP[feDistrict.name] ?? feDistrict.name;
    const serverLoc = serverLocations.find((s) => s.locationName === serverName);
    return serverLoc?.locationId ?? feDistrict.id;
  };

  const selectedDistrict = mergedDistricts.find((district) => district.id === selectedId);
  const selectedInteriorCost = useMemo(() => {
    if (!selectedDistrict) return null;
    const serverName = LOCATION_NAME_MAP[selectedDistrict.name] ?? selectedDistrict.name;
    const serverLoc = serverLocations.find((s) => s.locationName === serverName);
    if (serverLoc) return serverLoc.interiorCost;
    return Math.round(parseCurrency(selectedDistrict.rent) * 7 * 0.1);
  }, [selectedDistrict, serverLocations]);
  const rentDiscountMultiplier = useMemo(
    () => getSelectedDiscountMultiplier(selectedDashboardItems, "RENT"),
    [selectedDashboardItems],
  );
  const rentDiscountPercent = useMemo(
    () => getSelectedDiscountPercent(selectedDashboardItems, "RENT"),
    [selectedDashboardItems],
  );
  const discountedRent = selectedDistrict
    ? applyDiscount(parseCurrency(selectedDistrict.rent), rentDiscountMultiplier)
    : null;

  useEffect(() => {
    let isCancelled = false;

    async function verifySeasonAccess() {
      try {
        const waitingStatus = await getGameWaitingStatus();

        if (isCancelled) {
          return;
        }

        const nextSelectionWindow = buildSelectionWindow(
          waitingStatus,
          locationSelectionDeadlineMs,
        );

        if (!nextSelectionWindow) {
          clearLocationSelectionDeadline();
          navigate("/", { replace: true });
          return;
        }

        setSelectionWindow(nextSelectionWindow);
        setIsAccessChecking(false);
      } catch {
        if (!isCancelled) {
          clearLocationSelectionDeadline();
          navigate("/", { replace: true });
        }
      }
    }

    void verifySeasonAccess();

    return () => {
      isCancelled = true;
    };
  }, [locationSelectionDeadlineMs, navigate]);

  useEffect(() => {
    if (selectionWindow?.mode !== "midseason" || !selectionWindow.cutoffTimestampMs) {
      return;
    }

    const remainingMs = selectionWindow.cutoffTimestampMs - Date.now();

    if (remainingMs <= 0) {
      clearLocationSelectionDeadline();
      clearStoredBrandName();
      navigate("/", {
        replace: true,
        state: { showMidSeasonSetupExpiredModal: true },
      });
      return;
    }

    const timer = window.setTimeout(() => {
      clearLocationSelectionDeadline();
      clearStoredBrandName();
      navigate("/", {
        replace: true,
        state: { showMidSeasonSetupExpiredModal: true },
      });
    }, remainingMs);

    return () => {
      window.clearTimeout(timer);
    };
  }, [navigate, selectionWindow]);

  const handleComplete = async (brandName: string) => {
    if (!selectedDistrict || isJoining || !selectionWindow) {
      return;
    }

    const normalizedBrandName = brandName.trim().slice(0, BRAND_NAME_MAX_LENGTH);

    if (!normalizedBrandName) {
      return;
    }

    setJoinError(null);
    setIsJoining(true);
    clearFlashNotice();
    setStoredBrandName(normalizedBrandName);

    try {
      const serverLocationId = getServerLocationId(selectedDistrict);

      const joinResponse = await joinCurrentSeason({
        locationId: serverLocationId,
        storeName: normalizedBrandName,
      });
      // join 응답의 playableFromDay를 store에 저장 (GameGuard에서 참조)
      useGameStore.getState().setPlayableFromDay(joinResponse.playableFromDay);
      useGameStore.getState().setCurrentLocationName(selectedDistrict.name);
      const selectedItemIds = getStoredSelectedDashboardItemIds();
      if (selectedItemIds.length > 0) {
        try {
          await purchaseItems({ itemId: selectedItemIds });
          clearStoredSelectedDashboardItems();
          clearFlashNotice();
        } catch {
          showFlashNotice(PURCHASE_FAILURE_NOTICE);
        }
      }
      const nextPrepPath = `/game/${joinResponse.playableFromDay ?? DEFAULT_PREP_DAY}/prep`;
      const remainingSelectionSeconds = Math.max(
        0,
        Math.ceil((selectionWindow.endTimestampMs - Date.now()) / 1000),
      );

      // playableFromDay가 현재 day보다 크면 대기 필요
      const needsWaiting = joinResponse.playableFromDay > guardContext.day;

      if (needsWaiting) {
        clearLocationSelectionDeadline();

        // 다음 영업준비까지 남은 시간 계산
        const waitingState: WaitingRouteState = {
          mode: "next_business_day",
          brandName: normalizedBrandName,
          districtName: selectedDistrict.name,
          nextPath: nextPrepPath,
          targetDay: joinResponse.playableFromDay,
          endTimestampMs: selectionWindow.endTimestampMs,
        };

        navigate("/game/waiting", { state: waitingState });
        return;
      }

      if (selectionWindow.mode === "opening_window" && remainingSelectionSeconds > 0) {
        const waitingState: WaitingRouteState = {
          mode: "prep_locked",
          brandName: normalizedBrandName,
          districtName: selectedDistrict.name,
          endTimestampMs: selectionWindow.endTimestampMs,
          nextPath: nextPrepPath,
          targetDay: joinResponse.playableFromDay,
        };

        navigate("/game/waiting", { state: waitingState });
        return;
      }

      clearLocationSelectionDeadline();
      navigate(nextPrepPath);
    } catch (error) {
      setJoinError(resolveJoinErrorMessage(error));
    } finally {
      setIsJoining(false);
    }
  };

  const handleTimerComplete = () => {
    if (isJoining || !selectionWindow) {
      return;
    }

    clearLocationSelectionDeadline();
    // 시간 끝나면 새로고침 → GameGuard가 새 페이즈에 맞게 리다이렉트
    // 6~7일차면 joinEnabled=false → GameGuard가 홈으로 보냄
    navigate(0);
  };

  if (isAccessChecking || !selectionWindow) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#FDFDFB] font-display text-slate-400">
        참여 가능 상태를 확인하는 중입니다.
      </div>
    );
  }

  return (
    <div className="relative h-screen w-full overflow-hidden bg-[#FDFDFB] font-display text-slate-800">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(circle at top left, rgba(168,191,169,0.18), transparent 34%), radial-gradient(circle at bottom right, rgba(212,165,165,0.14), transparent 30%)",
        }}
      />
      <div className="absolute left-[-8%] top-[8%] size-56 rounded-full bg-primary/10 blur-3xl" />
      <div className="absolute bottom-[10%] right-[-6%] size-64 rounded-full bg-accent-rose/10 blur-3xl" />

      <div className="pointer-events-none absolute left-0 top-0 z-[60] w-full p-4 sm:p-6">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-16 items-center gap-3 rounded-[22px] border border-white/70 bg-white/90 px-6 shadow-premium backdrop-blur">
              <div className="flex size-10 items-center justify-center rounded-2xl bg-primary/15 text-primary-dark">
                <span className="material-symbols-outlined text-2xl">location_on</span>
              </div>
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">
                  Step 1
                </p>
                <p className="text-base font-bold leading-tight text-slate-800">지역 선택</p>
              </div>
            </div>
            <p className="hidden text-sm text-slate-500 sm:block">{selectionWindow.helperText}</p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-stretch">
            <div className="flex min-h-16 items-center gap-3 rounded-[22px] border border-white/70 bg-white/90 px-5 py-3 shadow-premium backdrop-blur">
              <div className="flex size-10 items-center justify-center rounded-2xl bg-amber-100 text-amber-600">
                <span className="material-symbols-outlined text-xl">account_balance_wallet</span>
              </div>
              <div className="flex flex-col leading-tight">
                <span className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">
                  초기 자본
                </span>
                <span className="font-mono text-lg font-bold text-primary-dark">
                  {formatCurrency(INITIAL_CAPITAL)}
                </span>
              </div>
            </div>

            <div className="flex min-h-16 min-w-[196px] flex-col justify-center rounded-[22px] border border-white/70 bg-white/90 px-5 py-3 shadow-premium backdrop-blur">
              <span className="text-[10px] font-bold uppercase tracking-[0.24em] text-slate-400">
                {selectionWindow.timerLabel}
              </span>
              <div className="mt-1">
                <CountdownTimer
                  endTimestampMs={selectionWindow.endTimestampMs}
                  label={selectionWindow.timerLabel}
                  onComplete={handleTimerComplete}
                  variant="inline"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <SeoulMap3D districts={mergedDistricts} selectedId={selectedId} onSelect={setSelectedId} />

      {selectedDistrict && (
        <DistrictDetailPanel
          district={selectedDistrict}
          interiorCost={selectedInteriorCost !== null ? formatCurrency(selectedInteriorCost) : null}
          discountedRent={discountedRent !== null ? formatCurrency(discountedRent) : null}
          rentDiscountLabel={
            rentDiscountPercent > 0 ? `아이템 적용으로 ${rentDiscountPercent}% 할인` : null
          }
          isSubmitting={isJoining}
          submitError={joinError}
          onComplete={handleComplete}
          onClose={() => {
            if (!isJoining) {
              setJoinError(null);
              setSelectedId(null);
            }
          }}
        />
      )}
    </div>
  );
}
