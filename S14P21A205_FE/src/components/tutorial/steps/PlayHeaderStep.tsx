import { useCallback, useEffect, useRef, useState } from "react";
import TutorialPlayLayout from "../TutorialPlayLayout";

/** 코치마크 스텝 정의 — target: "header" | "ranking" | "alert" */
const COACH_STEPS = [
  {
    title: "매장 정보",
    icon: "storefront",
    desc: "현재 위치한 지역, 팝업 브랜드명, 판매 중인 메뉴가 표시돼요.",
    target: "header" as const,
    sectionIndex: 0,
  },
  {
    title: "시간 정보",
    icon: "schedule",
    desc: "현재 DAY와 게임 내 시각(10:00~22:00), 남은 영업 시간이 표시돼요. 아날로그 시계가 실시간으로 흘러가며, 실제 2분 = 게임 내 12시간이에요.",
    target: "header" as const,
    sectionIndex: 1,
  },
  {
    title: "영업 지표",
    icon: "monitoring",
    desc: "유동인구 혼잡도, 일일 방문객 수, 남은 재고, 현재 잔액이 실시간으로 업데이트돼요.",
    target: "header" as const,
    sectionIndex: 2,
  },
  {
    title: "실시간 랭킹",
    icon: "leaderboard",
    desc: "다른 플레이어들의 ROI(투자 대비 수익률) 기준 실시간 순위를 확인할 수 있어요. 단순히 매출이 높다고 1등이 아니에요!",
    target: "ranking" as const,
    sectionIndex: -1,
  },
  {
    title: "실시간 알림",
    icon: "notifications_active",
    desc: "영업 중 발생하는 이벤트(연예인 방문, 재해, 원가 변동 등)가 실시간으로 표시돼요. 하루에 최대 2번 이벤트가 발생할 수 있어요.",
    target: "alert" as const,
    sectionIndex: -1,
  },
];

/**
 * 진행 순서 (step 값):
 * 0~2: 헤더 코치마크 (COACH_STEPS[0~2])
 * 3:   손님 수 요약 패널
 * 4~5: 사이드바 코치마크 (COACH_STEPS[3~4])
 * 6:   자유 탐색
 */
const SUMMARY_STEP = 3;
const FREE_EXPLORE_STEP = COACH_STEPS.length + 1; // 6

/** step → COACH_STEPS 인덱스 매핑 (요약 패널이 3에 끼어있으므로 4+ 는 -1) */
function toCoachIndex(step: number): number | null {
  if (step < SUMMARY_STEP) return step;
  if (step === SUMMARY_STEP) return null; // 요약 패널
  if (step <= COACH_STEPS.length) return step - 1;
  return null;
}

export default function PlayHeaderStep() {
  const [step, setStep] = useState(0);
  const headerRef = useRef<HTMLDivElement | null>(null);
  const rankingRef = useRef<HTMLDivElement | null>(null);
  const alertRef = useRef<HTMLDivElement | null>(null);
  const [headerRects, setHeaderRects] = useState<DOMRect[]>([]);
  const [rankingRect, setRankingRect] = useState<DOMRect | null>(null);
  const [alertRect, setAlertRect] = useState<DOMRect | null>(null);

  const coachIndex = toCoachIndex(step);
  const isCoaching = coachIndex !== null;
  const isSummary = step === SUMMARY_STEP;
  const isFreeExplore = step === FREE_EXPLORE_STEP;

  const measure = useCallback(() => {
    if (headerRef.current) {
      const header = headerRef.current.querySelector("header");
      if (header) {
        const divs = Array.from(header.querySelectorAll(":scope > div"));
        setHeaderRects(divs.map((d) => d.getBoundingClientRect()));
      }
    }
    if (rankingRef.current) {
      const aside = rankingRef.current.querySelector("aside");
      if (aside) setRankingRect(aside.getBoundingClientRect());
    }
    if (alertRef.current) {
      const aside = alertRef.current.querySelector("aside");
      if (aside) setAlertRect(aside.getBoundingClientRect());
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(measure, 150);
    window.addEventListener("resize", measure);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("resize", measure);
    };
  }, [measure]);

  const currentCoach = isCoaching ? COACH_STEPS[coachIndex] : null;

  // 코치마크 spotlight 영역
  let spotlightRect: DOMRect | null = null;
  if (currentCoach) {
    if (currentCoach.target === "header") {
      spotlightRect = headerRects[currentCoach.sectionIndex] ?? null;
    } else if (currentCoach.target === "ranking") {
      spotlightRect = rankingRect;
    } else if (currentCoach.target === "alert") {
      spotlightRect = alertRect;
    }
  }

  // 코치마크 진행률 표시용 (요약/자유탐색 제외, 코치마크만 카운트)
  const coachDisplay = isCoaching
    ? { index: coachIndex, total: COACH_STEPS.length }
    : null;

  const goNext = () => setStep((s) => s + 1);
  const goPrev = () => setStep((s) => Math.max(0, s - 1));

  return (
    <>
      <TutorialPlayLayout
        headerRef={headerRef}
        rankingRef={rankingRef}
        alertRef={alertRef}
        showActionBar={!isCoaching && !isSummary}
      >
        {/* 손님 수 요약 패널 (헤더 코치마크 3개 직후) */}
        {isSummary && (
          <div className="absolute inset-x-0 bottom-24 z-30 flex justify-center px-4">
            <div className="w-full max-w-lg rounded-2xl border border-white/20 bg-white/95 backdrop-blur-sm shadow-xl p-5">
              <p className="text-sm font-bold text-slate-800 mb-3">손님 수는 어떻게 결정될까?</p>
              <div className="flex items-center justify-center gap-2 p-3 bg-slate-50 rounded-xl">
                <div className="text-center px-3 py-2 bg-blue-50 rounded-lg">
                  <p className="text-[10px] text-blue-500">유동인구</p>
                  <p className="text-xs font-bold text-blue-700">지역·시간·날씨</p>
                </div>
                <span className="text-slate-400 font-bold">×</span>
                <div className="text-center px-3 py-2 bg-green-50 rounded-lg">
                  <p className="text-[10px] text-green-500">유인율</p>
                  <p className="text-xs font-bold text-green-700">내 전략</p>
                </div>
                <span className="text-slate-400 font-bold">=</span>
                <div className="text-center px-3 py-2 bg-primary/10 rounded-lg">
                  <p className="text-[10px] text-primary">방문객</p>
                  <p className="text-xs font-bold text-primary-dark">손님 수</p>
                </div>
              </div>
              <div className="mt-3 space-y-1 text-xs text-slate-600">
                <p>💡 홍보·나눔·가격 설정이 유인율에 영향을 줘요</p>
                <p>💡 손님마다 구매 수량이 달라요! 한 번에 여러 개 사는 손님도 있어요</p>
                <p>💡 재고가 0이 되면 손님이 와도 구매를 하지 못해요!</p>
              </div>
              <div className="flex items-center justify-between mt-3">
                <button
                  onClick={goPrev}
                  className="text-xs text-slate-500 hover:text-slate-700 font-medium"
                >
                  이전
                </button>
                <button
                  onClick={goNext}
                  className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
                >
                  다음
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 자유 탐색 모드 — 유니티 화면만 + 하단 안내 */}
        {isFreeExplore && (
          <div className="absolute inset-x-0 bottom-24 z-30 flex justify-center px-4">
            <div className="rounded-2xl bg-white/95 backdrop-blur-sm border border-white/60 shadow-xl px-5 py-3">
              <p className="text-sm font-bold text-slate-800 text-center">
                자유롭게 둘러보세요!
              </p>
              <p className="text-[11px] text-slate-500 text-center mt-1">
                스페이스바를 누르면 탑뷰로 전환돼서 맵을 구경할 수 있어요
              </p>
              <div className="flex justify-center mt-2">
                <button
                  onClick={goNext}
                  className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
                >
                  확인
                </button>
              </div>
            </div>
          </div>
        )}

      </TutorialPlayLayout>

      {/* 코치마크 오버레이 (fixed로 전체 뷰포트 위에) */}
      {isCoaching && spotlightRect && (
        <div className="fixed inset-0 z-[100]" onClick={goNext}>
          {/* 스포트라이트 — box-shadow로 나머지 영역 어둡게 */}
          <div
            className="absolute rounded-xl transition-all duration-300 ease-out"
            style={{
              top: spotlightRect.top - 6,
              left: spotlightRect.left - 6,
              width: spotlightRect.width + 12,
              height: spotlightRect.height + 12,
              boxShadow: "0 0 0 9999px rgba(15,23,42,0.65)",
              border: "2px solid rgba(168,191,169,0.7)",
            }}
          />

          {/* 툴팁 — 사이드바는 옆에, 헤더는 아래에 배치 */}
          <div
            className="absolute bg-white rounded-2xl shadow-2xl p-5 w-80"
            style={
              currentCoach!.target === "ranking"
                ? {
                    top: spotlightRect.top,
                    left: spotlightRect.right + 16,
                  }
                : currentCoach!.target === "alert"
                  ? {
                      top: spotlightRect.top,
                      left: spotlightRect.left - 336,
                    }
                  : {
                      top: spotlightRect.bottom + 16,
                      left: Math.max(12, Math.min(spotlightRect.left, window.innerWidth - 340)),
                    }
            }
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-2 mb-2">
              <div className="flex items-center justify-center w-8 h-8 rounded-full bg-primary/15">
                <span className="material-symbols-outlined text-primary text-lg">
                  {currentCoach!.icon}
                </span>
              </div>
              <h3 className="text-base font-bold text-slate-800">{currentCoach!.title}</h3>
              <span className="ml-auto text-xs text-slate-400 tabular-nums">
                {coachDisplay!.index + 1}/{coachDisplay!.total}
              </span>
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">{currentCoach!.desc}</p>
            <div className="flex items-center justify-between mt-4">
              {step > 0 ? (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    goPrev();
                  }}
                  className="text-sm text-slate-500 hover:text-slate-700 font-medium"
                >
                  이전
                </button>
              ) : (
                <span />
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  goNext();
                }}
                className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
              >
                {step < FREE_EXPLORE_STEP - 1 ? "다음" : "완료"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
