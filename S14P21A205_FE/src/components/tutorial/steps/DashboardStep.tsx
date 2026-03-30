import { useState } from "react";
import GuideOverlay from "../GuideOverlay";
import { useTutorialDataContext } from "../TutorialDataContext";
import ItemSelector from "../../common/ItemSelector";

export default function DashboardStep() {
  const { itemGroups } = useTutorialDataContext();
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const availablePoints = 100;

  const usedPoints = itemGroups.flatMap((g) => g.items)
    .filter((item) => selectedIds.includes(item.id))
    .reduce((sum, item) => sum + item.price, 0);

  const handleToggle = (id: number) => {
    setSelectedIds((prev) => {
      if (prev.includes(id)) return prev.filter((i) => i !== id);

      // 같은 그룹에서 이미 선택된 게 있으면 교체
      const targetGroup = itemGroups.find((g) => g.items.some((item) => item.id === id));
      if (targetGroup) {
        const groupItemIds = targetGroup.items.map((item) => item.id);
        const filtered = prev.filter((i) => !groupItemIds.includes(i));
        return [...filtered, id];
      }
      return [...prev, id];
    });
  };

  return (
    <GuideOverlay
      title="대시보드 안내"
      description={
        <span>
          게임을 시작하기 전, 대시보드에서 <strong>포인트</strong>, <strong>아이템</strong>, <strong>시즌 정보</strong>를 확인할 수 있어요.
          아이템을 직접 선택해보세요!
        </span>
      }
    >
      <div className="space-y-6">
        {/* 포인트 카드 */}
        <div className="rounded-[20px] bg-white shadow-soft p-6">
          <span className="text-sm font-medium text-gray-500">보유 포인트</span>
          <div className="flex items-baseline gap-2 mt-2">
            <span className="font-countdown text-[40px] font-bold leading-none tracking-tight text-primary">
              {availablePoints - usedPoints}P
            </span>
            {usedPoints > 0 && (
              <span className="text-sm font-medium text-slate-400">/ {availablePoints}P</span>
            )}
          </div>
          {usedPoints > 0 && (
            <p className="text-xs text-slate-400 mt-1 mb-4">
              현재 선택 기준으로 {usedPoints}P 사용 예정
            </p>
          )}
          {usedPoints === 0 && <div className="mb-4" />}

        </div>

        {/* 아이템 선택 — 실제 컴포넌트 재사용 */}
        <div className="p-3 bg-rose-50 rounded-xl text-sm text-rose-700">
          ⚠️ 같은 카테고리에서는 1개만 선택할 수 있어요. 아이템을 선택하고 시즌에 참여해서 매장을 생성하면 그때 포인트가 차감돼요. 시즌 참여 후에는 변경 불가!
        </div>

        <ItemSelector
          groups={itemGroups}
          selectedIds={selectedIds}
          onToggle={handleToggle}
          availablePoints={availablePoints - usedPoints}
          hideTooltip
        />

        {/* 시즌 흐름 */}
        <div className="rounded-2xl bg-white border border-slate-100 shadow-soft p-6">
          <h3 className="text-base font-bold text-slate-800 mb-3">시즌 흐름</h3>
          <p className="text-sm text-slate-600 mb-4">한 시즌 = <strong>7일</strong></p>

          {/* 전체 시즌 흐름 */}
          <div className="flex items-center gap-2 overflow-x-auto pb-3">
            <div className="px-3 py-2 rounded-lg text-xs font-bold bg-slate-50 text-slate-600 shrink-0">
              지역 선택 <span className="text-slate-400">(1분)</span>
            </div>
            <span className="material-symbols-outlined text-slate-300 text-sm shrink-0">arrow_forward</span>

            {/* 하루 반복 블록 */}
            <div className="shrink-0 border-2 border-dashed border-primary/30 rounded-xl px-3 py-2">
              <p className="text-[10px] font-bold text-primary text-center mb-1.5">하루 (× 7일 반복)</p>
              <div className="flex items-center gap-1.5">
                {[
                  { label: "준비", time: "40초", highlight: false },
                  { label: "영업", time: "2분", highlight: true },
                  { label: "리포트", time: "20초", highlight: false },
                ].map((step, i) => (
                  <div key={step.label} className="flex items-center gap-1.5">
                    {i > 0 && <span className="material-symbols-outlined text-slate-300 text-xs">arrow_forward</span>}
                    <div className={`px-2 py-1.5 rounded-lg text-[11px] font-bold whitespace-nowrap ${
                      step.highlight ? "bg-primary/10 text-primary" : "bg-slate-50 text-slate-600"
                    }`}>
                      {step.label} <span className="text-slate-400 font-normal">({step.time})</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <span className="material-symbols-outlined text-slate-300 text-sm shrink-0">arrow_forward</span>
            <div className="px-3 py-2 rounded-lg text-xs font-bold bg-slate-50 text-slate-600 shrink-0">
              최종 결과
            </div>
          </div>

          <div className="mt-4 space-y-2 text-sm text-slate-600">
            <p>⏱️ 하루 영업은 실제로 <strong>2분</strong>이에요. 게임 내 시간으로는 10시~22시(12시간)</p>
            <p>⚠️ 중간에 참여할 수도 있지만, <strong>DAY 6 이후에는 참여 불가</strong>!</p>
          </div>
        </div>
      </div>
    </GuideOverlay>
  );
}
