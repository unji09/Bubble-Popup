import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getGameWaitingStatus, type GameWaitingResponse } from "../api/game";
import CountdownTimer from "../components/common/CountdownTimer";
import { LOCATION_SELECTION_DEADLINE_STORAGE_KEY } from "../constants";
import type { WaitingRouteState } from "../types/waiting";

function isWaitingRouteState(value: unknown): value is WaitingRouteState {
  if (!value || typeof value !== "object") {
    return false;
  }

  const state = value as Partial<WaitingRouteState>;

  if (state.mode === "season_starting") {
    return true;
  }

  return (
    (state.mode === "prep_locked" || state.mode === "next_business_day") &&
    typeof state.brandName === "string" &&
    typeof state.districtName === "string" &&
    typeof state.nextPath === "string"
  );
}

function buildSeasonStartingState(
  waitingStatus: GameWaitingResponse | null,
): WaitingRouteState | null {
  if (
    waitingStatus?.status !== "WAITING" ||
    (waitingStatus.phaseRemainingSeconds ?? 0) > 0
  ) {
    return null;
  }

  return {
    mode: "season_starting",
    seasonNumber: waitingStatus.nextSeasonNumber,
    nextPath: "/game/setup/location",
  };
}

export default function WaitingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = isWaitingRouteState(location.state) ? location.state : null;
  const [waitingStatus, setWaitingStatus] = useState<GameWaitingResponse | null>(null);
  const [isStartingSyncing, setIsStartingSyncing] = useState(false);
  const inferredSeasonStartingState = useMemo(
    () => buildSeasonStartingState(waitingStatus),
    [waitingStatus],
  );
  const effectiveState = routeState ?? inferredSeasonStartingState;

  useEffect(() => {
    if (routeState) {
      return;
    }

    let cancelled = false;

    const loadWaitingStatus = async () => {
      try {
        const nextWaitingStatus = await getGameWaitingStatus();

        if (!cancelled) {
          setWaitingStatus(nextWaitingStatus);
        }
      } catch {
        // Keep the fallback empty state when waiting status cannot be loaded.
      }
    };

    void loadWaitingStatus();

    return () => {
      cancelled = true;
    };
  }, [routeState]);

  useEffect(() => {
    if (!effectiveState || effectiveState.mode !== "season_starting") {
      return;
    }

    let cancelled = false;
    let timerId: number | null = null;

    const pollSeasonStart = async () => {
      try {
        const nextWaitingStatus = await getGameWaitingStatus();

        if (cancelled) {
          return;
        }

        setWaitingStatus(nextWaitingStatus);

        if (nextWaitingStatus.status === "IN_PROGRESS") {
          navigate(effectiveState.nextPath ?? "/game/setup/location", { replace: true });
          return;
        }

        setIsStartingSyncing(true);
        timerId = window.setTimeout(pollSeasonStart, 1000);
      } catch {
        if (cancelled) {
          return;
        }

        setIsStartingSyncing(true);
        timerId = window.setTimeout(pollSeasonStart, 1000);
      }
    };

    void pollSeasonStart();

    return () => {
      cancelled = true;
      if (timerId !== null) {
        window.clearTimeout(timerId);
      }
    };
  }, [effectiveState, navigate]);

  const content = useMemo(() => {
    if (!effectiveState) {
      return {
        eyebrow: "Waiting State",
        title: "대기 정보를 찾을 수 없습니다.",
        description: "다시 진입하거나 이전 화면에서 게임 흐름을 다시 시작해 주세요.",
        statusLabel: "대기 정보 없음",
        badgeTone: "border-slate-200 bg-white/80 text-slate-600",
        iconTone: "bg-slate-900 text-white",
        ringTone: "border-slate-200",
      };
    }

    if (effectiveState.mode === "season_starting") {
      const seasonNumber = effectiveState.seasonNumber ?? waitingStatus?.nextSeasonNumber ?? null;

      return {
        eyebrow: "Season Starting",
        title:
          typeof seasonNumber === "number"
            ? `${seasonNumber}번째 시즌 시작 준비 중입니다.`
            : "시즌 시작 준비 중입니다.",
        description:
          "예정된 시작 시각은 지났고 서버가 마지막 시작 처리를 마무리하는 중입니다. 준비가 끝나면 자동으로 입지선정 화면으로 이동합니다.",
        statusLabel: isStartingSyncing ? "시작 상태 확인 중" : "시작 준비 중",
        badgeTone: "border-primary/20 bg-primary/12 text-primary-dark",
        iconTone: "bg-primary-dark text-white",
        ringTone: "border-primary/20",
      };
    }

    if (effectiveState.mode === "next_business_day") {
      return {
        eyebrow: "Mid-season Join",
        title: `DAY ${effectiveState.targetDay ?? 2} 영업 입장 대기 중`,
        description: `${effectiveState.districtName}에서 "${effectiveState.brandName}" 영업 준비를 마쳤습니다. 다음 영업일이 시작되면 자동으로 입장합니다.`,
        statusLabel: effectiveState.endTimestampMs
          ? `DAY ${effectiveState.targetDay ?? 2} 영업 준비까지`
          : "다음 영업 입장 대기",
        badgeTone: "border-amber-200 bg-amber-50/90 text-amber-700",
        iconTone: "bg-amber-500 text-white",
        ringTone: "border-amber-200/80",
      };
    }

    return {
      eyebrow: "Opening Window",
      title: "오픈 대기 중",
      description: `${effectiveState.districtName}에서 "${effectiveState.brandName}" 매장이 등록됐습니다.\n설정 시간이 끝나면 영업 준비 화면으로 자동 이동합니다.`,
      statusLabel: "설정 시간 종료까지",
      badgeTone: "border-primary/20 bg-primary/12 text-primary-dark",
      iconTone: "bg-primary-dark text-white",
      ringTone: "border-primary/20",
    };
  }, [effectiveState, isStartingSyncing, waitingStatus?.nextSeasonNumber]);

  const handleComplete = () => {
    if (effectiveState?.mode === "season_starting" || !effectiveState?.nextPath) {
      return;
    }

    try {
      sessionStorage.removeItem(LOCATION_SELECTION_DEADLINE_STORAGE_KEY);
    } catch {
      // Ignore storage access failures and continue navigation.
    }

    navigate(effectiveState.nextPath, { replace: true });
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#FDFDFB] px-4 py-8 font-display text-slate-900 sm:px-6">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(circle at 18% 20%, rgba(168,191,169,0.22), transparent 34%), radial-gradient(circle at 82% 18%, rgba(212,165,165,0.18), transparent 28%), radial-gradient(circle at 50% 100%, rgba(248,250,252,0.95), rgba(253,253,251,0.78))",
        }}
      />
      <div className="absolute left-[-4rem] top-[15%] size-40 rounded-full bg-primary/10 blur-3xl" />
      <div className="absolute right-[-5rem] top-[10%] size-52 rounded-full bg-accent-rose/10 blur-3xl" />
      <div className="absolute bottom-[-6rem] left-1/2 size-72 -translate-x-1/2 rounded-full bg-primary/10 blur-3xl" />

      <div className="relative z-10 w-full max-w-3xl">
        <div className="rounded-[2rem] border border-white/70 bg-white/88 p-6 shadow-premium backdrop-blur sm:p-8">
          <div className="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-xl">
              <div
                className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-[11px] font-bold uppercase tracking-[0.24em] ${content.badgeTone}`}
              >
                <span className="material-symbols-outlined text-[14px]">schedule</span>
                <span>{content.eyebrow}</span>
              </div>
              <h1 className="mt-5 text-3xl font-black tracking-tight text-slate-900 sm:text-[2.35rem]">
                {content.title}
              </h1>
              <p className="mt-3 max-w-lg whitespace-pre-line text-sm leading-7 text-slate-500 sm:text-[15px]">
                {content.description}
              </p>

              {effectiveState && effectiveState.mode !== "season_starting" && (
                <div className="mt-6 grid max-w-md grid-cols-1 items-stretch gap-3 sm:grid-cols-2">
                  <div className="h-full rounded-2xl border border-slate-200 bg-slate-50/85 px-4 py-3 text-center">
                    <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-400">
                      매장 브랜드명
                    </p>
                    <p className="mt-1 text-base font-bold text-slate-900">
                      {"brandName" in effectiveState ? effectiveState.brandName : "-"}
                    </p>
                  </div>
                  <div className="flex h-full flex-col rounded-2xl border border-slate-200 bg-slate-50/85 px-4 py-3 text-center">
                    <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-400">
                      입점 지역
                    </p>
                    <div className="flex flex-1 items-center justify-center">
                      <p className="text-base font-bold text-slate-900">
                        {"districtName" in effectiveState ? effectiveState.districtName : "-"}
                      </p>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="flex w-full max-w-sm flex-col items-center rounded-[2rem] border border-slate-100 bg-slate-50/80 px-6 py-7 text-center shadow-soft">
              <div className="relative flex size-40 items-center justify-center">
                <div className={`absolute inset-0 rounded-full border ${content.ringTone} animate-pulse`} />
                <div className={`absolute inset-4 rounded-full border ${content.ringTone} animate-[spin_10s_linear_infinite]`} />
                <div className={`relative flex size-20 items-center justify-center rounded-full shadow-lg ${content.iconTone}`}>
                  <span className="material-symbols-outlined text-[2rem]">schedule</span>
                </div>
              </div>

              <p className="mt-4 text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">
                {content.statusLabel}
              </p>

              {effectiveState?.mode !== "season_starting" &&
              typeof effectiveState?.endTimestampMs === "number" ? (
                <div className="mt-3">
                  <CountdownTimer
                    key={`${effectiveState.mode}-${effectiveState.endTimestampMs}`}
                    endTimestampMs={effectiveState.endTimestampMs}
                    label={content.statusLabel}
                    onComplete={handleComplete}
                    variant="display"
                    showIcon={false}
                  />
                </div>
              ) : (
                <div className="mt-3 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-600 shadow-soft">
                  {effectiveState?.mode === "season_starting"
                    ? "시즌이 열리면 자동으로 입지선정으로 이동합니다."
                    : "다음 영업이 시작되면 자동으로 입장합니다."}
                </div>
              )}

              <p className="mt-4 text-sm leading-6 text-slate-500">
                {effectiveState?.mode === "season_starting"
                  ? "지금은 백엔드가 시즌 시작 상태를 마무리하는 구간입니다. 준비가 끝나면 별도 입력 없이 자동으로 다음 단계로 넘어갑니다."
                  : typeof effectiveState?.endTimestampMs === "number"
                    ? "남은 시간이 모두 지나면 다음 단계로 자동 이동합니다."
                    : "대기 상태가 실제 영업 시작 시점과 연결되면 자동 입장 화면으로 바뀝니다."}
              </p>
            </div>
          </div>

          {effectiveState?.mode === "prep_locked" && (
            <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
              <p className="text-sm text-slate-400">
                설정 시간이 끝나면 자동으로 이동합니다.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
