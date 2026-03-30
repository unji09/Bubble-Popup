import { useCallback, useEffect, useRef, useState } from "react";
import type { GameAlert } from "../../play/EventSidebar";
import TutorialPlayLayout from "../TutorialPlayLayout";
import { MOCK_EVENTS, EVENT_ICON_MAP, type EventCategory } from "../mockData";
import { sendToUnity } from "../../../utils/unity";
import { useEventEffectStore } from "../../play/effects/useEventEffect";
import type { EventEffectType } from "../../play/effects/effects";

/** 서울숲/성수 = locationId 5 → 0-based index 4 */
const SEONGSU_REGION_INDEX = 4;

const DISASTER_UNITY_WEATHER: Record<string, string> = {
  disaster_earthquake: `Earthquake,${SEONGSU_REGION_INDEX}`,
  disaster_flood: `Rain,${SEONGSU_REGION_INDEX}`,
  disaster_typhoon: `Wind,${SEONGSU_REGION_INDEX}`,
  disaster_fire: `Fire,${SEONGSU_REGION_INDEX}`,
};

const CATEGORY_TO_EFFECT: Record<string, EventEffectType> = {
  celebrity: "CELEBRITY_APPEARANCE",
  holiday: "SUBSTITUTE_HOLIDAY",
  subsidy: "GOVERNMENT_SUBSIDY",
  price_down: "PRICE_DOWN",
  price_up: "PRICE_UP",
  disaster_earthquake: "EARTHQUAKE",
  disaster_flood: "FLOOD",
  disaster_typhoon: "TYPHOON",
  disaster_fire: "FIRE",
  disease: "INFECTIOUS_DISEASE",
  policy: "POLICY_CHANGE",
  festival: "FESTIVAL",
};

/** 이벤트별 실제 게임 효과 데이터 */
interface EventEffectInfo {
  story: string;
  effects: string[];
}

const EVENT_EFFECT_INFO: Record<EventCategory, EventEffectInfo> = {
  celebrity: {
    story: "가게 앞에 사람들이 웅성거리더니, 유명 연예인이 지나가고 있어요! 구경꾼들 사이에서 우리 가게도 눈에 띄겠죠?",
    effects: ["유동인구 증가"],
  },
  holiday: {
    story: "갑자기 공휴일이라니, 오늘은 쉬는 날! 가족, 연인, 친구들이 거리로 쏟아져 나왔어요.",
    effects: ["유동인구 증가"],
  },
  subsidy: {
    story: "통장에 지원금이 들어왔어요! 이 돈으로 재료를 더 사둘까, 홍보에 쓸까?",
    effects: ["자본금 증가", "유동인구 소폭 증가"],
  },
  price_down: {
    story: "거래처에서 좋은 소식! 원재료 값이 내려서 같은 돈으로 더 많이 만들 수 있게 됐어요.",
    effects: ["해당 메뉴 원재료 가격 하락"],
  },
  price_up: {
    story: "거래처에서 안 좋은 소식이... 원재료 값이 올라서 마진이 빠듯해졌어요.",
    effects: ["해당 메뉴 원재료 가격 상승"],
  },
  disaster_earthquake: {
    story: "갑자기 땅이 흔들려요! 진열대가 쓰러지고 손님들이 대피하기 시작합니다.",
    effects: ["유동인구 감소", "재고 손실"],
  },
  disaster_flood: {
    story: "비가 너무 많이 와서 거리가 잠겼어요. 손님도 올 수 없고, 재고도 피해를 입었습니다.",
    effects: ["유동인구 감소", "재고 손실"],
  },
  disaster_typhoon: {
    story: "태풍이 몰려오고 있어요! 바람이 너무 세서 밖에 돌아다니는 사람이 없습니다.",
    effects: ["유동인구 감소", "재고 손실"],
  },
  disaster_fire: {
    story: "근처 건물에서 불이 났어요! 소방차가 출동하고 사람들이 대피하고 있습니다.",
    effects: ["유동인구 감소", "재고 손실"],
  },
  disease: {
    story: "감염병 주의보가 발령됐어요. 다들 외출을 피하면서 거리가 한산해졌습니다.",
    effects: ["유동인구 큰 폭 감소"],
  },
  policy: {
    story: "정부에서 새로운 규제를 발표했어요. 일회용품 제한, 위생 기준 강화로 비용이 늘어납니다.",
    effects: ["전체 원가 상승"],
  },
  festival: {
    story: "동네에서 축제가 열렸어요! 무대와 먹거리 부스 사이로 사람들이 가득합니다.",
    effects: ["해당 지역 유동인구 대폭 증가"],
  },
};

export default function EventStep() {
  const [alerts, setAlerts] = useState<GameAlert[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<EventCategory | null>(null);
  const [unityReady, setUnityReady] = useState(false);
  const unityIframeRef = useRef<HTMLIFrameElement | null>(null);
  const restoreTimerRef = useRef<number | null>(null);
  const triggerEffect = useEventEffectStore((s) => s.triggerEffect);
  const activeEffect = useEventEffectStore((s) => s.activeEffect);
  const clearEffect = useEventEffectStore((s) => s.clearEffect);
  const handleUnityReady = useCallback(() => setUnityReady(true), []);

  // 스텝을 벗어나면 이펙트 정리
  useEffect(() => {
    return () => {
      clearEffect();
    };
  }, [clearEffect]);

  // 이펙트가 끝나면 버튼 하이라이트 해제
  useEffect(() => {
    if (activeEffect === null) {
      setSelectedCategory(null);
    }
  }, [activeEffect]);

  const triggerUnityWeather = (category: string) => {
    const unityEffect = DISASTER_UNITY_WEATHER[category];
    if (unityEffect) {
      if (restoreTimerRef.current) clearTimeout(restoreTimerRef.current);
      sendToUnity(unityIframeRef, "SetWeather", unityEffect);
      restoreTimerRef.current = window.setTimeout(() => {
        sendToUnity(unityIframeRef, "SetWeather", `Clear,${SEONGSU_REGION_INDEX}`);
        restoreTimerRef.current = null;
      }, 15000);
    }
  };

  const handleEventTrigger = (event: typeof MOCK_EVENTS[0]) => {
    // 유니티 로딩 중이거나 이펙트 진행 중이면 실행 불가
    if (!unityReady || activeEffect !== null) return;

    const newAlert: GameAlert = {
      id: Date.now(),
      type: event.isPositive ? "event" : "bad_event",
      title: event.alertTitle ?? event.name,
      description: event.description,
      createdAt: Date.now(),
    };
    setAlerts((prev) => [newAlert, ...prev]);
    setSelectedCategory(event.category);

    const effectType = CATEGORY_TO_EFFECT[event.category];
    if (effectType) triggerEffect(effectType);
    triggerUnityWeather(event.category);
  };

  const selectedEvent = selectedCategory
    ? MOCK_EVENTS.find((e) => e.category === selectedCategory)
    : null;
  const selectedInfo = selectedCategory ? EVENT_EFFECT_INFO[selectedCategory] : null;

  return (
    <TutorialPlayLayout alerts={alerts} unityIframeRef={unityIframeRef} showActionBar={false} onUnityReady={handleUnityReady}>
      {/* 이펙트 재생 중 설명 오버레이 */}
      {selectedCategory && selectedEvent && selectedInfo && activeEffect !== null && (
        <div
          className="absolute inset-0 z-40 flex items-center justify-center cursor-pointer"
          onClick={() => setSelectedCategory(null)}
        >
          <div className={`rounded-2xl shadow-2xl overflow-hidden min-w-[260px] max-w-xs border backdrop-blur-md pointer-events-none ${
            selectedEvent.isPositive
              ? "bg-white/80 border-green-200/80"
              : "bg-white/80 border-rose-200/80"
          }`}>
            {/* 상단 컬러 바 */}
            <div className={`h-1.5 ${
              selectedEvent.isPositive ? "bg-gradient-to-r from-green-400 to-emerald-500" : "bg-gradient-to-r from-rose-400 to-red-500"
            }`} />

            <div className="px-5 py-4 text-center">
              {/* 아이콘 + 이름 */}
              <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-sm font-bold ${
                selectedEvent.isPositive
                  ? "bg-green-100/80 text-green-800"
                  : "bg-rose-100/80 text-rose-800"
              }`}>
                <span className="text-base">{EVENT_ICON_MAP[selectedCategory]}</span>
                {selectedEvent.name}
              </div>

              {/* 스토리 */}
              <p className="text-slate-600 text-xs mt-2.5 leading-relaxed">{selectedInfo.story}</p>

              {/* 구분선 */}
              <div className="border-t border-slate-200/60 my-3" />

              {/* 효과 태그 */}
              <div className="flex flex-wrap justify-center gap-1.5">
                {selectedInfo.effects.map((effect, i) => (
                  <span
                    key={i}
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold ${
                      selectedEvent.isPositive
                        ? "bg-green-100/80 text-green-700"
                        : "bg-rose-100/80 text-rose-700"
                    }`}
                  >
                    <span className="material-symbols-outlined text-xs">
                      {selectedEvent.isPositive ? "trending_up" : "trending_down"}
                    </span>
                    {effect}
                  </span>
                ))}
              </div>

              {/* 타이밍 */}
              <div className="flex justify-center gap-4 mt-3 text-[11px] text-slate-400">
                <span className="flex items-center gap-0.5">
                  <span className="material-symbols-outlined text-xs">schedule</span>
                  발생: {selectedEvent.timing}
                </span>
                <span className="flex items-center gap-0.5">
                  <span className="material-symbols-outlined text-xs">timer</span>
                  지속: {selectedEvent.duration}
                </span>
              </div>

              <p className="text-slate-400 text-[10px] mt-3">클릭하여 닫기</p>
            </div>
          </div>
        </div>
      )}

      {/* 이벤트 도감 — 하단 고정, 한 줄 */}
      <div className="absolute inset-x-0 bottom-0 z-30">
        <div className="bg-white/95 backdrop-blur-sm border-t border-slate-200 shadow-[0_-4px_20px_rgba(0,0,0,0.08)]">
          <div className="flex items-center gap-1.5 px-3 py-2 overflow-x-auto custom-scrollbar">
            <span className="shrink-0 text-[11px] font-bold text-slate-600 flex items-center gap-1 mr-1">
              <span className="material-symbols-outlined text-primary text-sm">menu_book</span>
              이벤트
            </span>
            {MOCK_EVENTS.map((event) => (
              <button
                key={event.category}
                onClick={() => handleEventTrigger(event)}
                disabled={!unityReady || activeEffect !== null}
                className={`shrink-0 flex items-center gap-1 px-2 py-1 rounded-lg border text-[11px] transition-all ${
                  (!unityReady || activeEffect !== null) && selectedCategory !== event.category
                    ? "border-slate-100 bg-slate-50 opacity-50 cursor-not-allowed"
                    : selectedCategory === event.category
                      ? event.isPositive
                        ? "border-green-300 bg-green-50 font-bold"
                        : "border-rose-300 bg-rose-50 font-bold"
                      : "border-slate-100 bg-white hover:border-slate-300 hover:shadow-sm"
                }`}
              >
                <span className="text-xs">{EVENT_ICON_MAP[event.category]}</span>
                <span className="text-slate-700 whitespace-nowrap">{event.name}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </TutorialPlayLayout>
  );
}
