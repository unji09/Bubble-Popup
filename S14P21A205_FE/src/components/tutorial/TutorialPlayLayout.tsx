import { useCallback, useRef, useState, type ReactNode } from "react";
import PlayHeader from "../play/PlayHeader";
import UnityCanvas, { type UnityBridgeHandle } from "../play/UnityCanvas";
import RankingSidebar, { type RankEntry } from "../play/RankingSidebar";
import EventSidebar, { type GameAlert } from "../play/EventSidebar";
import ActionBar, { type ActionType } from "../play/ActionBar";
import EventEffect3DOverlay from "../play/effects/EventEffect3DOverlay";
import { setWeather, spawnShopAtIndex, setCameraRegion, startDay } from "../../utils/unity";

/** 서울숲/성수 = locationId 5 → 0-based index 4 */
const SEONGSU_REGION_INDEX = 4;

/** 튜토리얼용 mock 랭킹 (RankingSidebar 형식, 상위 5위) */
const MOCK_RANK_ENTRIES: RankEntry[] = [
  { id: "1", name: "트렌드세터", storeName: "맛있는 팝업", revenue: 5200000, roi: 42.5, isMe: false },
  { id: "2", name: "서울왕", storeName: "서울의 맛", revenue: 4800000, roi: 38.2, isMe: false },
  { id: "me", name: "나", storeName: "내 팝업", revenue: 4500000, roi: 35.0, isMe: true },
  { id: "4", name: "핫플주인", storeName: "트렌디 버블", revenue: 4200000, roi: 31.8, isMe: false },
  { id: "5", name: "장사왕", storeName: "맛나라", revenue: 3900000, roi: 28.5, isMe: false },
];

interface TutorialPlayLayoutProps {
  children?: ReactNode;
  /** EventSidebar에 표시할 알림 목록 */
  alerts?: GameAlert[];
  /** ActionBar 클릭 콜백 — 없으면 ActionBar의 기본 noop 사용 */
  onAction?: (action: ActionType) => void;
  usedActions?: Set<ActionType>;
  activeEffects?: Set<ActionType>;
  /** false면 ActionBar 숨김 */
  showActionBar?: boolean;
  /** PlayHeader를 감싸는 div ref (코치마크용) */
  headerRef?: React.RefObject<HTMLDivElement | null>;
  /** 실시간 랭킹 사이드바 ref (코치마크용) */
  rankingRef?: React.RefObject<HTMLDivElement | null>;
  /** 실시간 알림 사이드바 ref (코치마크용) */
  alertRef?: React.RefObject<HTMLDivElement | null>;
  /** Unity iframe ref 외부 노출 (이벤트 이펙트용) */
  unityIframeRef?: React.MutableRefObject<HTMLIFrameElement | null>;
  /** Unity 로딩 완료 콜백 */
  onUnityReady?: () => void;
}

export default function TutorialPlayLayout({
  children,
  alerts = [],
  onAction,
  usedActions,
  activeEffects,
  showActionBar = true,
  headerRef,
  rankingRef,
  alertRef,
  unityIframeRef: externalIframeRef,
  onUnityReady: externalOnUnityReady,
}: TutorialPlayLayoutProps) {
  const [fallbackUsed] = useState<Set<ActionType>>(() => new Set());
  const [fallbackEffects] = useState<Set<ActionType>>(() => new Set());
  const internalIframeRef = useRef<HTMLIFrameElement | null>(null);
  const iframeRef = externalIframeRef ?? internalIframeRef;
  const unityBridgeRef = useRef<UnityBridgeHandle | null>(null);

  const handleUnityReady = useCallback(() => {
    // 성수 지역에 매장 스폰 + 카메라 이동 + 맑은 날씨 + 영업 시작 (멈춰있는 긴 시간)
    spawnShopAtIndex(iframeRef, SEONGSU_REGION_INDEX);
    setCameraRegion(iframeRef, SEONGSU_REGION_INDEX);
    setWeather(iframeRef, "SUNNY", SEONGSU_REGION_INDEX);
    startDay(iframeRef, 9999);
    externalOnUnityReady?.();
  }, [iframeRef, externalOnUnityReady]);

  return (
    <div className="flex flex-col h-full">
      {/* 실제 PlayHeader */}
      <div ref={headerRef}>
        <PlayHeader
          location="서울숲/성수"
          storeName="내 팝업"
          menuName="🧋 버블티"
          day={1}
          remainingSeconds={75}
          remainingMilliseconds={75000}
          congestion="normal"
          guests={87}
          stock={163}
          balance={4235000}
        />
      </div>

      {/* 메인 게임 영역 — 실제 PlayPage와 동일한 구조 */}
      <main className="relative flex flex-1 overflow-hidden">
        {/* Unity 3D 캔버스 */}
        <UnityCanvas
          ref={unityBridgeRef}
          iframeRef={iframeRef}
          className="relative z-0 flex-1 bg-slate-950"
          onReady={handleUnityReady}
        />

        {/* 3D 이벤트 이펙트 오버레이 (파티클 + 효과음) */}
        <EventEffect3DOverlay />

        {/* 실시간 랭킹 (좌측 상단) */}
        <div ref={rankingRef}>
          <RankingSidebar rankings={MOCK_RANK_ENTRIES} />
        </div>

        {/* 실시간 알림 (우측 상단) */}
        <div ref={alertRef}>
          <EventSidebar alerts={alerts} />
        </div>

        {/* 액션 바 (하단 중앙) */}
        {showActionBar && (
          <ActionBar
            onAction={onAction ?? (() => {})}
            usedActions={usedActions ?? fallbackUsed}
            activeEffects={activeEffects ?? fallbackEffects}
          />
        )}

        {/* 튜토리얼 오버레이 콘텐츠 */}
        {children}
      </main>
    </div>
  );
}
