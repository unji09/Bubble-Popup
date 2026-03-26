import { useCallback, useEffect, useRef, useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  getCurrentParticipation,
  getSeasonTime,
  type CurrentSeasonTimeResponse,
  type ParticipationResponse,
} from "../api/game";
import { getStore, type StoreResponse } from "../api/store";
import { phaseToRoute, type SeasonPhase } from "../constants/gameTime";
import { setStoredBrandName } from "../hooks/useBrandName";
import { useGameStore } from "../stores/useGameStore";
import type { WaitingRouteState } from "../types/waiting";
import { clearSeasonJoinIntent, hasSeasonJoinIntent } from "../utils/seasonJoinIntent";

/** GameGuard가 하위 페이지에 전달하는 context */
export interface GameGuardContext {
  phase: SeasonPhase;
  day: number;
  phaseRemainingSeconds: number;
  phaseEndTimestamp: number;
}

interface RedirectTarget {
  path: string;
  state?: WaitingRouteState;
}

async function resolveJoinedStoreAccess(day: number | null) {
  try {
    const participation = await getCurrentParticipation();

    if (!participation.joinedCurrentSeason) {
      return { joined: false, storeData: null as StoreResponse | null };
    }

    const fallbackStoreData = buildFallbackStoreData(participation, day);

    // 서버 브랜드명으로 localStorage 동기화 (다른 브라우저에서 변경된 경우 대비)
    if (participation.storeName) {
      setStoredBrandName(participation.storeName);
    }

    if (!participation.storeAccessible) {
      return { joined: true, storeData: fallbackStoreData };
    }

    try {
      const storeData = await getStore();
      if (storeData.popupName) {
        setStoredBrandName(storeData.popupName);
      }
      return {
        joined: true,
        storeData: {
          ...fallbackStoreData,
          ...storeData,
          playableFromDay: storeData.playableFromDay ?? fallbackStoreData.playableFromDay,
        },
      };
    } catch {
      return { joined: true, storeData: fallbackStoreData };
    }
  } catch {
    return { joined: false, storeData: null as StoreResponse | null };
  }
}

function buildFallbackStoreData(
  participation: ParticipationResponse,
  day: number | null,
): StoreResponse {
  return {
    location: "-",
    popupName: participation.storeName ?? "-",
    menu: "",
    day: typeof day === "number" ? day : 1,
    playableday: participation.playableFromDay ?? 1,
    playableFromDay: participation.playableFromDay ?? undefined,
  };
}

/** 참여 완료 유저의 경로 허용 판정 */
function isAllowedForJoinedUser(
  phase: SeasonPhase,
  day: number | null,
  pathname: string,
  waitingForPlayableDay: boolean,
): boolean {
  if (waitingForPlayableDay) {
    return pathname === "/game/waiting";
  }
  if (phase === "LOCATION_SELECTION") {
    // 이미 참여한 유저 → waiting + prep 허용 (waiting 타이머 끝나면 prep으로 이동)
    return pathname === "/game/waiting" || pathname === `/game/${day}/prep`;
  }
  if (phase === "DAY_PREPARING" && pathname === "/game/waiting") {
    return true;
  }
  if (phase === "SEASON_SUMMARY" || phase === "NEXT_SEASON_WAITING") {
    return pathname === "/ranking";
  }
  const expected = phaseToRoute(phase, day);
  return expected !== null && pathname === expected;
}

/** 미참여 유저의 경로 허용 판정 */
function isAllowedForNewUser(phase: SeasonPhase, pathname: string): boolean {
  // 시즌 종료 후에는 누구나 랭킹 조회 가능
  if ((phase === "SEASON_SUMMARY" || phase === "NEXT_SEASON_WAITING") && pathname === "/ranking") {
    return true;
  }
  return pathname.startsWith("/game/setup") || pathname === "/game/waiting";
}

/** waiting 페이지로 보낼 때 route state 생성 */
function buildWaitingState(
  timeData: CurrentSeasonTimeResponse,
  storeData: StoreResponse | null,
  waitingForPlayableDay: boolean,
): WaitingRouteState {
  const brandName = storeData?.popupName ?? "-";
  const districtName = storeData?.location ?? "-";

  if (waitingForPlayableDay) {
    const targetDay = timeData.joinPlayableFromDay ?? (timeData.currentDay + 1);
    return {
      mode: "next_business_day",
      brandName,
      districtName,
      nextPath: `/game/${targetDay}/prep`,
      endTimestampMs: Date.now() + timeData.phaseRemainingSeconds * 1000,
      targetDay,
    };
  }

  // LOCATION_SELECTION 중 이미 참여한 유저 → 오픈 대기
  return {
    mode: "prep_locked",
    brandName,
    districtName,
    nextPath: `/game/${timeData.currentDay}/prep`,
    endTimestampMs: Date.now() + timeData.phaseRemainingSeconds * 1000,
  };
}

/** 참여 완료 유저의 리다이렉트 대상 */
function resolveJoinedUserTarget(
  phase: SeasonPhase,
  day: number | null,
  waitingForPlayableDay: boolean,
  timeData: CurrentSeasonTimeResponse,
  storeData: StoreResponse | null,
): RedirectTarget {
  if (waitingForPlayableDay || phase === "LOCATION_SELECTION") {
    return {
      path: "/game/waiting",
      state: buildWaitingState(timeData, storeData, waitingForPlayableDay),
    };
  }
  return { path: phaseToRoute(phase, day) ?? "/" };
}

/** 미참여 유저의 리다이렉트 대상 */
function resolveNewUserTarget(canEnterSetup: boolean): RedirectTarget {
  return { path: canEnterSetup ? "/game/setup/location" : "/" };
}

type GuardState =
  | { status: "loading" }
  | { status: "redirect"; target: RedirectTarget }
  | { status: "allowed"; context: GameGuardContext };

export default function GameGuard() {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState<GuardState>({ status: "loading" });
  const timerRef = useRef<ReturnType<typeof setTimeout>>(null);

  const checkAndRoute = useCallback(async () => {
    try {
      let timeData = await getSeasonTime();

      // 남은 시간이 0이면 서버가 아직 페이즈 전환 안 한 것 → 1초 후 재시도
      if (timeData.phaseRemainingSeconds <= 0) {
        await new Promise((r) => setTimeout(r, 1000));
        timeData = await getSeasonTime();
      }

      const phase = timeData.seasonPhase as SeasonPhase;
      const day = timeData.currentDay;
      const remaining = timeData.phaseRemainingSeconds;
      const joinEnabled = timeData.joinEnabled;
      const joinIntent = hasSeasonJoinIntent();

      // 참여 여부 확인
      let { joined, storeData } = await resolveJoinedStoreAccess(day);

      // getStore 실패 시 sessionStorage의 playableFromDay를 fallback으로 사용
      // (BE가 파산 후 재참여 시 store를 바로 안 만드는 경우 대비)
      const cachedPlayableFromDay = useGameStore.getState().playableFromDay;
      if (!joined && cachedPlayableFromDay != null && day < cachedPlayableFromDay) {
        joined = true;
      }

      const playableFromDay = storeData?.playableFromDay ?? cachedPlayableFromDay;
      const waitingForPlayableDay = joined
        && playableFromDay != null
        && day < playableFromDay;

      // playableFromDay를 지났으면 캐시 클리어 (다음 시즌에 영향 방지)
      if (cachedPlayableFromDay != null && day >= cachedPlayableFromDay) {
        useGameStore.getState().clearGame();
      }

      if (joined || !joinEnabled) {
        clearSeasonJoinIntent();
      }


      const pathname = location.pathname;
      let allowed = false;
      let target: RedirectTarget = { path: "/" };

      if (joined) {
        allowed = isAllowedForJoinedUser(phase, day, pathname, waitingForPlayableDay);
        target = resolveJoinedUserTarget(phase, day, waitingForPlayableDay, timeData, storeData);
      } else {
        allowed = isAllowedForNewUser(phase, pathname);
        if (!allowed) {
          const canEnterSetup = Boolean(joinEnabled && joinIntent);
          if (canEnterSetup) {
            target = resolveNewUserTarget(true);
          } else {
            target = { path: "/" };
          }
        }
      }

      if (allowed) {
        // 대기 유저가 /game/waiting에 route state 없이 도달한 경우 (새로고침, 직접 이동 등)
        // → state를 주입해서 WaitingPage가 정상 렌더링되도록
        if (waitingForPlayableDay && pathname === "/game/waiting" && !location.state) {
          const waitingState = buildWaitingState(timeData, storeData, true);
          setState({
            status: "redirect",
            target: { path: "/game/waiting", state: waitingState },
          });
          return { allowed: false, remaining: 0 };
        }

        setState({
          status: "allowed",
          context: {
            phase,
            day,
            phaseRemainingSeconds: remaining,
            phaseEndTimestamp: Date.now() + remaining * 1000,
          },
        });
        return { allowed: true, remaining };
      }

      setState({ status: "redirect", target });
      return { allowed: false, remaining: 0 };
    } catch {
      setState({ status: "redirect", target: { path: "/" } });
      return { allowed: false, remaining: 0 };
    }
  }, [location.pathname]);

  // 자동 전환 스케줄러
  const scheduleTransition = useCallback((remainingSeconds: number) => {
    if (timerRef.current) clearTimeout(timerRef.current);

    const delayMs = (remainingSeconds + 1) * 1000;

    timerRef.current = setTimeout(async () => {
      try {
        const timeData = await getSeasonTime();
        const phase = timeData.seasonPhase as SeasonPhase;
        const day = timeData.currentDay;

        const { joined, storeData } = await resolveJoinedStoreAccess(day);
        const canEnterSetup = Boolean(timeData.joinEnabled && hasSeasonJoinIntent());

        const pfd = storeData?.playableFromDay ?? null;
        const waiting = joined && pfd != null && day < pfd;

        let target: RedirectTarget;
        if (joined) {
          target = resolveJoinedUserTarget(phase, day, waiting, timeData, storeData);
        } else {
          target = resolveNewUserTarget(canEnterSetup);
        }

        const stillAllowed = joined
          ? isAllowedForJoinedUser(phase, day, location.pathname, waiting)
          : isAllowedForNewUser(phase, location.pathname);

        if (!stillAllowed) {
          navigate(target.path, { replace: true, state: target.state });
        } else {
          scheduleTransition(timeData.phaseRemainingSeconds);
        }
      } catch {
        navigate("/", { replace: true });
      }
    }, delayMs);
  }, [location.pathname, navigate]);

  // 페이지 진입 시 체크 + 타이머 설정
  useEffect(() => {
    let cancelled = false;

    setState({ status: "loading" });

    checkAndRoute().then((result) => {
      if (cancelled) return;
      // waiting 페이지는 자체 타이머로 전환 처리 → GameGuard 타이머 스킵
      const isWaiting = location.pathname === "/game/waiting";
      if (result.allowed && result.remaining > 0 && !isWaiting) {
        scheduleTransition(result.remaining);
      }
    });

    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [checkAndRoute, scheduleTransition]);

  if (state.status === "loading") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#FDFDFB] text-slate-900 font-display">
        <p className="text-lg font-semibold">게임 상태 확인 중...</p>
      </div>
    );
  }

  if (state.status === "redirect") {
    return <Navigate to={state.target.path} state={state.target.state} replace />;
  }

  return <Outlet context={state.context} />;
}
