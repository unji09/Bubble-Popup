import { lazy, Suspense } from "react";
import { Canvas } from "@react-three/fiber";
import { useEventEffectStore } from "./useEventEffect";
import type { EventEffectType } from "./effects";
import { EFFECT_CONFIG } from "./effects";

const FloodEffect = lazy(() => import("./particles/FloodEffect"));
const FireEffect = lazy(() => import("./particles/FireEffect"));
const TyphoonDebrisEffect = lazy(() => import("./particles/TyphoonDebrisEffect"));
const CoinRainEffect = lazy(() => import("./particles/CoinRainEffect"));
const StarBurstEffect = lazy(() => import("./particles/StarBurstEffect"));
const ConfettiEffect = lazy(() => import("./particles/ConfettiEffect"));
const VirusFogEffect = lazy(() => import("./particles/VirusFogEffect"));
const DocumentEffect = lazy(() => import("./particles/DocumentEffect"));
const FireworkEffect = lazy(() => import("./particles/FireworkEffect"));
const PriceArrowEffect = lazy(() => import("./particles/PriceArrowEffect"));
const EarthquakeEffect = lazy(() => import("./particles/EarthquakeEffect"));

const EFFECT_COMPONENT: Partial<
  Record<EventEffectType, React.LazyExoticComponent<React.ComponentType<{ durationMs: number }>>>
> = {
  TYPHOON: TyphoonDebrisEffect,
  FLOOD: FloodEffect,
  FIRE: FireEffect,
  EARTHQUAKE: EarthquakeEffect,
  GOVERNMENT_SUBSIDY: CoinRainEffect,
  CELEBRITY_APPEARANCE: StarBurstEffect,
  SUBSTITUTE_HOLIDAY: ConfettiEffect,
  INFECTIOUS_DISEASE: VirusFogEffect,
  POLICY_CHANGE: DocumentEffect,
  FESTIVAL: FireworkEffect,
  PRICE_DOWN: PriceArrowEffect,
  PRICE_UP: PriceArrowEffect,
};

export default function EventEffect3DOverlay() {
  const activeEffect = useEventEffectStore((s) => s.activeEffect);

  if (!activeEffect) return null;

  const EffectComp = EFFECT_COMPONENT[activeEffect];
  // Unity 전용 이펙트 (EARTHQUAKE 등)는 프론트엔드 오버레이 없음
  if (!EffectComp) return null;

  const config = EFFECT_CONFIG[activeEffect];

  return (
    <div className="absolute inset-0 z-[5] pointer-events-none">
      <Canvas
        camera={{ position: [0, 0, 10], fov: 60 }}
        gl={{ alpha: true, antialias: false }}
        dpr={[1, 1.5]}
        style={{ background: "transparent" }}
      >
        <ambientLight intensity={0.5} />
        <Suspense fallback={null}>
          <EffectComp
            durationMs={config.durationMs}
            {...(activeEffect === "PRICE_DOWN" ? { direction: "down" } : {})}
            {...(activeEffect === "PRICE_UP" ? { direction: "up" } : {})}
          />
        </Suspense>
      </Canvas>
    </div>
  );
}
