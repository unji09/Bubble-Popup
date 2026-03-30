import { useState } from "react";
import GuideOverlay from "../GuideOverlay";

/** 시뮬레이션 일자별 데이터 */
const SIM_DAYS = [
  { day: 1, revenue: 850000, cost: 620000, balance: 5230000 },
  { day: 2, revenue: 420000, cost: 580000, balance: 5070000 },
  { day: 3, revenue: 310000, cost: 590000, balance: 4790000 },
  { day: 4, revenue: 280000, cost: 610000, balance: 4460000 },
];

function formatMoney(v: number) {
  return `${v < 0 ? "-" : ""}${Math.abs(v).toLocaleString()}원`;
}

export default function BankruptcyStep() {
  const [simDay, setSimDay] = useState(0);
  const [showModal, setShowModal] = useState(false);

  const current = SIM_DAYS[simDay];
  const profit = current.revenue - current.cost;
  const deficitStreak = simDay === 0 ? 0 : Math.min(simDay, 3);

  const handleNext = () => {
    if (simDay < SIM_DAYS.length - 1) {
      setSimDay((s) => s + 1);
    } else {
      setShowModal(true);
    }
  };

  const handleReset = () => {
    setSimDay(0);
    setShowModal(false);
  };

  return (
    <GuideOverlay
      title="파산 조건"
      description="비용 관리를 잘 하는 게 생존의 핵심이에요. 파산이 어떻게 일어나는지 직접 체험해보세요!"
    >
      <div className="flex flex-col gap-6">
        {/* 파산 시뮬레이션 */}
        <div className="rounded-2xl bg-white border border-slate-100 shadow-soft overflow-hidden">
          {/* 시뮬레이션 헤더 */}
          <div className={`px-6 py-3.5 border-b transition-colors duration-300 ${
            showModal
              ? "bg-rose-50 border-rose-200"
              : deficitStreak >= 2
                ? "bg-rose-50 border-rose-200"
                : deficitStreak >= 1
                  ? "bg-amber-50 border-amber-200"
                  : "bg-slate-50 border-slate-100"
          }`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className={`material-symbols-outlined text-lg ${
                  showModal || deficitStreak >= 2 ? "text-rose-500"
                    : deficitStreak >= 1 ? "text-amber-500" : "text-slate-500"
                }`}>
                  {showModal ? "dangerous" : "play_circle"}
                </span>
                <h3 className={`text-sm font-bold ${
                  showModal || deficitStreak >= 2 ? "text-rose-700"
                    : deficitStreak >= 1 ? "text-amber-700" : "text-slate-700"
                }`}>
                  {showModal ? "파산!" : "파산 시뮬레이션"}
                </h3>
              </div>
              {!showModal && (
                <span className="text-xs font-bold text-slate-400">
                  DAY {current.day} / 4
                </span>
              )}
            </div>
          </div>

          {/* 파산 모달 체험 */}
          {showModal ? (
            <div className="p-8 flex flex-col items-center text-center">
              <div className="text-7xl mb-4 animate-bounce">😭</div>
              <h2 className="text-2xl font-black text-slate-800 mb-2">
                아쉽습니다! 파산했습니다.
              </h2>
              <p className="text-slate-500 mb-6 leading-relaxed">
                3일 연속 적자로 매장이 폐업되었습니다.<br />
                서울의 트렌드를 다시 분석하여 도전해보세요.
              </p>
              <button
                onClick={handleReset}
                className="px-6 py-3 rounded-xl bg-primary text-white font-bold hover:bg-primary-dark transition-colors"
              >
                다시 시뮬레이션하기
              </button>
            </div>
          ) : (
            <div className="p-6">
              {/* 일자별 타임라인 */}
              <div className="flex items-center gap-1 mb-6">
                {SIM_DAYS.map((d, i) => {
                  const p = d.revenue - d.cost;
                  const isActive = i <= simDay;
                  const isCurrent = i === simDay;
                  return (
                    <div key={d.day} className="flex-1 flex flex-col items-center gap-1.5">
                      <div className={`
                        w-full h-2 rounded-full transition-all duration-300
                        ${!isActive ? "bg-slate-100"
                          : p >= 0 ? "bg-primary/60"
                          : i === 3 ? "bg-rose-400"
                          : "bg-rose-300"
                        }
                      `} />
                      <span className={`text-[11px] font-bold transition-colors ${
                        isCurrent ? "text-slate-700" : isActive ? "text-slate-400" : "text-slate-200"
                      }`}>
                        D{d.day}
                      </span>
                    </div>
                  );
                })}
              </div>

              {/* 현재 일자 상세 */}
              <div className="grid grid-cols-3 gap-3 mb-5">
                <div className="rounded-xl bg-slate-50 border border-slate-100 p-3 text-center">
                  <p className="text-[10px] text-slate-500 font-bold mb-1">매출</p>
                  <p className="text-sm font-black text-slate-700">{formatMoney(current.revenue)}</p>
                </div>
                <div className="rounded-xl bg-slate-50 border border-slate-100 p-3 text-center">
                  <p className="text-[10px] text-slate-500 font-bold mb-1">지출</p>
                  <p className="text-sm font-black text-slate-700">{formatMoney(current.cost)}</p>
                </div>
                <div className={`rounded-xl border p-3 text-center ${
                  profit >= 0
                    ? "bg-primary/5 border-primary/20"
                    : "bg-rose-50/50 border-rose-200/60"
                }`}>
                  <p className={`text-[10px] font-bold mb-1 ${profit >= 0 ? "text-primary" : "text-rose-500"}`}>
                    순이익
                  </p>
                  <p className={`text-sm font-black ${profit >= 0 ? "text-primary-dark" : "text-rose-500"}`}>
                    {formatMoney(profit)}
                  </p>
                </div>
              </div>

              {/* 연속 적자 카운터 */}
              <div className={`flex items-center gap-3 rounded-xl p-4 mb-5 transition-colors duration-300 ${
                deficitStreak === 0
                  ? "bg-slate-50 border border-slate-100"
                  : deficitStreak >= 2
                    ? "bg-rose-50/60 border border-rose-200"
                    : "bg-amber-50/50 border border-amber-200/60"
              }`}>
                <span className={`material-symbols-outlined text-2xl ${
                  deficitStreak === 0 ? "text-slate-400"
                    : deficitStreak >= 2 ? "text-rose-400" : "text-amber-400"
                }`}>
                  {deficitStreak === 0 ? "check_circle" : "warning"}
                </span>
                <div className="flex-1">
                  <p className={`text-sm font-bold ${
                    deficitStreak === 0 ? "text-slate-600"
                      : deficitStreak >= 2 ? "text-rose-600" : "text-amber-600"
                  }`}>
                    {deficitStreak === 0
                      ? "연속 적자 없음"
                      : `연속 적자 ${deficitStreak}일째`
                    }
                    {deficitStreak >= 2 && " — 위험!"}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {deficitStreak === 0 && "좋아요! 이대로 유지하세요"}
                    {deficitStreak === 1 && "주의하세요. 내일도 적자면 경고 단계예요"}
                    {deficitStreak === 2 && "내일 한 번 더 적자가 나면 파산이에요!"}
                  </p>
                </div>
                <div className="flex gap-1">
                  {[1, 2, 3].map((n) => (
                    <div
                      key={n}
                      className={`w-3.5 h-3.5 rounded-full border-2 transition-all duration-300 ${
                        n <= deficitStreak
                          ? "bg-rose-400 border-rose-400"
                          : "bg-white border-slate-200"
                      }`}
                    />
                  ))}
                </div>
              </div>

              {/* 다음 버튼 */}
              <button
                onClick={handleNext}
                className={`w-full py-3 rounded-xl font-bold text-sm transition-colors ${
                  simDay === SIM_DAYS.length - 1
                    ? "bg-rose-400 text-white hover:bg-rose-500"
                    : "bg-primary text-white hover:bg-primary-dark"
                }`}
              >
                {simDay === SIM_DAYS.length - 1
                  ? "DAY 4 결과 확인 →"
                  : `DAY ${current.day + 1} 진행 →`
                }
              </button>
            </div>
          )}
        </div>

        {/* 파산 조건 요약 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="flex items-start gap-3 p-4 rounded-xl bg-white border border-slate-100 shadow-soft">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-rose-50 shrink-0">
              <span className="material-symbols-outlined text-rose-400 text-lg">trending_down</span>
            </div>
            <div>
              <p className="text-sm font-bold text-slate-700">3일 연속 적자</p>
              <p className="text-xs text-slate-400 mt-1">
                순이익이 3일 연속 마이너스이면 파산해요. 중간에 흑자가 나면 카운터가 리셋돼요.
              </p>
            </div>
          </div>
          <div className="flex items-start gap-3 p-4 rounded-xl bg-white border border-slate-100 shadow-soft">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-rose-50 shrink-0">
              <span className="material-symbols-outlined text-rose-400 text-lg">money_off</span>
            </div>
            <div>
              <p className="text-sm font-bold text-slate-700">임대료 미지불</p>
              <p className="text-xs text-slate-400 mt-1">
                일일 임대료를 낼 잔액이 없으면 즉시 파산이에요!
              </p>
            </div>
          </div>
        </div>

        {/* 중간 재참여 + 생존 팁 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="flex items-start gap-3 p-4 rounded-xl bg-white border border-slate-100 shadow-soft">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-primary/10 shrink-0">
              <span className="material-symbols-outlined text-primary text-lg">refresh</span>
            </div>
            <div>
              <p className="text-sm font-bold text-slate-700">중간 재참여</p>
              <p className="text-xs text-slate-400 mt-1">
                파산해도 같은 시즌에 다시 참여 가능해요. 단, DAY 6 이전까지만!
              </p>
            </div>
          </div>
          <div className="flex items-start gap-3 p-4 rounded-xl bg-white border border-slate-100 shadow-soft">
            <div className="flex items-center justify-center w-9 h-9 rounded-full bg-amber-50 shrink-0">
              <span className="material-symbols-outlined text-amber-500 text-lg">lightbulb</span>
            </div>
            <div>
              <p className="text-sm font-bold text-slate-700">생존 팁</p>
              <p className="text-xs text-slate-400 mt-1">
                임대료가 낮은 지역에서 시작하고, 적자 2일째에는 가격 전략을 바꿔보세요!
              </p>
            </div>
          </div>
        </div>
      </div>
    </GuideOverlay>
  );
}
