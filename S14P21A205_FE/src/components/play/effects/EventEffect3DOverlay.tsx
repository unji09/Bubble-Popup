import { lazy, Suspense, type ComponentType } from "react";
import { Canvas } from "@react-three/fiber";
import { useEventEffectStore } from "./useEventEffect";
import type { EventEffectType } from "./effects";
import { EFFECT_CONFIG } from "./effects";
import LazyLoadErrorBoundary from "../../common/LazyLoadErrorBoundary";

function lazyWithRetry(importFn: () => Promise<{ default: ComponentType<any> }>) {
  return lazy(() =>
    importFn().catch(() => {
      const hasReloaded = sessionStorage.getItem("chunk_reload");
      if (!hasReloaded) {
        sessionStorage.setItem("chunk_reload", "1");
        window.location.reload();
      }
      return { default: () => null } as { default: ComponentType<any> };
    }),
  );
}

const FloodEffect = lazyWithRetry(() => import("./particles/FloodEffect"));
const FireEffect = lazyWithRetry(() => import("./particles/FireEffect"));
const TyphoonDebrisEffect = lazyWithRetry(() => import("./particles/TyphoonDebrisEffect"));
const CoinRainEffect = lazyWithRetry(() => import("./particles/CoinRainEffect"));
const StarBurstEffect = lazyWithRetry(() => import("./particles/StarBurstEffect"));
const ConfettiEffect = lazyWithRetry(() => import("./particles/ConfettiEffect"));
const VirusFogEffect = lazyWithRetry(() => import("./particles/VirusFogEffect"));
const DocumentEffect = lazyWithRetry(() => import("./particles/DocumentEffect"));
const FireworkEffect = lazyWithRetry(() => import("./particles/FireworkEffect"));
const PriceArrowEffect = lazyWithRetry(() => import("./particles/PriceArrowEffect"));
const EarthquakeEffect = lazyWithRetry(() => import("./particles/EarthquakeEffect"));

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
        <LazyLoadErrorBoundary>
          <Suspense fallback={null}>
            <EffectComp
              durationMs={config.durationMs}
              {...(activeEffect === "PRICE_DOWN" ? { direction: "down" } : {})}
              {...(activeEffect === "PRICE_UP" ? { direction: "up" } : {})}
            />
          </Suspense>
        </LazyLoadErrorBoundary>
      </Canvas>
    </div>
  );
}
