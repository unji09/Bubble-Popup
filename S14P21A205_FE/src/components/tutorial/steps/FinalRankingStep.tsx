import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTutorialStore } from "../../../stores/useTutorialStore";
import Podium from "../../ranking/Podium";
import RankingList from "../../ranking/RankingList";
import RankingRow from "../../ranking/RankingRow";
import Badge from "../../common/Badge";
import { MOCK_PODIUM_ENTRIES, MOCK_RANKING_LIST_ENTRIES } from "../mockData";

const listEntries = MOCK_RANKING_LIST_ENTRIES.filter((e) => e.rank >= 4);
const bankruptEntries = MOCK_RANKING_LIST_ENTRIES.filter((e) => e.isBankrupt);

/** 코치마크 스텝 정의 */
const COACH_STEPS = [
  {
    title: "시즌 랭킹",
    icon: "emoji_events",
    desc: "7일간의 시즌이 끝나면 최종 랭킹이 발표돼요. ROI(투자 대비 수익률) 기준으로 순위가 결정돼요!",
    sectionKey: "header",
  },
  {
    title: "포디움",
    icon: "military_tech",
    desc: "상위 3명은 포디움에 올라가요! 1위부터 3위까지 메달과 함께 ROI, 총 매출, 보상 포인트가 표시돼요.",
    sectionKey: "podium",
  },
  {
    title: "전체 순위",
    icon: "leaderboard",
    desc: "4위 이하 플레이어들의 순위를 확인할 수 있어요. 파산한 매장은 따로 표시돼요.",
    sectionKey: "list",
  },
  {
    title: "포인트 보상",
    icon: "toll",
    desc: "순위에 따라 포인트를 받아요. 받은 포인트로 다음 시즌에 아이템을 구매할 수 있어요!",
    sectionKey: "reward",
  },
];

export default function FinalRankingStep() {
  const navigate = useNavigate();
  const { completeCurrentStep } = useTutorialStore();
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

  useEffect(() => {
    if (!isWalking) return;
    const key = COACH_STEPS[coachStep].sectionKey;
    const el = sectionRefs.current[key];
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      const timer = setTimeout(measure, 350);
      return () => clearTimeout(timer);
    }
  }, [coachStep, isWalking, measure]);

  const currentStepData = isWalking ? COACH_STEPS[coachStep] : null;
  const spotlightRect = currentStepData ? rects[currentStepData.sectionKey] : null;

  const setRef = (key: string) => (el: HTMLDivElement | null) => {
    sectionRefs.current[key] = el;
  };

  return (
    <>
      {/* 실제 게임과 동일한 랭킹 레이아웃 */}
      <main className="flex-1 flex flex-col items-center py-8 px-4 sm:px-6">
        <div className="w-full max-w-[1100px] flex flex-col gap-8">
          {/* 헤더 */}
          <div ref={setRef("header")} className="mt-4 flex flex-col items-center gap-2 text-center">
            <Badge variant="gray" size="md">시즌 1</Badge>
            <h1 className="text-4xl font-black leading-tight tracking-tight">시즌 랭킹</h1>
            <p className="text-slate-500 font-medium">
              이번 시즌 최고의 팝업스토어 마스터를 확인하세요.
            </p>
          </div>

          {/* 포디움 */}
          <div ref={setRef("podium")}>
            <Podium entries={MOCK_PODIUM_ENTRIES} />
          </div>

          {/* 4위 이하 + 파산 매장 */}
          <div ref={setRef("list")} className="flex flex-col gap-6">
            <RankingList entries={listEntries} />

            {bankruptEntries.length > 0 && (
              <div className="flex flex-col gap-4">
                <h2 className="flex items-center gap-2 text-lg font-bold text-slate-500">
                  <span className="material-symbols-outlined text-rose-400">dangerous</span>
                  파산 매장
                </h2>
                {bankruptEntries.map((entry, index) => (
                  <RankingRow
                    key={`bankrupt-${index}`}
                    entry={{ ...entry, rank: entry.rank }}
                    animationDelay={600 + index * 100}
                  />
                ))}
              </div>
            )}
          </div>

          {/* 포인트 보상 안내 */}
          <div ref={setRef("reward")} className="rounded-2xl bg-primary/5 border border-primary/20 p-6 text-center">
            <span className="text-4xl">🎉</span>
            <h3 className="text-lg font-bold text-slate-800 mt-2 mb-1">시즌 종료!</h3>
            <p className="text-sm text-slate-600 mb-4">
              순위에 따라 포인트를 받을 수 있어요.<br />
              받은 포인트로 다음 시즌에 아이템을 구매하세요!
            </p>
            <div className="flex items-center justify-center gap-3 flex-wrap">
              {[
                { rank: "1위", points: "30P", color: "bg-amber-50 border-amber-200 text-amber-700" },
                { rank: "2위", points: "20P", color: "bg-slate-50 border-slate-200 text-slate-600" },
                { rank: "3위", points: "10P", color: "bg-orange-50 border-orange-200 text-orange-700" },
                { rank: "4위+", points: "5P", color: "bg-slate-50 border-slate-100 text-slate-500" },
              ].map((r) => (
                <div key={r.rank} className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl border ${r.color}`}>
                  <span className="text-sm font-bold">{r.rank}</span>
                  <span className="text-lg font-black">{r.points}</span>
                </div>
              ))}
            </div>
          </div>

          {/* 통산 기록 안내 */}
          <div className="p-4 bg-slate-50 rounded-xl text-sm text-slate-600">
            <p className="font-bold text-slate-700 mb-2">📊 통산 기록</p>
            <p>마이페이지에서 전체 시즌 누적 플레이 성과, 역대 최고 ROI, 참여 시즌 수 등을 확인할 수 있어요.</p>
          </div>
        </div>
      </main>

      {/* 코치마크 완료 후 요약 */}
      {!isWalking && (
        <div className="fixed inset-x-0 bottom-20 z-30 flex justify-center px-4">
          <div className="w-full max-w-lg rounded-2xl border border-white/20 bg-white/95 backdrop-blur-sm shadow-xl p-5">
            <p className="text-sm font-bold text-slate-800 mb-3">튜토리얼 완료!</p>
            <div className="space-y-1.5 text-xs text-slate-600">
              <p>🏆 ROI(투자 대비 수익률) 기준으로 순위가 결정돼요</p>
              <p>💰 순위에 따라 포인트를 받고, 다음 시즌에 아이템을 구매할 수 있어요</p>
              <p>📊 마이페이지에서 통산 기록도 확인할 수 있어요</p>
            </div>
            <div className="flex items-center justify-between mt-4">
              <button
                onClick={() => setCoachStep(0)}
                className="text-xs text-slate-500 hover:text-slate-700 font-medium"
              >
                다시 보기
              </button>
              <button
                onClick={() => {
                  completeCurrentStep();
                  navigate("/");
                }}
                className="px-4 py-2 bg-primary text-white text-sm font-bold rounded-xl hover:bg-primary-dark transition-colors"
              >
                튜토리얼 완료
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
