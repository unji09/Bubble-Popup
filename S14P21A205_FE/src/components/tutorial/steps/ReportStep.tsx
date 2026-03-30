import { useCallback, useEffect, useRef, useState } from "react";
import { useTutorialStore } from "../../../stores/useTutorialStore";
import StatCard from "../../common/StatCard";
import ProfitChart from "../../report/ProfitChart";
import WeatherCard from "../../report/WeatherCard";
import { MOCK_REPORT, MOCK_PROFIT_CHART } from "../mockData";

const chartData = MOCK_PROFIT_CHART.map((d) => ({
  day: d.day,
  value: d.value,
  isCurrent: d.day === 5,
  isFuture: d.isFuture,
}));

const todayProfit = MOCK_REPORT.netProfit;
const reputation = MOCK_REPORT.reputation;

function formatCurrency(value: number) {
  const absolute = Math.abs(value).toLocaleString();
  return value < 0 ? `-${absolute}원` : `${absolute}원`;
}

/** 코치마크 스텝 정의 */
const COACH_STEPS = [
  {
    title: "리포트 개요",
    icon: "receipt_long",
    desc: "매일 영업이 끝나면 리포트가 생성돼요. 시즌·지역·메뉴 정보와 함께 임대료 정산, 발주일 안내가 표시돼요.",
    sectionKey: "header",
  },
  {
    title: "영업 통계",
    icon: "monitoring",
    desc: "매출·지출·순이익·방문객·평판·판매수량·재고·폐기까지 8가지 지표를 한눈에 확인할 수 있어요. 평판은 게임 내 유입률을 시각화한 수치예요. 순이익이 마이너스이면 빨간색으로 강조돼요!",
    sectionKey: "stats",
  },
  {
    title: "수익 추이",
    icon: "show_chart",
    desc: "시즌 전체의 일별 순이익 추이를 그래프로 보여줘요. 미래 일자는 흐리게 표시돼요.",
    sectionKey: "chart",
  },
  {
    title: "날씨 정보",
    icon: "wb_sunny",
    desc: "다음 날 날씨를 미리 알려줘요. 날씨에 따라 유동인구가 달라지니까, 전략을 세울 때 참고하세요!",
    sectionKey: "weather",
  },
];

export default function ReportStep() {
  const { nextStep } = useTutorialStore();
  const [coachStep, setCoachStep] = useState(0);
  const isWalking = coachStep >= 0 && coachStep < COACH_STEPS.length;

  const sectionRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const [rects, setRects] = useState<Record<string, DOMRect>>({});

  const measure = useCallback(() => {
    const newRects: Record<string, DOMRect> = {};
    for (const key of Object.keys(sectionRefs.current)) {
      const el = sectionRefs.current[key];
      if (el) newRects[key] = el.getBoundingClientRect();
    }
    setRects(newRects);
  }, []);

  useEffect(() => {
    const timer = setTimeout(measure, 150);
    window.addEventListener("resize", measure);
    window.addEventListener("scroll", measure, true);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, true);
    };
  }, [measure]);

  // 코치 스텝 변경 시 해당 섹션으로 스크롤 + 재측정
  useEffect(() => {
    if (!isWalking) return;
    const key = COACH_STEPS[coachStep].sectionKey;
    const el = sectionRefs.current[key];
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      // 스크롤 완료 후 재측정
      const timer = setTimeout(measure, 350);
      return () => clearTimeout(timer);
    }
  }, [coachStep, isWalking, measure]);

  // 코치마크 활성화 시 스크롤 차단
  useEffect(() => {
    if (!isWalking) return;
    const block = (e: Event) => {
      e.preventDefault();
      e.stopPropagation();
    };
    // wheel + touchmove 차단 (capture 단계에서)
    window.addEventListener("wheel", block, { capture: true, passive: false });
    window.addEventListener("touchmove", block, { capture: true, passive: false });
    return () => {
      window.removeEventListener("wheel", block, { capture: true });
      window.removeEventListener("touchmove", block, { capture: true });
    };
  }, [isWalking]);

  const currentStepData = isWalking ? COACH_STEPS[coachStep] : null;
  const spotlightRect = currentStepData ? rects[currentStepData.sectionKey] : null;

  const setRef = (key: string) => (el: HTMLDivElement | null) => {
    sectionRefs.current[key] = el;
  };

  return (
    <>
      {/* 실제 게임과 동일한 리포트 레이아웃 */}
      <main className={`flex-1 flex justify-center px-4 py-8 sm:px-10 ${isWalking ? "overflow-hidden" : "overflow-auto"}`}>
        <div className="flex w-full max-w-[1024px] flex-col gap-8">
          {/* 헤더 섹션 */}
          <div ref={setRef("header")} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2 border-b border-slate-200 pb-6">
              <div className="flex items-center gap-2">
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-600">시즌 1</span>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-green-100 text-green-700">서울숲/성수</span>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-100 text-amber-700">🧋 버블티</span>
              </div>
              <h1 className="text-4xl font-black leading-tight tracking-tight text-slate-900">내 팝업</h1>
              <p className="text-base text-slate-500">Day 5 운영 결과를 확인하세요.</p>
            </div>

            <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-slate-700">
              <span className="material-symbols-outlined text-2xl text-slate-500">receipt_long</span>
              <h3 className="text-base font-bold tracking-tight">Day 5 영업 마감 후 일일 임대료가 정산되었습니다</h3>
            </div>

            <div className="flex items-center gap-3 rounded-xl border border-primary/30 bg-primary/10 p-4 text-primary-dark">
              <span className="material-symbols-outlined text-2xl text-primary">local_shipping</span>
              <h3 className="text-base font-bold tracking-tight">내일은 발주일입니다. 재고를 확인하세요.</h3>
            </div>
          </div>

          {/* 스탯 카드 8개 — 실제 게임과 동일한 4열 그리드 */}
          <div ref={setRef("stats")} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="매출"
              value={formatCurrency(MOCK_REPORT.revenue)}
              icon="payments"
              iconBg="bg-green-100"
              iconColor="text-green-600"
            />
            <StatCard
              label="지출"
              value={formatCurrency(MOCK_REPORT.totalCost)}
              icon="shopping_cart_checkout"
              iconBg="bg-red-100"
              iconColor="text-red-600"
            />
            <StatCard
              label="순이익"
              value={formatCurrency(todayProfit)}
              icon={todayProfit >= 0 ? "savings" : "money_off"}
              iconBg={todayProfit >= 0 ? "bg-primary/20" : "bg-rose-100"}
              iconColor={todayProfit >= 0 ? "text-primary-dark" : "text-rose-dark"}
              highlight={todayProfit < 0}
            />
            <StatCard
              label="방문객 수"
              value={`${MOCK_REPORT.visitors}명`}
              icon="groups"
              iconBg="bg-slate-100"
              iconColor="text-slate-600"
            />
            <StatCard
              label="평판"
              value={reputation.toFixed(1)}
              change={{ value: "+0.3", positive: true }}
              subtext="게임 내 유입률 수치"
              icon="star"
              iconBg="bg-yellow-100"
              iconColor="text-yellow-600"
            />
            <StatCard
              label="판매 수량"
              value={`${MOCK_REPORT.salesQuantity}개`}
              subtext="🧋 버블티"
              icon="shopping_bag"
              iconBg="bg-blue-100"
              iconColor="text-blue-600"
            />
            <StatCard
              label="남은 재고"
              value={`${MOCK_REPORT.remainingStock}개`}
              subtext="다음 날 이월"
              icon="inventory_2"
              iconBg="bg-purple-100"
              iconColor="text-purple-600"
            />
            <StatCard
              label="폐기 재고"
              value={`${MOCK_REPORT.wastedStock}개`}
              subtext="폐기 발생"
              icon="delete"
              iconBg="bg-slate-100"
              iconColor="text-slate-500"
            />
          </div>

          {/* 수익 그래프 + 날씨 — 실제 게임과 동일한 레이아웃 */}
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-4">
            <div ref={setRef("chart")} className="lg:col-span-2">
              <ProfitChart data={chartData} />
            </div>
            <div ref={setRef("weather")} className="lg:col-span-2">
              <WeatherCard condition="SUNNY" />
            </div>
          </div>
        </div>
      </main>

      {/* 코치마크 완료 후 요약 */}
      {!isWalking && (
        <div className="fixed inset-x-0 bottom-20 z-30 flex justify-center px-4">
          <div className="w-full max-w-lg rounded-2xl border border-white/20 bg-white/95 backdrop-blur-sm shadow-xl p-5">
            <p className="text-sm font-bold text-slate-800 mb-3">리포트 요약</p>
            <div className="space-y-1.5 text-xs text-slate-600">
              <p>💰 매출·지출·순이익으로 하루 손익을 한눈에 확인하세요</p>
              <p>⭐ 평판은 유입률을 시각화한 거예요 — 높을수록 손님이 많이 와요</p>
              <p>📦 남은 재고는 다음 날 이월, 발주일에는 이전 재고 폐기</p>
              <p>🌤️ 다음 날 날씨를 확인하고 전략을 세워보세요!</p>
            </div>
            <div className="flex items-center justify-between mt-4">
              <button
                onClick={() => setCoachStep(0)}
                className="text-xs text-slate-500 hover:text-slate-700 font-medium"
              >
                다시 보기
              </button>
              <button
                onClick={nextStep}
                className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
              >
                다음
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 코치마크 오버레이 */}
      {isWalking && spotlightRect && (
        <div className="fixed inset-0 z-[100]" onClick={() => setCoachStep((s) => s + 1)}>
          <div
            className="absolute rounded-xl transition-all duration-300 ease-out"
            style={{
              top: spotlightRect.top - 8,
              left: spotlightRect.left - 8,
              width: spotlightRect.width + 16,
              height: spotlightRect.height + 16,
              boxShadow: "0 0 0 9999px rgba(15,23,42,0.65)",
              border: "2px solid rgba(168,191,169,0.7)",
            }}
          />

          <div
            className="absolute bg-white rounded-2xl shadow-2xl p-5 w-80"
            style={{
              top: Math.min(spotlightRect.bottom + 16, window.innerHeight - 200),
              left: Math.max(12, Math.min(spotlightRect.left, window.innerWidth - 340)),
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-2 mb-2">
              <div className="flex items-center justify-center w-8 h-8 rounded-full bg-primary/15">
                <span className="material-symbols-outlined text-primary text-lg">
                  {currentStepData!.icon}
                </span>
              </div>
              <h3 className="text-base font-bold text-slate-800">{currentStepData!.title}</h3>
              <span className="ml-auto text-xs text-slate-400 tabular-nums">
                {coachStep + 1}/{COACH_STEPS.length}
              </span>
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">{currentStepData!.desc}</p>
            <div className="flex items-center justify-between mt-4">
              {coachStep > 0 ? (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setCoachStep((s) => s - 1);
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
                  setCoachStep((s) => s + 1);
                }}
                className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
              >
                {coachStep < COACH_STEPS.length - 1 ? "다음" : "확인"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
