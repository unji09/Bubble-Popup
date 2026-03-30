import { useState } from "react";
import type { ActionType } from "../../play/ActionBar";
import type { GameAlert } from "../../play/EventSidebar";
import TutorialPlayLayout from "../TutorialPlayLayout";
import DiscountModal from "../../play/modals/DiscountModal";
import EmergencyOrderModal from "../../play/modals/EmergencyOrderModal";
import PromotionModal from "../../play/modals/PromotionModal";
import ShareModal from "../../play/modals/ShareModal";
import MoveModal from "../../play/modals/MoveModal";
import { MOCK_EMERGENCY_MENU_ITEMS, MOCK_MOVE_REGIONS, MOCK_PROMOTION_OPTIONS } from "../mockData";

const ACTION_EFFECT_INFO: Record<ActionType, { icon: string; label: string; story: string; effects: string[] }> = {
  discount: {
    icon: "sell",
    label: "할인",
    story: "가격을 낮추면 지나가던 손님도 발걸음을 멈춥니다. 박리다매 전략으로 승부해보세요!",
    effects: ["판매가를 낮춰 유입률 증가", "시장 평균가 대비 저렴할수록 효과 상승"],
  },
  emergency: {
    icon: "local_shipping",
    label: "긴급발주",
    story: "재고가 바닥났는데 손님은 줄을 서고 있어요! 급하게 전화를 걸어 재료를 주문합니다.",
    effects: ["부족한 재고를 즉시 발주", "원가 할증 적용", "교통 상황에 따라 도착 시간 변동"],
  },
  promotion: {
    icon: "campaign",
    label: "홍보",
    story: "우리 가게를 알려야 손님이 옵니다. 어떤 방법으로 홍보할지 골라보세요!",
    effects: ["유입률 증가", "인플루언서/SNS/전단지/입소문 타입별 비용과 효과가 다름"],
  },
  share: {
    icon: "volunteer_activism",
    label: "나눔",
    story: "남은 재고를 이웃에게 나눠주면 입소문이 퍼집니다. 따뜻한 마음이 손님을 부르죠.",
    effects: ["재고를 나눠 유입률 보너스 획득"],
  },
  move: {
    icon: "move_location",
    label: "팝업이전",
    story: "이 동네는 더 이상 장사가 안 될 것 같아요. 새로운 터전을 찾아 떠나볼까요?",
    effects: ["다음 영업일부터 선택한 지역으로 이전", "인테리어비 즉시 차감"],
  },
};

export default function ActionStep() {
  const [openModal, setOpenModal] = useState<ActionType | null>(null);
  const [usedActions, setUsedActions] = useState<Set<ActionType>>(() => new Set());
  const [activeEffects, setActiveEffects] = useState<Set<ActionType>>(() => new Set());
  const [alerts, setAlerts] = useState<GameAlert[]>([]);
  const [showEffectInfo, setShowEffectInfo] = useState<ActionType | null>(null);

  const handleAction = (action: ActionType) => {
    if (usedActions.has(action)) return;
    setOpenModal(action);
  };

  const closeModal = () => {
    setOpenModal(null);
  };

  const completeAction = (action: ActionType, title: string, description: string) => {
    setUsedActions((prev) => new Set(prev).add(action));
    setActiveEffects((prev) => {
      const next = new Set(prev);
      next.add(action);
      return next;
    });
    setOpenModal(null);
    setShowEffectInfo(action);
    setAlerts((prev) => [
      {
        id: Date.now(),
        type: "action",
        title,
        description,
        createdAt: Date.now(),
      },
      ...prev,
    ]);
    // 5초 후 activeEffect 해제
    setTimeout(() => {
      setActiveEffects((prev) => {
        const next = new Set(prev);
        next.delete(action);
        return next;
      });
      setShowEffectInfo(null);
    }, 5000);
  };

  return (
    <TutorialPlayLayout
      alerts={alerts}
      onAction={handleAction}
      usedActions={usedActions}
      activeEffects={activeEffects}
    >
      {/* 아무 액션도 사용하지 않았을 때만 안내 */}
      {!openModal && !showEffectInfo && usedActions.size === 0 && (
        <div className="absolute inset-x-0 bottom-36 z-30 flex justify-center px-4">
          <div className="rounded-2xl bg-white/95 backdrop-blur-sm border border-white/60 shadow-xl px-5 py-3">
            <p className="text-sm font-bold text-slate-800 text-center">
              하단 액션 바에서 버튼을 클릭해보세요!
            </p>
            <p className="text-[11px] text-slate-500 text-center mt-1">
              5가지 액션의 실제 화면을 체험할 수 있어요 • 각 액션은 하루에 한 번씩만 사용 가능
            </p>
          </div>
        </div>
      )}

      {/* 액션 완료 후 효과 설명 오버레이 */}
      {showEffectInfo && (() => {
        const info = ACTION_EFFECT_INFO[showEffectInfo];
        return (
          <div
            className="absolute inset-0 z-50 flex items-center justify-center cursor-pointer"
            onClick={() => setShowEffectInfo(null)}
          >
            <div className="rounded-2xl shadow-2xl overflow-hidden min-w-[280px] max-w-sm border bg-white/85 backdrop-blur-md border-primary/30 pointer-events-none">
              {/* 상단 컬러 바 */}
              <div className="h-1.5 bg-gradient-to-r from-primary to-blue-500" />

              <div className="px-6 py-5">
                {/* 아이콘 + 이름 */}
                <div className="flex items-center gap-3">
                  <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-primary/10">
                    <span className="material-symbols-outlined text-xl text-primary">
                      {info.icon}
                    </span>
                  </div>
                  <div className="text-left">
                    <p className="text-sm font-bold text-slate-800">{info.label}</p>
                    <p className="text-[10px] text-slate-400">액션 효과</p>
                  </div>
                </div>

                {/* 스토리 */}
                <p className="text-slate-600 text-xs mt-3 leading-relaxed">
                  {info.story}
                </p>

                {/* 구분선 */}
                <div className="border-t border-slate-200/60 my-3" />

                {/* 효과 목록 */}
                <div className="flex flex-col gap-2">
                  {info.effects.map((effect, i) => (
                    <div
                      key={i}
                      className="flex items-start gap-2 text-xs text-slate-700"
                    >
                      <span className="material-symbols-outlined text-sm text-primary mt-px">check_circle</span>
                      <span>{effect}</span>
                    </div>
                  ))}
                </div>

                <p className="text-slate-400 text-[10px] mt-4 text-center">클릭하여 닫기</p>
              </div>
            </div>
          </div>
        );
      })()}

      {/* 실제 게임 모달들 */}
      {openModal === "discount" && (
        <DiscountModal
          currentPrice={2600}
          minimumPrice={1300}
          onClose={closeModal}
          onSubmit={(rate) => {
            completeAction("discount", "할인 이벤트 적용", `${rate}% 할인이 적용되었습니다.`);
          }}
        />
      )}

      {openModal === "emergency" && (
        <EmergencyOrderModal
          currentBalance={4235000}
          menuItems={MOCK_EMERGENCY_MENU_ITEMS}
          currentMenuId={10}
          currentMenuPricing={{
            costPrice: 1300,
            recommendedPrice: 2600,
            maxSellingPrice: 5200,
            sellingPrice: 2600,
          }}
          deliveryTrafficLabel="보통"
          estimatedArrivalLabel="15:00"
          onClose={closeModal}
          onSubmit={({ menuName, quantity }) => {
            completeAction("emergency", "긴급 발주 완료", `${menuName} ${quantity}개를 긴급 발주했습니다. 15:00 도착 예정`);
          }}
        />
      )}

      {openModal === "promotion" && (
        <PromotionModal
          currentBalance={4235000}
          options={MOCK_PROMOTION_OPTIONS}
          onClose={closeModal}
          onSubmit={({ promotionId }) => {
            const opt = MOCK_PROMOTION_OPTIONS.find((o) => o.id === promotionId);
            completeAction("promotion", "홍보 시작", `${opt?.name ?? "홍보"}를 시작했습니다.`);
          }}
        />
      )}

      {openModal === "share" && (
        <ShareModal
          currentStock={163}
          onClose={closeModal}
          onSubmit={(quantity) => {
            completeAction("share", "나눔 이벤트 진행", `재고 ${quantity}개 나눔을 시작했습니다.`);
          }}
        />
      )}

      {openModal === "move" && (
        <MoveModal
          currentBalance={4235000}
          currentRegionName="서울숲/성수"
          regions={MOCK_MOVE_REGIONS}
          onClose={closeModal}
          onSubmit={({ regionName }) => {
            completeAction("move", "영업 지역 이전 예약", `${regionName}으로 다음 영업부터 이동합니다.`);
          }}
        />
      )}
    </TutorialPlayLayout>
  );
}
